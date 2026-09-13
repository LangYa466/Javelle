package teyru_test

import (
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"testing"

	"github.com/LangYa466/Teyru/internal/driver"
)

// TestPrograms compiles and runs every program in tests/programs and compares
// its output with the matching .expected file.
func TestPrograms(t *testing.T) {
	if _, err := exec.LookPath("clang"); err != nil {
		if _, err2 := exec.LookPath("gcc"); err2 != nil {
			t.Skip("no C compiler available")
		}
	}
	dir := "tests/programs"
	entries, err := os.ReadDir(dir)
	if err != nil {
		t.Fatal(err)
	}
	for _, e := range entries {
		if !strings.HasSuffix(e.Name(), ".teyru") {
			continue
		}
		name := strings.TrimSuffix(e.Name(), ".teyru")
		src := filepath.Join(dir, e.Name())
		want, err := os.ReadFile(filepath.Join(dir, name+".expected"))
		if err != nil {
			t.Fatalf("%s: missing expectation file: %v", name, err)
		}
		t.Run(name, func(t *testing.T) {
			out := filepath.Join(t.TempDir(), name)
			res, err := driver.Compile([]string{src}, driver.Options{Out: out, Opt: "-O1"})
			if err != nil {
				t.Fatalf("compile failed: %v\n%s", err, res.Diags)
			}
			cmd := exec.Command(res.Exe)
			if raw, err := os.ReadFile(filepath.Join(dir, name+".args")); err == nil {
				for _, a := range strings.Fields(string(raw)) {
					cmd.Args = append(cmd.Args, a)
				}
			}
			got, err := cmd.CombinedOutput()
			if err != nil {
				if ee, ok := err.(*exec.ExitError); !ok || ee.ExitCode() != 0 {
					t.Fatalf("run failed: %v\n%s", err, got)
				}
			}
			if string(got) != string(want) {
				t.Errorf("output mismatch\n--- want ---\n%s\n--- got ---\n%s", want, got)
			}
		})
	}
}

// TestDiagnostics checks that ill-typed programs are rejected.
func TestDiagnostics(t *testing.T) {
	cases := []struct {
		name string
		src  string
		code string
	}{
		{"semicolon", "class A {\n  public static void main(String[] args) {\n    int x = 1;\n  }\n}\n", "TY-SYN-0001"},
		{"type", "class A {\n  public static void main(String[] args) {\n    int x = \"s\"\n  }\n}\n", "TY-TYP-0051"},
		{"unknownName", "class A {\n  public static void main(String[] args) {\n    System.out.println(missing)\n  }\n}\n", "TY-TYP-0048"},
		{"abstractMissing", "abstract class B {\n  abstract int f()\n}\nclass A extends B {\n  public static void main(String[] args) {\n  }\n}\n", "TY-TYP-0019"},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			dir := t.TempDir()
			path := filepath.Join(dir, tc.name+".teyru")
			if err := os.WriteFile(path, []byte(tc.src), 0o644); err != nil {
				t.Fatal(err)
			}
			res, err := driver.Compile([]string{path}, driver.Options{Out: filepath.Join(dir, "out"), Opt: "-O0"})
			if err == nil {
				t.Fatalf("expected failure")
			}
			if res == nil || res.Diags == nil {
				t.Fatalf("expected diagnostics, got %v", err)
			}
			if !strings.Contains(res.Diags.String(), tc.code) {
				t.Errorf("expected code %s in:\n%s", tc.code, res.Diags)
			}
		})
	}
}

// TestNoJava checks that the produced binary has no JVM dependency.
func TestNoJava(t *testing.T) {
	dir := t.TempDir()
	src := filepath.Join(dir, "Hello.teyru")
	if err := os.WriteFile(src, []byte("class Hello {\n  public static void main(String[] args) {\n    System.out.println(\"hi\")\n  }\n}\n"), 0o644); err != nil {
		t.Fatal(err)
	}
	out := filepath.Join(dir, "hello")
	res, err := driver.Compile([]string{src}, driver.Options{Out: out, Opt: "-O2"})
	if err != nil {
		t.Skipf("no C compiler available: %v", err)
	}
	data, err := os.ReadFile(res.CFile)
	if err != nil {
		t.Fatal(err)
	}
	lower := strings.ToLower(string(data))
	for _, bad := range []string{"jni", "jvm", "javac", "class file"} {
		if strings.Contains(lower, bad) {
			t.Errorf("generated C mentions %q", bad)
		}
	}
	if strings.Contains(string(data), ".class") {
		t.Error("generated C references class files")
	}
}
