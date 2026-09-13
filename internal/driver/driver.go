// Package driver wires the front end, semantic analysis and the C back end
// into a single compile pipeline.
package driver

import (
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"strings"

	"github.com/LangYa466/Teyru/internal/ast"
	"github.com/LangYa466/Teyru/internal/codegen"
	"github.com/LangYa466/Teyru/internal/parser"
	tyrt "github.com/LangYa466/Teyru/internal/runtime"
	"github.com/LangYa466/Teyru/internal/sema"
	"github.com/LangYa466/Teyru/internal/source"
	"github.com/LangYa466/Teyru/internal/util"
	"github.com/LangYa466/Teyru/lib"
)

// Options configures a compilation.
type Options struct {
	Out      string // output executable path
	EmitC    string // if set, also write the generated C here
	CFile    string // generated C path (defaults to a sibling .c of Out)
	CC       string // C compiler (default: clang)
	Opt      string // optimisation flag (default -O2)
	EmitLLVM string // if set, also write LLVM IR here (the backend is clang/LLVM)
	NoLTO    bool   // disable link-time optimisation (on by default)
	Verbose  bool
	ExtraCC  []string
	NoGC     bool
	KeptTemp bool
	// Native lists C sources that implement the program's native methods; they
	// are compiled together with the generated program.
	Native []string
	// Link holds extra arguments for the link step, such as -lm or a path to
	// a static library.
	Link []string
	// NativeHeader, when set, receives a C header declaring every native
	// method the program expects to be implemented.
	NativeHeader string
}

// Result reports the outcome of a compilation.
type Result struct {
	CFile    string
	LLVMFile string
	Exe      string
	Diags    *source.Diagnostics
}

// Compile turns Teyru sources into a native executable.
func Compile(paths []string, opts Options) (*Result, error) {
	if len(paths) == 0 {
		paths = []string{"."}
	}
	files := []string{}
	for _, p := range paths {
		st, err := os.Stat(p)
		if err != nil {
			return nil, err
		}
		if st.IsDir() {
			entries, err := os.ReadDir(p)
			if err != nil {
				return nil, err
			}
			for _, e := range entries {
				if !e.IsDir() && strings.HasSuffix(e.Name(), ".teyru") {
					files = append(files, filepath.Join(p, e.Name()))
				}
			}
			continue
		}
		files = append(files, p)
	}
	if len(files) == 0 {
		return nil, fmt.Errorf("no .teyru source files found")
	}

	diags := &source.Diagnostics{}
	astFiles := parsePrelude(diags)
	for _, f := range files {
		astFiles = append(astFiles, parseFile(f, diags))
	}
	if diags.HasErrors() {
		return &Result{Diags: diags}, fmt.Errorf("parse errors")
	}
	prog := sema.Check(astFiles, diags)
	if diags.HasErrors() {
		return &Result{Diags: diags}, fmt.Errorf("semantic errors")
	}
	if prog.Main == nil {
		return &Result{Diags: diags}, fmt.Errorf("no entry point: declare a static method named main")
	}
	if opts.NativeHeader != "" {
		if err := writeNativeHeader(opts.NativeHeader, codegen.NativeDecls(prog), codegen.InterfaceSelectors(prog)); err != nil {
			return nil, err
		}
	}
	csrc := codegen.Emit(prog)

	cfile := opts.CFile
	if cfile == "" {
		cfile = strings.TrimSuffix(opts.Out, filepath.Ext(opts.Out)) + ".c"
		if opts.Out == "" {
			cfile = "a.c"
		}
	}
	dir := filepath.Dir(cfile)
	if err := os.MkdirAll(dir, 0o755); err != nil {
		return nil, err
	}
	rtDir, err := os.MkdirTemp("", "teyru-rt-")
	if err != nil {
		return nil, err
	}
	if !opts.KeptTemp {
		defer os.RemoveAll(rtDir)
	}
	rtC, rtH := writeRuntime(rtDir)
	_ = rtH
	if err := os.WriteFile(cfile, []byte(csrc), 0o644); err != nil {
		return nil, err
	}
	if opts.EmitC != "" {
		if err := os.WriteFile(opts.EmitC, []byte(csrc), 0o644); err != nil {
			return nil, err
		}
	}
	exe := opts.Out
	opt := opts.Opt
	if opt == "" {
		opt = "-O2"
	}
	base := []string{opt, "-std=gnu11", "-fno-strict-aliasing", "-w", "-I", rtDir, cfile}
	base = append(base, strings.Fields(rtC)...)
	base = append(base, opts.Native...)
	base = append(base, "-o", exe, "-lm", "-lpthread")
	base = append(base, opts.Link...)
	base = append(base, opts.ExtraCC...)
	// Link-time optimisation lets clang inline runtime helpers (string ops, the
	// allocation fast path) into the generated program. It is on by default and
	// silently retried without it when the toolchain has no LTO support.
	args := append([]string{}, base...)
	if !opts.NoLTO {
		args = append([]string{"-flto"}, base...)
	}
	cc := opts.CC
	if cc == "" {
		cc = findCC()
	}
	if opts.Verbose {
		fmt.Fprintf(os.Stderr, "teyru: %s %s\n", cc, strings.Join(args, " "))
	}
	cmd := exec.Command(cc, args...)
	cmd.Stderr = os.Stderr
	if err := cmd.Run(); err != nil {
		if !opts.NoLTO {
			if opts.Verbose {
				fmt.Fprintln(os.Stderr, "teyru: retrying without -flto")
			}
			retry := exec.Command(cc, base...)
			retry.Stderr = os.Stderr
			if err2 := retry.Run(); err2 == nil {
				return &Result{CFile: cfile, Exe: exe, Diags: diags}, nil
			}
		}
		_ = rtH
		return &Result{CFile: cfile, Diags: diags}, fmt.Errorf("C backend failed: %w", err)
	}
	res := &Result{CFile: cfile, Exe: exe, Diags: diags}
	if opts.EmitLLVM != "" {
		// The backend is clang, whose middle and back end are LLVM: emit the
		// module-level IR so it can be inspected or fed to llc/opt directly.
		irArgs := []string{"-S", "-emit-llvm", "-std=gnu11", "-fno-strict-aliasing", "-w",
			"-I", rtDir, cfile, "-o", opts.EmitLLVM}
		if opt != "" {
			irArgs = append(irArgs, opt)
		}
		ir := exec.Command(cc, irArgs...)
		ir.Stderr = os.Stderr
		if err := ir.Run(); err != nil {
			return res, fmt.Errorf("LLVM IR emission failed: %w", err)
		}
		res.LLVMFile = opts.EmitLLVM
	}
	return res, nil
}

// parsePrelude reads the standard library, which is Teyru source shipped with
// the compiler, as the first compilation units of every program.
func parsePrelude(diags *source.Diagnostics) []*ast.File {
	files := lib.Files()
	out := make([]*ast.File, 0, len(files))
	for _, f := range files {
		out = append(out, parseSource("<lib>/"+f.Name, f.Source, diags))
	}
	return out
}

func parseFile(path string, diags *source.Diagnostics) *ast.File {
	data, err := os.ReadFile(path)
	if err != nil {
		diags.Errorf(source.Pos{}, "TY-IO-0001", "cannot read %s: %v", path, err)
		return &ast.File{Src: source.NewFile(path, "")}
	}
	return parseSource(path, string(data), diags)
}

func parseSource(path, text string, diags *source.Diagnostics) *ast.File {
	return parser.Parse(source.NewFile(path, text), diags)
}

// writeNativeHeader writes the C prototypes a program has to implement, so
// that native methods can be written against a declaration the compiler
// generated instead of a name the author has to guess.
func writeNativeHeader(path string, decls []codegen.NativeDecl, sels []codegen.SelectorDecl) error {
	var b strings.Builder
	b.WriteString("/* native methods declared by this program.\n")
	b.WriteString("   Implement each one and pass the file back with `--native <file.c>`. */\n\n")
	b.WriteString("#ifndef TEYRU_NATIVE_H\n#define TEYRU_NATIVE_H\n\n")
	b.WriteString("#include \"tyrt.h\"\n\n")
	if len(decls) == 0 {
		b.WriteString("/* this program declares no native methods */\n")
	}
	for _, d := range decls {
		fmt.Fprintf(&b, "/* %s */\n%s;\n\n", d.Method, d.Signature)
	}
	if len(sels) > 0 {
		b.WriteString("/* Dispatch selectors of interface methods. A native method can call\n")
		b.WriteString("   back into Teyru with\n")
		b.WriteString("     ((int32_t (*)(void *, int32_t)) ty_itab(obj, SEL))(obj, arg) */\n\n")
		for _, s := range sels {
			fmt.Fprintf(&b, "#define TY_SEL_%s_%s %d\n", mangleForHeader(s.Iface), mangleForHeader(s.Method), s.Selector)
		}
		b.WriteString("\n")
	}
	b.WriteString("#endif\n")
	if err := os.WriteFile(path, []byte(b.String()), 0o644); err != nil {
		return fmt.Errorf("cannot write %s: %w", path, err)
	}
	return nil
}

// mangleForHeader turns a Teyru name into the upper case identifier used in
// the generated header.
func mangleForHeader(s string) string {
	return strings.ToUpper(util.Mangle(s))
}

// writeRuntime materialises the C runtime next to the generated program.
func writeRuntime(dir string) (cfile, hfile string) {
	hfile = filepath.Join(dir, "tyrt.h")
	c1 := filepath.Join(dir, "tyrt.c")
	c2 := filepath.Join(dir, "tyrt2.c")
	must(os.WriteFile(hfile, []byte(tyrt.Header), 0o644))
	must(os.WriteFile(c1, []byte(tyrt.Core), 0o644))
	must(os.WriteFile(c2, []byte(tyrt.Extra), 0o644))
	return c1 + " " + c2, hfile
}

func must(err error) {
	if err != nil {
		panic(err)
	}
}

func findCC() string {
	for _, c := range []string{"clang", "gcc", "cc"} {
		if _, err := exec.LookPath(c); err == nil {
			return c
		}
	}
	return "cc"
}

// Run executes a compiled program.
func Run(exe string, args []string) (int, error) {
	cmd := exec.Command(exe, args...)
	cmd.Stdout = os.Stdout
	cmd.Stderr = os.Stderr
	cmd.Stdin = os.Stdin
	err := cmd.Run()
	if err == nil {
		return 0, nil
	}
	if ee, ok := err.(*exec.ExitError); ok {
		return ee.ExitCode(), nil
	}
	return 1, err
}

// Version reports the compiler version string.
func Version() string {
	return "teyru 0.2.0 (" + runtime.GOOS + "/" + runtime.GOARCH + ")"
}
