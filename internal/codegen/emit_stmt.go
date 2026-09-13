package codegen

import (
	"fmt"
	"strings"

	"github.com/LangYa466/Teyru/internal/ast"
)

// localName returns the stable C name of a local variable or parameter.
func (e *Emitter) localName(v *ast.Var) string {
	if v.ID < 0 {
		return "this"
	}
	if n, ok := e.locals[v]; ok {
		return n
	}
	n := fmt.Sprintf("v%d_%s", v.ID, mangle(v.Name))
	e.locals[v] = n
	return n
}

func (e *Emitter) emitBlockInner(b *ast.Block) {
	for _, s := range b.Stmts {
		e.stmt(s)
	}
}

func (e *Emitter) stmt(s ast.Stmt) {
	switch v := s.(type) {
	case *ast.Block:
		e.line("{\n")
		e.indent++
		e.emitBlockInner(v)
		e.indent--
		e.line("}\n")
	case *ast.Empty:
	case *ast.LocalVar:
		e.localVar(v)
	case *ast.LocalClass:
	case *ast.ExprStmt:
		e.exprStmt(v.X)
	case *ast.If:
		e.line("if (%s) {\n", e.cond(v.Cond))
		e.indent++
		e.stmtAsBlock(v.Then)
		e.indent--
		if v.Else != nil {
			e.line("} else {\n")
			e.indent++
			e.stmtAsBlock(v.Else)
			e.indent--
		}
		e.line("}\n")
	case *ast.While:
		e.line("while (%s) {\n", e.cond(v.Cond))
		e.indent++
		e.stmtAsBlock(v.Body)
		e.indent--
		e.line("}\n")
	case *ast.DoWhile:
		e.line("do {\n")
		e.indent++
		e.stmtAsBlock(v.Body)
		e.indent--
		e.line("} while (%s);\n", e.cond(v.Cond))
	case *ast.For:
		e.line("{\n")
		e.indent++
		for _, init := range v.Init {
			e.stmt(init)
		}
		cond := "1"
		if v.Cond != nil {
			cond = e.cond(v.Cond)
		}
		var up []string
		for _, u := range v.Update {
			up = append(up, e.expr(u))
		}
		e.line("while (%s) {\n", cond)
		e.indent++
		e.stmtAsBlock(v.Body)
		if len(up) > 0 {
			e.line("%s;\n", strings.Join(up, ", "))
		}
		e.indent--
		e.line("}\n")
		e.indent--
		e.line("}\n")
	case *ast.ForEach:
		e.forEach(v)
	case *ast.Return:
		if v.X == nil {
			e.line("return;\n")
		} else {
			e.line("return %s;\n", e.expr(v.X))
		}
	case *ast.Break:
		if v.Label != "" {
			e.line("goto %s;\n", e.labelName(v.Label, true))
		} else {
			e.line("break;\n")
		}
	case *ast.Continue:
		if v.Label != "" {
			e.line("goto %s;\n", e.labelName(v.Label, false))
		} else {
			e.line("continue;\n")
		}
	case *ast.Throw:
		e.line("ty_throw((tyobj*)%s);\n", e.refExpr(v.X))
	case *ast.Try:
		e.tryStmt(v)
	case *ast.Switch:
		e.switchStmt(v, "")
	case *ast.Yield:
		e.line("/* yield handled by switch expression */;\n")
	case *ast.Labeled:
		e.line("%s: ;\n", e.labelName(v.Label, false))
		e.stmt(v.Body)
		if isLoop(v.Body) {
			e.line("%s: ;\n", e.labelName(v.Label, true))
		}
	case *ast.Assert:
		e.line("if (!(%s)) { ty_assertfail(%s); }\n", e.cond(v.Cond), e.assertMsg(v))
	case *ast.Sync:
		e.line("{ void* _lock = (void*)%s; ty_sync_enter(_lock);\n", e.refExpr(v.Lock))
		e.indent++
		e.emitBlockInner(v.Body)
		e.line("ty_sync_exit(_lock);\n")
		e.indent--
		e.line("}\n")
	}
}

func (e *Emitter) assertMsg(v *ast.Assert) string {
	if v.Msg == nil {
		return "NULL"
	}
	if lit, ok := v.Msg.(*ast.Literal); ok && lit.Kind == ast.LitString {
		return e.cstr(lit.Str)
	}
	return e.tmpRef(e.expr(v.Msg))
}

func isLoop(s ast.Stmt) bool {
	switch s.(type) {
	case *ast.While, *ast.DoWhile, *ast.For, *ast.ForEach:
		return true
	}
	return false
}

func (e *Emitter) labelName(l string, brk bool) string {
	if brk {
		return "brk_" + mangle(l)
	}
	return "lbl_" + mangle(l)
}

func (e *Emitter) stmtAsBlock(s ast.Stmt) {
	if b, ok := s.(*ast.Block); ok {
		e.emitBlockInner(b)
		return
	}
	e.stmt(s)
}

func (e *Emitter) localVar(v *ast.LocalVar) {
	for _, vd := range v.Vars {
		if vd.Sym == nil {
			continue
		}
		ct := e.ctype(vd.Sym.Type)
		name := e.localName(vd.Sym)
		if vd.Init == nil {
			e.line("%s %s = %s;\n", ct, name, zeroOf(ct))
			continue
		}
		e.line("%s %s = %s;\n", ct, name, e.coerce(e.expr(vd.Init), vd.Init.GetType(), vd.Sym.Type))
	}
}

// exprStmt emits an expression as a statement.
func (e *Emitter) exprStmt(x ast.Expr) {
	switch v := x.(type) {
	case *ast.Call:
		if v.ThisCtor {
			recv := "this"
			if v.Super {
				recv = "((void*)this)"
			}
			e.line("%s(%s);\n", e.cfunc(v.Method), e.args(recv, v.Args, v.Method))
			return
		}
		e.line("%s;\n", e.callExpr(v))
	case *ast.Assign, *ast.Unary:
		e.line("%s;\n", e.expr(x))
	case *ast.New:
		e.line("%s;\n", e.expr(x))
	case *ast.SwitchExpr:
		e.line("%s;\n", e.expr(x))
	default:
		e.line("(void)(%s);\n", e.expr(x))
	}
}

// args renders the C argument list of a call, inserting conversions.
func (e *Emitter) args(recv string, list []ast.Expr, m *ast.Method) string {
	var parts []string
	if recv != "" && m != nil && !m.IsStatic() {
		parts = append(parts, "("+cname(m.Owner)+"*)"+recv)
	} else if recv != "" && m == nil {
		parts = append(parts, recv)
	}
	for i, a := range list {
		var want ast.Type
		if m != nil {
			if i < len(m.Params) {
				want = m.Params[i]
			} else if m.Varargs && len(m.Params) > 0 {
				if arr, ok := m.Params[len(m.Params)-1].(*ast.ArrayType); ok {
					want = arr.Elem
				}
			}
		}
		parts = append(parts, e.coerce(e.expr(a), a.GetType(), want))
	}
	// varargs packing
	if m != nil && m.Varargs && len(list) != len(m.Params) {
		e.packVarargs(&parts, list, m)
	}
	if len(parts) == 0 {
		return ""
	}
	return strings.Join(parts, ", ")
}

func (e *Emitter) packVarargs(parts *[]string, list []ast.Expr, m *ast.Method) {
	// last parameter is an array; gather the remaining arguments into one
	if len(*parts) == 0 {
		return
	}
	n := len(m.Params) - 1
	head := (*parts)[:n+boolToInt(!m.IsStatic())]
	tail := (*parts)[n+boolToInt(!m.IsStatic()):]
	elem := m.Params[len(m.Params)-1].(*ast.ArrayType).Elem
	es := e.elemSize(elem)
	var b strings.Builder
	b.WriteString("({ tyarr* _va = ty_array_new(" + fmt.Sprint(len(tail)) + ", " + es + ");")
	if e.isRef(elem) {
		b.WriteString(" _va->refs = 1;")
	}
	for i, t := range tail {
		if e.isRef(elem) {
			fmt.Fprintf(&b, " ((void*)_va->data)[%d] = (void*)%s;", i, t)
		} else {
			fmt.Fprintf(&b, " ((%s*)_va->data)[%d] = %s;", e.ctype(elem), i, t)
		}
	}
	b.WriteString(" _va; })")
	*parts = append(head, b.String())
}

func boolToInt(b bool) int {
	if b {
		return 1
	}
	return 0
}

func (e *Emitter) forEach(v *ast.ForEach) {
	e.line("{\n")
	e.indent++
	name := e.localName(v.Var.Sym)
	x := e.expr(v.X)
	if !v.Iterable {
		ix := e.tmpName()
		e.line("tyarr* %s = (tyarr*)%s;\n", ix, x)
		if v.Var.Sym != nil {
			e.line("int64_t %s = 0;\n", ix+"_i")
			e.line("for (%s = 0; %s < %s->len; %s++) {\n", ix+"_i", ix+"_i", ix, ix+"_i")
			e.indent++
			if e.isRefElem(v.Elem) {
				e.line("%s %s = (%s)((void**)%s->data)[%s];\n", e.ctype(v.Elem), name, e.ctype(v.Elem), ix, ix+"_i")
			} else {
				e.line("%s %s = ((%s*)%s->data)[%s];\n", e.ctype(v.Elem), name, e.ctype(v.Elem), ix, ix+"_i")
			}
		}
		e.stmtAsBlock(v.Body)
		e.indent--
		e.line("}\n")
		e.indent--
		e.line("}\n")
		return
	}
	// Iterable protocol
	itSel := e.selectorOf(e.prog.Builtins.Iterable, "iterator")
	hnSel := e.selectorOf(e.prog.Builtins.Iterator, "hasNext")
	nxSel := e.selectorOf(e.prog.Builtins.Iterator, "next")
	it := e.tmpName()
	e.line("void* %s = ((void*(*)(void*))ty_itab((tyobj*)%s, %d))((tyobj*)%s);\n", it, x, itSel, x)
	e.line("while (((int32_t(*)(void*))ty_itab((tyobj*)%s, %d))(%s)) {\n", it, hnSel, it)
	e.indent++
	if v.Var.Sym != nil {
		e.line("%s %s = (%s)((void*(*)(void*))ty_itab((tyobj*)%s, %d))(%s);\n", e.ctype(v.Elem), name, e.ctype(v.Elem), it, nxSel, it)
	}
	e.stmtAsBlock(v.Body)
	e.indent--
	e.line("}\n")
	e.indent--
	e.line("}\n")
}

func (e *Emitter) selectorOf(cl *ast.Class, name string) int {
	for _, m := range cl.Methods[name] {
		if m.Selector >= 0 {
			return m.Selector
		}
	}
	return -1
}

// ---------------------------------------------------------------- try

func (e *Emitter) tryStmt(v *ast.Try) {
	if len(v.Resources) > 0 {
		e.tryWithResources(v)
		return
	}
	e.emitTryCore(v)
}

func (e *Emitter) tryWithResources(v *ast.Try) {
	// resources are allocated in the enclosing block and closed at the end
	e.line("{\n")
	e.indent++
	for _, r := range v.Resources {
		e.stmt(r)
	}
	closure := &ast.Try{Pos: v.Pos, Body: v.Body, Catches: v.Catches, Finally: nil}
	e.emitTryCore(closure)
	// close in reverse order
	for i := len(v.Resources) - 1; i >= 0; i-- {
		var name string
		switch r := v.Resources[i].(type) {
		case *ast.LocalVar:
			name = e.localName(r.Vars[0].Sym)
		case *ast.ExprStmt:
			name = e.tmpRef(e.expr(r.X))
		}
		if name != "" {
			e.line("if (%s) ((void(*)(void*))ty_itab((tyobj*)%s, %d))((tyobj*)%s);\n",
				name, name, e.selectorOf(e.prog.Builtins.AutoCloseable, "close"), name)
		}
	}
	if v.Finally != nil {
		e.emitBlockInner(v.Finally)
	}
	e.indent--
	e.line("}\n")
}

func (e *Emitter) emitTryCore(v *ast.Try) {
	hasFinally := v.Finally != nil
	if hasFinally {
		e.line("{ tycatch _fin; tyobj* _finex = NULL; int _finok = 0;\n")
		e.indent++
		e.line("_fin.prev = ty_cur_catch; _fin.ex = NULL; ty_cur_catch = &_fin;\n")
		e.line("if (setjmp(_fin.buf) == 0) {\n")
		e.indent++
	}
	if len(v.Catches) > 0 {
		c := e.tmpName()
		e.line("{ tycatch %s; %s.prev = ty_cur_catch; %s.ex = NULL; ty_cur_catch = &%s;\n", c, c, c, c)
		e.indent++
		e.line("if (setjmp(%s.buf) == 0) {\n", c)
		e.indent++
		e.emitBlockInner(v.Body)
		e.indent--
		e.line("} else {\n")
		e.indent++
		e.line("tyobj* _ex = %s.ex;\n", c)
		e.line("ty_cur_catch = %s.prev;\n", c)
		first := true
		for i, cat := range v.Catches {
			cond := e.catchCond(cat)
			if first {
				e.line("if (%s) {\n", cond)
				first = false
			} else {
				e.line("else if (%s) {\n", cond)
			}
			e.indent++
			e.line("v%d_%s = (%s)(void*)_ex;\n", cat.Sym.ID, mangle(cat.Sym.Name), e.ctype(cat.Sym.Type))
			e.emitBlockInner(cat.Body)
			e.indent--
			e.line("}\n")
			_ = i
		}
		e.line("else { ty_cur_catch = %s.prev; ty_throw(_ex); }\n", c)
		e.indent--
		e.line("}\n")
		e.line("ty_cur_catch = %s.prev;\n", c)
		e.indent--
		e.line("}\n")
	} else {
		e.emitBlockInner(v.Body)
	}
	if hasFinally {
		e.indent--
		e.line("} else { _finex = _fin.ex; }\n")
		e.line("ty_cur_catch = _fin.prev;\n")
		e.emitBlockInner(v.Finally)
		e.line("if (_finex) ty_throw(_finex);\n")
		e.indent--
		e.line("}\n")
		_ = _finokUnused
	}
}

var _finokUnused = 0

func (e *Emitter) catchCond(cat *ast.Catch) string {
	var parts []string
	for _, te := range cat.Types {
		t := te.Resolved
		if ct, ok := t.(*ast.ClassType); ok {
			parts = append(parts, fmt.Sprintf("ty_instanceof(_ex, &cls_%s)", mangle(ct.Class.Full)))
		}
	}
	if len(parts) == 0 {
		return "1"
	}
	return strings.Join(parts, " || ")
}

// ---------------------------------------------------------------- switch

func (e *Emitter) switchStmt(s *ast.Switch, resultTmp string) {
	e.line("{\n")
	e.indent++
	selT := e.ctype(s.X.GetType())
	if s.Kind == ast.SwitchString {
		e.line("tystr* _s = (tystr*)%s;\n", e.expr(s.X))
		e.line("int _k = -1;\n")
		for i, cs := range s.Cases {
			if cs.Default || len(cs.Labels) == 0 {
				continue
			}
			for _, l := range cs.Labels {
				e.line("if (_k < 0 && ty_str_eq(_s, %s)) _k = %d;\n", e.tmpRef(e.expr(l)), i)
			}
		}
		e.line("switch (_k) {\n")
		for i := range s.Cases {
			e.line("case %d: goto _c%d;\n", i, i)
		}
		e.line("default: goto _cd;\n}\n")
	} else {
		e.line("%s _s = %s;\n", selT, e.expr(s.X))
		if s.Kind == ast.SwitchEnum {
			e.line("int32_t _e = ((int32_t(*)(void*))%s)((void*)_s);\n", e.enumOrdinalFn())
			e.line("switch (_e) {\n")
		} else {
			e.line("switch ((int64_t)_s) {\n")
		}
		for i, cs := range s.Cases {
			if cs.Default {
				continue
			}
			for _, l := range cs.Labels {
				e.line("case %s: ", e.constInt(l))
			}
			if len(cs.Labels) > 0 {
				e.line("goto _c%d;\n", i)
			}
		}
		e.line("default: goto _cd;\n}\n")
	}
	for i, cs := range s.Cases {
		if cs.Default {
			continue
		}
		e.line("_c%d: ;\n", i)
		e.indent++
		e.switchCaseBody(cs, resultTmp)
		e.indent--
	}
	e.line("_cd: ;\n")
	e.indent++
	for _, cs := range s.Cases {
		if cs.Default {
			e.switchCaseBody(cs, resultTmp)
		}
	}
	e.indent--
	e.line("_end: ;\n")
	e.indent--
	e.line("}\n")
}

func (e *Emitter) constInt(l ast.Expr) string {
	if lit, ok := l.(*ast.Literal); ok {
		switch lit.Kind {
		case ast.LitChar, ast.LitInt, ast.LitLong:
			return fmt.Sprint(int64(lit.Int))
		}
	}
	if cv := e.prog.ConstInt(l); cv != nil {
		return fmt.Sprint(*cv)
	}
	return "0"
}

// switchCaseBody emits one case body; Java colon cases fall through.
func (e *Emitter) switchCaseBody(cs *ast.Case, resultTmp string) {
	if cs.ArrowX != nil {
		if resultTmp != "" {
			e.line("%s = %s;\n", resultTmp, e.expr(cs.ArrowX))
		} else {
			e.line("(void)(%s);\n", e.expr(cs.ArrowX))
		}
		e.line("goto _end;\n")
		return
	}
	for _, st := range cs.Body {
		if y, ok := st.(*ast.Yield); ok && resultTmp != "" {
			e.line("%s = %s;\n", resultTmp, e.expr(y.X))
			e.line("goto _end;\n")
			continue
		}
		if b, ok := st.(*ast.Block); ok && resultTmp != "" {
			e.emitYieldBlock(b, resultTmp)
			continue
		}
		if _, isBreak := st.(*ast.Break); isBreak {
			e.line("goto _end;\n")
			continue
		}
		e.stmt(st)
	}
}

func (e *Emitter) emitYieldBlock(b *ast.Block, resultTmp string) {
	for _, st := range b.Stmts {
		if y, ok := st.(*ast.Yield); ok {
			e.line("%s = %s;\n", resultTmp, e.expr(y.X))
			continue
		}
		e.stmt(st)
	}
}

func (e *Emitter) enumOrdinalFn() string {
	if e.enumOrdinal != "" {
		return e.enumOrdinal
	}
	cl := e.prog.Builtins.Enum
	for _, m := range cl.Methods["ordinal"] {
		if m.VIndex >= 0 {
			e.enumOrdinal = "((void*)this)->obj.cls->vtable[" + fmt.Sprint(m.VIndex) + "]"
			return e.enumOrdinal
		}
	}
	e.enumOrdinal = "(void*)0"
	return e.enumOrdinal
}

var _ = ast.ModPublic
