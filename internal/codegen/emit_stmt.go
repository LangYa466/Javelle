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
		e.hoistPatterns(v.Cond)
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
		e.clearPatterns()
	case *ast.While:
		labels := e.takeLabels()
		inner := e.capture(func() {
			e.hoistPatterns(v.Cond)
			e.line("while (%s) {\n", e.cond(v.Cond))
			e.indent++
			e.pushLoop("")
			e.stmtAsBlock(v.Body)
			e.popLoop()
			e.contLabels(labels)
			e.indent--
			e.line("}\n")
		})
		// the bindings are declared once, outside the loop
		e.code.WriteString(inner)
		e.brkLabels(labels)
		e.clearPatterns()
	case *ast.DoWhile:
		labels := e.takeLabels()
		e.line("do {\n")
		e.indent++
		e.pushLoop("")
		e.stmtAsBlock(v.Body)
		e.popLoop()
		e.contLabels(labels)
		e.indent--
		e.line("} while (%s);\n", e.cond(v.Cond))
		e.brkLabels(labels)
	case *ast.For:
		labels := e.takeLabels()
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
		// the update lives at the end of the body, so both an unlabelled
		// continue and a labelled one must jump over the rest of the body
		cont := e.tmpName()
		e.line("while (%s) {\n", cond)
		e.indent++
		e.pushLoop(cont)
		e.stmtAsBlock(v.Body)
		e.popLoop()
		e.contLabels(labels)
		e.line("%s: ;\n", cont)
		if len(up) > 0 {
			e.line("%s;\n", strings.Join(up, ", "))
		}
		e.indent--
		e.line("}\n")
		e.indent--
		e.line("}\n")
		e.brkLabels(labels)
	case *ast.ForEach:
		e.forEach(v)
	case *ast.Return:
		if v.X == nil {
			e.line("return;\n")
		} else {
			e.line("return %s;\n", e.coerce(e.expr(v.X), v.X.GetType(), e.retType))
		}
	case *ast.Break:
		if v.Label != "" {
			e.line("goto %s;\n", e.labelName(v.Label, true))
		} else {
			e.line("break;\n")
		}
	case *ast.Continue:
		switch {
		case v.Label != "":
			e.line("goto %s;\n", e.labelName(v.Label, false))
		case e.continueTarget() != "":
			e.line("goto %s;\n", e.continueTarget())
		default:
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
		// a loop turns the pending labels into its continue and break targets
		// (chained labels such as `a: b: for (...) {}` all apply to the loop);
		// any other statement only has the break target
		if isLoop(unwrapLabels(v.Body)) {
			e.pendingLabels = append(e.pendingLabels, v.Label)
			e.stmt(v.Body)
			return
		}
		e.stmt(v.Body)
		e.line("%s: ;\n", e.labelName(v.Label, true))
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

// unwrapLabels removes the labels stacked in front of a statement, so that
// `a: b: for (...) {}` is recognised as a labelled loop.
func unwrapLabels(s ast.Stmt) ast.Stmt {
	for {
		l, ok := s.(*ast.Labeled)
		if !ok {
			return s
		}
		s = l.Body
	}
}

func (e *Emitter) labelName(l string, brk bool) string {
	if brk {
		return "brk_" + mangle(l)
	}
	return "lbl_" + mangle(l)
}

// takeLabels removes the labels pending for the current statement, so that
// statements nested inside it do not inherit them.
func (e *Emitter) takeLabels() []string {
	l := e.pendingLabels
	e.pendingLabels = nil
	return l
}

// contLabels emits the targets of `continue label`.
func (e *Emitter) contLabels(labels []string) {
	for _, l := range labels {
		e.line("%s: ;\n", e.labelName(l, false))
	}
}

// brkLabels emits the targets of `break label`.
func (e *Emitter) brkLabels(labels []string) {
	for _, l := range labels {
		e.line("%s: ;\n", e.labelName(l, true))
	}
}

// continueTarget returns the label an unlabelled continue must jump to in the
// innermost enclosing loop, or "" when C's continue statement already means
// the right thing.
func (e *Emitter) continueTarget() string {
	if len(e.loops) == 0 {
		return ""
	}
	return e.loops[len(e.loops)-1]
}

func (e *Emitter) pushLoop(target string) { e.loops = append(e.loops, target) }

func (e *Emitter) popLoop() { e.loops = e.loops[:len(e.loops)-1] }

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
	return e.argsFor(recv, list, m, nil)
}

// argsFor renders a call's argument list; call is used to consult the varargs
// decision made during overload resolution and may be nil.
func (e *Emitter) argsFor(recv string, list []ast.Expr, m *ast.Method, call *ast.Call) string {
	if call != nil && m != nil && m.Varargs && e.prog.VarargsDirect(call) {
		var parts []string
		if recv != "" && !m.IsStatic() {
			parts = append(parts, "("+cname(m.Owner)+"*)"+recv)
		}
		for i, a := range list {
			var want ast.Type
			if i < len(m.Params) {
				want = m.Params[i]
			}
			parts = append(parts, e.coerce(e.expr(a), a.GetType(), want))
		}
		return strings.Join(parts, ", ")
	}
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
	// varargs packing, unless overload resolution chose to pass the array itself
	if m != nil && m.Varargs {
		direct := call != nil && e.prog.VarargsDirect(call)
		if !direct && call != nil {
			e.packVarargs(&parts, list, m)
		} else if call == nil && len(list) != len(m.Params) {
			e.packVarargs(&parts, list, m)
		}
	}
	if len(parts) == 0 {
		return ""
	}
	return strings.Join(parts, ", ")
}

func (e *Emitter) packVarargs(parts *[]string, list []ast.Expr, m *ast.Method) {
	// the last parameter is an array; gather the remaining arguments into one
	n := len(m.Params) - 1
	off := boolToInt(!m.IsStatic())
	head := []string{}
	tail := *parts
	if len(*parts) >= n+off {
		head = (*parts)[:n+off]
		tail = (*parts)[n+off:]
	}
	elem := m.Params[len(m.Params)-1].(*ast.ArrayType).Elem
	es := e.elemSize(elem)
	var b strings.Builder
	b.WriteString("({ tyarr* _va = ty_array_new(" + fmt.Sprint(len(tail)) + ", " + es + ");")
	if e.isRef(elem) {
		b.WriteString(" _va->refs = 1;")
	}
	for i, t := range tail {
		if e.isRef(elem) {
			fmt.Fprintf(&b, " ((void**)_va->data)[%d] = (void*)%s;", i, t)
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
	labels := e.takeLabels()
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
		e.pushLoop("")
		e.stmtAsBlock(v.Body)
		e.popLoop()
		e.contLabels(labels)
		e.indent--
		e.line("}\n")
		e.indent--
		e.line("}\n")
		e.brkLabels(labels)
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
	e.pushLoop("")
	e.stmtAsBlock(v.Body)
	e.popLoop()
	e.contLabels(labels)
	e.indent--
	e.line("}\n")
	e.indent--
	e.line("}\n")
	e.brkLabels(labels)
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
			e.line("%s %s = (%s)(void*)_ex;\n", e.ctype(cat.Sym.Type), e.localName(cat.Sym), e.ctype(cat.Sym.Type))
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

// hoistPatterns declares the variables bound by `instanceof` patterns that
// appear in a controlling expression, so the condition can refer to them.
func (e *Emitter) hoistPatterns(cond ast.Expr) {
	if cond == nil {
		return
	}
	e.patternVars = map[*ast.InstanceOf]string{}
	var walk func(ast.Expr)
	walk = func(x ast.Expr) {
		switch v := x.(type) {
		case *ast.InstanceOf:
			if v.Binding != nil {
				name := e.bindExprPattern(v)
				if name != "" {
					e.patternVars[v] = name
				}
			}
			walk(v.X)
		case *ast.Binary:
			walk(v.X)
			walk(v.Y)
		case *ast.Unary:
			walk(v.X)
		case *ast.Cond:
			walk(v.C)
			walk(v.X)
			walk(v.Y)
		case *ast.Conv:
			walk(v.X)
		}
	}
	walk(cond)
}

// bindExprPattern declares the variable of an instanceof pattern and returns
// the C expression that holds the matched value (empty for `_`).
func (e *Emitter) bindExprPattern(v *ast.InstanceOf) string {
	pat := v.Binding
	if pat == nil {
		return ""
	}
	src := e.expr(v.X)
	typeName := e.ctype(v.Type.Resolved)
	n := e.tmpName()
	e.line("%s %s = (%s)(void*)%s;\n", typeName, n, typeName, src)
	if len(pat.Decomp) > 0 {
		e.bindComponents(pat, n)
		return n
	}
	if pat.Sym != nil && !pat.Unnamed {
		e.locals[pat.Sym] = n
		return n
	}
	return n
}

// bindComponents extracts the record components of a record pattern.
func (e *Emitter) bindComponents(p *ast.Param, recv string) {
	for i, sub := range p.Decomp {
		if i >= len(p.Comps) {
			return
		}
		comp := p.Comps[i]
		if len(sub.Decomp) > 0 {
			inner := e.tmpName()
			ct := e.ctype(comp.Type)
			e.line("%s %s = (%s)(%s)->f_%s;\n", ct, inner, ct, recv, mangle(comp.Name))
			e.bindComponents(sub, inner)
			continue
		}
		if sub.Sym == nil || sub.Unnamed {
			continue
		}
		ct := e.ctype(comp.Type)
		e.locals[sub.Sym] = e.tmpName()
		e.line("%s %s = (%s)(%s)->f_%s;\n", ct, e.locals[sub.Sym], ct, recv, mangle(comp.Name))
	}
}

// clearPatterns drops the substitutions recorded for one controlling expression.
func (e *Emitter) clearPatterns() { e.patternVars = nil }

// switchNeedsChain reports whether the switch uses patterns or guards, which
// cannot be expressed as a plain C switch and are lowered as an if/else chain.
func switchNeedsChain(s *ast.Switch) bool {
	for _, cs := range s.Cases {
		if cs.Pattern != nil || cs.Guard != nil {
			return true
		}
	}
	return false
}

// switchStmt lowers a switch statement or expression. Cases are emitted as
// labels so that colon-form cases keep Java's fall-through semantics; resultTmp
// is non-empty for switch expressions and receives the yielded value.
func (e *Emitter) switchStmt(s *ast.Switch, resultTmp string) {
	if len(s.Cases) == 0 {
		return
	}
	id := e.switchID
	e.switchID++
	if switchNeedsChain(s) {
		e.switchChain(s, resultTmp, id)
		return
	}
	e.line("{\n")
	e.indent++
	selT := e.ctype(s.X.GetType())
	switch s.Kind {
	case ast.SwitchString:
		e.line("tystr* _s%d = (tystr*)%s;\n", id, e.expr(s.X))
		e.line("int _k%d = -1;\n", id)
		for i, cs := range s.Cases {
			if isDefaultCase(cs) {
				continue
			}
			for _, l := range cs.Labels {
				e.line("if (_k%d < 0 && ty_str_eq(_s%d, %s)) _k%d = %d;\n", id, id, e.tmpRef(e.expr(l)), id, i)
			}
		}
		e.line("switch (_k%d) {\n", id)
	case ast.SwitchEnum:
		e.line("%s _s%d = %s;\n", selT, id, e.expr(s.X))
		e.line("int32_t _e%d = ty_enum_ordinal((void*)_s%d);\n", id, id)
		e.line("switch (_e%d) {\n", id)
	default:
		e.line("%s _s%d = %s;\n", selT, id, e.expr(s.X))
		e.line("switch ((int64_t)_s%d) {\n", id)
	}
	for i, cs := range s.Cases {
		if isDefaultCase(cs) {
			continue
		}
		for _, l := range cs.Labels {
			e.line("case %s: ", e.constInt(l))
		}
		if len(cs.Labels) > 0 {
			e.line("goto _c%d_%d;\n", id, i)
		}
	}
	e.line("default: goto _cd%d;\n}\n", id)
	for i, cs := range s.Cases {
		if isDefaultCase(cs) {
			continue
		}
		e.line("_c%d_%d: ;\n", id, i)
		e.indent++
		e.switchCaseBody(cs, resultTmp, id)
		e.indent--
	}
	e.line("_cd%d: ;\n", id)
	e.indent++
	for _, cs := range s.Cases {
		if isDefaultCase(cs) {
			e.switchCaseBody(cs, resultTmp, id)
		}
	}
	e.indent--
	e.line("_end%d: ;\n", id)
	e.indent--
	e.line("}\n")
}

// isDefaultCase reports whether a case is the default branch.
func isDefaultCase(cs *ast.Case) bool {
	return cs.Default || (len(cs.Labels) == 0 && cs.Pattern == nil && !cs.Null)
}

// switchChain lowers a switch with type patterns or guards into an if/else
// chain that computes the case index, then reuses the labelled-case scheme.
func (e *Emitter) switchChain(s *ast.Switch, resultTmp string, id int) {
	e.line("{\n")
	e.indent++
	selT := e.ctype(s.X.GetType())
	e.line("%s _s%d = %s;\n", selT, id, e.expr(s.X))
	e.line("int _k%d = -1;\n", id)
	def := -1
	n := 0
	for i, cs := range s.Cases {
		if isDefaultCase(cs) {
			def = i
			continue
		}
		kw := "if"
		if n > 0 {
			kw = "else if"
		}
		n++
		e.line("%s (%s) { _k%d = %d; }\n", kw, e.caseCond(s, cs, id), id, i)
	}
	if def >= 0 {
		if n > 0 {
			e.line("else { _k%d = %d; }\n", id, def)
		} else {
			e.line("_k%d = %d;\n", id, def)
		}
	} else if n > 0 && resultTmp != "" {
		// A switch expression with no matching case and no default has no value.
		e.line("if (_k%d < 0) { ty_throw((tyobj*)ty_illegal_state(%s)); }\n", id, e.cstr("no matching switch case"))
	}
	e.line("switch (_k%d) {\n", id)
	for i, cs := range s.Cases {
		if isDefaultCase(cs) {
			continue
		}
		e.line("case %d: goto _c%d_%d;\n", i, id, i)
	}
	e.line("default: goto _cd%d;\n}\n", id)
	for i, cs := range s.Cases {
		if isDefaultCase(cs) {
			continue
		}
		e.line("_c%d_%d: ;\n", id, i)
		e.indent++
		e.emitPatternBinding(cs, id)
		e.switchCaseBody(cs, resultTmp, id)
		e.indent--
	}
	e.line("_cd%d: ;\n", id)
	e.indent++
	for _, cs := range s.Cases {
		if isDefaultCase(cs) {
			e.emitPatternBinding(cs, id)
			e.switchCaseBody(cs, resultTmp, id)
		}
	}
	e.indent--
	e.line("_end%d: ;\n", id)
	e.indent--
	e.line("}\n")
}

// emitPatternBinding declares the variables of a type or record pattern case.
func (e *Emitter) emitPatternBinding(cs *ast.Case, id int) {
	if cs.Pattern == nil {
		return
	}
	src := fmt.Sprintf("((void*)_s%d)", id)
	if len(cs.Pattern.Decomp) > 0 {
		n := e.tmpName()
		ct := e.ctype(cs.Pattern.Type.Resolved)
		e.line("%s %s = (%s)%s;\n", ct, n, ct, src)
		e.bindComponents(cs.Pattern, n)
		return
	}
	if cs.Pattern.Sym == nil || cs.Pattern.Unnamed {
		return
	}
	ct := e.ctype(cs.Pattern.Sym.Type)
	e.line("%s %s = (%s)%s;\n", ct, e.localName(cs.Pattern.Sym), ct, src)
}

// caseCond renders the condition that selects a case.
func (e *Emitter) caseCond(s *ast.Switch, cs *ast.Case, id int) string {
	var parts []string
	if cs.Null {
		parts = append(parts, fmt.Sprintf("(_s%d == NULL)", id))
	}
	for _, l := range cs.Labels {
		if s.Kind == ast.SwitchString {
			parts = append(parts, fmt.Sprintf("ty_str_eq((tystr*)_s%d, (tystr*)%s)", id, e.expr(l)))
			continue
		}
		parts = append(parts, fmt.Sprintf("((int64_t)_s%d == %s)", id, e.constInt(l)))
	}
	if cs.Pattern != nil {
		parts = append(parts, fmt.Sprintf("ty_instanceof((void*)_s%d, %s)", id, e.classOf(cs.Pattern.Type.Resolved)))
	}
	cond := "1"
	if len(parts) > 0 {
		cond = "(" + strings.Join(parts, " || ") + ")"
	}
	if cs.Guard == nil {
		return cond
	}
	guard := e.expr(cs.Guard)
	if cs.Pattern != nil {
		inner := e.capture(func() { e.emitPatternBinding(cs, id) })
		return fmt.Sprintf("({ %s (%s) && (%s); })", inner, cond, guard)
	}
	return "(" + cond + " && " + guard + ")"
}

// classOf renders the class descriptor of a resolved type.
func (e *Emitter) classOf(t ast.Type) string {
	if ct, ok := t.(*ast.ClassType); ok {
		return "&cls_" + mangle(ct.Class.Full)
	}
	return "&cls_" + mangle(e.prog.ArrayClass().Full)
}

// constInt renders a case label as a C integer constant.
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
func (e *Emitter) switchCaseBody(cs *ast.Case, resultTmp string, id int) {
	if cs.ArrowX != nil {
		if resultTmp != "" {
			e.line("%s = %s;\n", resultTmp, e.expr(cs.ArrowX))
		} else {
			e.line("(void)(%s);\n", e.expr(cs.ArrowX))
		}
		e.line("goto _end%d;\n", id)
		return
	}
	for _, st := range cs.Body {
		if y, ok := st.(*ast.Yield); ok {
			if resultTmp != "" {
				e.line("%s = %s;\n", resultTmp, e.expr(y.X))
			}
			e.line("goto _end%d;\n", id)
			continue
		}
		if _, isBreak := st.(*ast.Break); isBreak {
			e.line("goto _end%d;\n", id)
			continue
		}
		if b, ok := st.(*ast.Block); ok && resultTmp != "" {
			e.emitYieldBlock(b, resultTmp, id)
			continue
		}
		e.stmt(st)
	}
}

// emitYieldBlock handles blocks that yield a value inside a switch expression.
func (e *Emitter) emitYieldBlock(b *ast.Block, resultTmp string, id int) {
	for _, st := range b.Stmts {
		if y, ok := st.(*ast.Yield); ok {
			e.line("%s = %s;\n", resultTmp, e.expr(y.X))
			e.line("goto _end%d;\n", id)
			continue
		}
		e.stmt(st)
	}
}

var _ = ast.ModPublic
