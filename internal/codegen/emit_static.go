package codegen

import (
	"fmt"

	"github.com/LangYa466/Teyru/internal/ast"
)

// staticName is the C global holding a class's static field.
func staticName(cl *ast.Class, f *ast.Field) string {
	return "G_" + mangle(cl.Full) + "_" + mangle(f.Name)
}

// emitStaticFields declares the C globals for a class's static fields.
func (e *Emitter) emitStaticFields(cl *ast.Class) {
	for _, f := range cl.Fields {
		if !f.Mods.Has(ast.ModStatic) {
			continue
		}
		ct := e.ctype(f.Type)
		fmt.Fprintf(&e.data, "static %s %s = %s;\n", ct, staticName(cl, f), zeroOf(ct))
	}
	for _, f := range cl.Fields {
		if f.Mods.Has(ast.ModStatic) {
			fmt.Fprintf(&e.data, "static void* _root_%s = (void*)&%s;\n", staticName(cl, f), staticName(cl, f))
		}
	}
}

// clinitRefs collects the global addresses that must be registered with the GC.
func (e *Emitter) clinitRefs() string {
	var b []byte
	for _, cl := range e.prog.Classes {
		for _, f := range cl.Fields {
			if f.Mods.Has(ast.ModStatic) {
				nm := "_root_" + staticName(cl, f)
				b = append(b, []byte("\tty_gc_register_static((void*)&"+nm+");\n")...)
			}
		}
	}
	return string(b)
}

// emitClInit writes a class's static initializer.
func (e *Emitter) emitClInit(cl *ast.Class) {
	if cl.ClInit == nil {
		return
	}
	m := cl.ClInit
	fmt.Fprintf(&e.fns, "static %s;\n", e.signature(m))
	e.indent = 0
	fmt.Fprintf(&e.code, "static %s {\n", e.signature(m))
	e.indent++
	if cl.Super != nil {
		e.line("ty_clinit(&cls_%s);\n", mangle(cl.Super.Class.Full))
	}
	if cl.Decl != nil {
		for _, mem := range cl.Decl.Members {
			fd, ok := mem.(*ast.FieldDecl)
			if !ok {
				continue
			}
			for _, vd := range fd.Vars {
				f := vd.Fld
				if f == nil || !f.Mods.Has(ast.ModStatic) || vd.Init == nil {
					continue
				}
				e.line("%s = %s;\n", staticName(cl, f), e.coerce(e.expr(vd.Init), vd.Init.GetType(), f.Type))
			}
		}
		for _, mem := range cl.Decl.Members {
			if ib, ok := mem.(*ast.InitBlock); ok && ib.Static {
				e.emitBlockInner(ib.Body)
			}
		}
	}
	e.indent--
	e.code.WriteString("}\n\n")
}
