package codegen

import (
	"fmt"
	"strings"

	"github.com/LangYa466/Teyru/internal/ast"
)

// cstr returns a C string literal for s.
func (e *Emitter) cstr(s string) string {
	var b strings.Builder
	b.WriteByte('"')
	for i := 0; i < len(s); i++ {
		c := s[i]
		switch {
		case c == '"':
			b.WriteString("\\\"")
		case c == '\\':
			b.WriteString("\\\\")
		case c == '\n':
			b.WriteString("\\n")
		case c == '\r':
			b.WriteString("\\r")
		case c == '\t':
			b.WriteString("\\t")
		case c < 32 || c > 126:
			fmt.Fprintf(&b, "\\%03o", c)
		default:
			b.WriteByte(c)
		}
	}
	b.WriteByte('"')
	return b.String()
}

// tmpRef materialises an expression into a statement-expression so it can be
// referenced more than once without double evaluation.
func (e *Emitter) tmpRef(x string) string {
	n := e.tmpName()
	return "({ __typeof__(" + x + ") " + n + " = " + x + "; " + n + "; })"
}

// cond renders a boolean condition.
func (e *Emitter) cond(x ast.Expr) string {
	return e.expr(x)
}

// refExpr renders an expression that is being used as an object reference.
func (e *Emitter) refExpr(x ast.Expr) string {
	return e.expr(x)
}

// coerce converts an emitted value from src to dst type.
func (e *Emitter) coerce(v string, src, dst ast.Type) string {
	if src == nil || dst == nil || dst == ast.TVoid || ast.IsError(src) || ast.IsError(dst) {
		return v
	}
	if _, ok := dst.(*ast.TypeVarType); ok {
		return v
	}
	if _, ok := src.(*ast.TypeVarType); ok {
		return v
	}
	_, sp := src.(*ast.PrimType)
	_, dp := dst.(*ast.PrimType)
	switch {
	case sp && dp:
		return v // C handles numeric conversions implicitly
	case sp && !dp:
		return e.boxCall(v, src.(*ast.PrimType), dst)
	case !sp && dp:
		return e.unboxCall(v, src, dst.(*ast.PrimType))
	case !sp && !dp:
		if e.isRef(src) && e.isRef(dst) {
			return "(" + e.ctype(dst) + ")" + v
		}
	}
	return v
}

func (e *Emitter) boxCall(v string, p *ast.PrimType, dst ast.Type) string {
	if ct, ok := dst.(*ast.ClassType); ok {
		if fn := boxFn(p.Kind); fn != "" && ct.Class.Special == "box" {
			return fn + "(" + v + ")"
		}
		if ct.Class.Special == "Object" {
			return "(tyobj*)" + boxFn(p.Kind) + "(" + v + ")"
		}
	}
	return v
}

func boxFn(k ast.PrimKind) string {
	switch k {
	case ast.Boolean:
		return "ty_box_bool"
	case ast.Byte:
		return "ty_box_byte"
	case ast.Short:
		return "ty_box_short"
	case ast.Char:
		return "ty_box_char"
	case ast.Int:
		return "ty_box_int"
	case ast.Long:
		return "ty_box_long"
	case ast.Float:
		return "ty_box_float"
	case ast.Double:
		return "ty_box_double"
	}
	return ""
}

func (e *Emitter) unboxCall(v string, src ast.Type, p *ast.PrimType) string {
	if ct, ok := src.(*ast.ClassType); ok {
		if _, ok2 := e.prog.Builtins.Unbox[ct.Class]; ok2 {
			recv := v
			switch p.Kind {
			case ast.Boolean:
				return "ty_unbox_bool((void*)" + recv + ")"
			case ast.Byte:
				return "ty_unbox_byte((void*)" + recv + ")"
			case ast.Short:
				return "ty_unbox_short((void*)" + recv + ")"
			case ast.Char:
				return "ty_unbox_char((void*)" + recv + ")"
			case ast.Int:
				return "ty_unbox_int((void*)" + recv + ")"
			case ast.Long:
				return "ty_unbox_long((void*)" + recv + ")"
			case ast.Float:
				return "ty_unbox_float((void*)" + recv + ")"
			case ast.Double:
				return "ty_unbox_double((void*)" + recv + ")"
			}
		}
	}
	return v
}

// ---------------------------------------------------------------- expressions

func (e *Emitter) expr(x ast.Expr) string {
	if x == nil {
		return "0"
	}
	if v, ok := e.prog.Lowered(x); ok {
		return e.expr(v)
	}
	switch v := x.(type) {
	case *ast.Literal:
		return e.literal(v)
	case *ast.Ident:
		return e.ident(v)
	case *ast.Select:
		return e.selectExpr(v)
	case *ast.Index:
		return e.indexExpr(v)
	case *ast.Call:
		if v.ThisCtor {
			return ""
		}
		return e.callExpr(v)
	case *ast.New:
		return e.newExpr(v)
	case *ast.NewArray:
		return e.newArray(v)
	case *ast.ArrayInit:
		return e.arrayInit(v)
	case *ast.Unary:
		return e.unary(v)
	case *ast.Binary:
		return e.binary(v)
	case *ast.Assign:
		return e.assign(v)
	case *ast.Cond:
		return "(" + e.cond(v.C) + " ? " + e.coerce(e.expr(v.X), v.X.GetType(), v.GetType()) +
			" : " + e.coerce(e.expr(v.Y), v.Y.GetType(), v.GetType()) + ")"
	case *ast.Cast:
		return e.cast(v)
	case *ast.InstanceOf:
		return e.instanceOf(v)
	case *ast.Conv:
		return e.coerce(e.expr(v.X), v.X.GetType(), v.GetType())
	case *ast.This:
		if v.Qual != "" {
			return "((void*)this)"
		}
		return "this"
	case *ast.SuperExpr:
		return "((void*)this)"
	case *ast.ClassLit:
		t := v.Type.Resolved
		if ct, ok := t.(*ast.ClassType); ok {
			return "((tyobj*)&cls_" + mangle(ct.Class.Full) + ")"
		}
		return "((tyobj*)&cls_" + mangle(e.prog.Builtins.Object.Full) + ")"
	case *ast.Lambda:
		return e.lambdaExpr(v)
	case *ast.MethodRef:
		if v.Lam != nil {
			return e.lambdaExpr(v.Lam)
		}
		return "NULL"
	case *ast.SwitchExpr:
		n := e.tmpName()
		e.line("{\n")
		e.indent++
		e.line("%s %s = %s;\n", e.ctype(v.GetType()), n, zeroOf(e.ctype(v.GetType())))
		e.switchStmt(v.S, n)
		e.indent--
		e.line("}\n")
		return n
	}
	return "0"
}

func (e *Emitter) literal(v *ast.Literal) string {
	switch v.Kind {
	case ast.LitInt:
		i := int32(v.Int)
		if i == -2147483648 {
			return "(-2147483647 - 1)"
		}
		return fmt.Sprintf("%d", i)
	case ast.LitLong:
		return fmt.Sprintf("%dLL", int64(v.Int))
	case ast.LitFloat:
		return fmt.Sprintf("%vf", v.Flt)
	case ast.LitDouble:
		return fmt.Sprintf("%v", v.Flt)
	case ast.LitChar:
		return fmt.Sprintf("((uint16_t)%d)", uint16(v.Int))
	case ast.LitString:
		return e.strLit(v.Str)
	case ast.LitBool:
		if v.Bool {
			return "1"
		}
		return "0"
	case ast.LitNull:
		return "NULL"
	}
	return "0"
}

// strLit interns a string literal into a static global.
func (e *Emitter) strLit(s string) string {
	if id, ok := e.strings[s]; ok {
		return fmt.Sprintf("((tystr*)&S%d)", id)
	}
	id := len(e.strOrder)
	e.strings[s] = id
	e.strOrder = append(e.strOrder, s)
	data := e.cstr(s)
	fmt.Fprintf(&e.data, "static tystr S%d = {{&cls_%s}, %d, %s};\n", id,
		mangle(e.prog.Builtins.String.Full), len(s), data)
	return fmt.Sprintf("((tystr*)&S%d)", id)
}

// finishStrings rewrites static string globals so their data pointer is valid.
func (e *Emitter) stringGlobals() string { return "" }

func (e *Emitter) ident(v *ast.Ident) string {
	switch r := v.Ref.(type) {
	case *ast.Var:
		return e.localName(r)
	case *ast.Field:
		return e.fieldAccess(r, "this")
	}
	return "0"
}

// fieldAccess renders a field read through a receiver expression.
func (e *Emitter) fieldAccess(f *ast.Field, recv string) string {
	name := "f_" + mangle(f.Name)
	if f.Mods.Has(ast.ModStatic) {
		return "G_" + mangle(f.Owner.Full) + "_" + mangle(f.Name)
	}
	ct := cname(f.Owner)
	return "((" + ct + "*)" + recv + ")->" + name
}

func (e *Emitter) selectExpr(v *ast.Select) string {
	if r, ok := v.Ref.(*ast.Class); ok {
		return "((tyobj*)&cls_" + mangle(r.Full) + ")"
	}
	if s, ok := v.Ref.(string); ok && s == "length" {
		arr := e.tmpRef(e.expr(v.X))
		return "(" + arr + " ? " + arr + "->len : (int64_t)ty_aioobe(0, 0))"
	}
	if f, ok := v.Ref.(*ast.Field); ok {
		if f.Mods.Has(ast.ModStatic) {
			return e.fieldAccess(f, "")
		}
		recv := e.expr(v.X)
		return e.fieldAccess(f, e.tmpRef(recv))
	}
	return "0"
}

func (e *Emitter) indexExpr(v *ast.Index) string {
	a := e.tmpRef(e.expr(v.X))
	i := e.expr(v.Index)
	elem := v.GetType()
	if e.isRef(elem) {
		return "((" + e.ctype(elem) + ")ty_arr_ref((tyarr*)" + a + ", " + i + "))"
	}
	return "((((" + e.ctype(elem) + "*)ty_arr_ptr((tyarr*)" + a + ", " + i + ")))[0])"
}

func (e *Emitter) cast(v *ast.Cast) string {
	src := v.X.GetType()
	dst := v.Type.Resolved
	_, sp := src.(*ast.PrimType)
	_, dp := dst.(*ast.PrimType)
	inner := e.expr(v.X)
	switch {
	case sp && dp:
		return "((" + e.ctype(dst) + ")(" + inner + "))"
	case sp && !dp:
		return e.boxCall(inner, src.(*ast.PrimType), dst)
	case !sp && dp:
		return e.unboxCall(inner, src, dst.(*ast.PrimType))
	}
	if ct, ok := dst.(*ast.ClassType); ok {
		return "((" + cname(ct.Class) + "*)ty_checkcast((tyobj*)" + e.refExpr(v.X) + ", &cls_" + mangle(ct.Class.Full) + "))"
	}
	if arr, ok := dst.(*ast.ArrayType); ok {
		_ = arr
		return "((tyarr*)ty_checkcast((tyobj*)" + e.refExpr(v.X) + ", &cls_" + mangle(e.prog.ArrayClass().Full) + "))"
	}
	return "(" + e.ctype(dst) + ")(" + inner + ")"
}

func (e *Emitter) instanceOf(v *ast.InstanceOf) string {
	dst := v.Type.Resolved
	target := ""
	if ct, ok := dst.(*ast.ClassType); ok {
		target = "&cls_" + mangle(ct.Class.Full)
	} else {
		target = "&cls_" + mangle(e.prog.ArrayClass().Full)
	}
	return "ty_instanceof((tyobj*)" + e.refExpr(v.X) + ", " + target + ")"
}

func (e *Emitter) unary(v *ast.Unary) string {
	x := e.expr(v.X)
	xt := v.X.GetType()
	switch v.Op {
	case "+":
		return "(" + x + ")"
	case "-":
		return "(-(" + x + "))"
	case "!":
		if _, ok := xt.(*ast.PrimType); !ok {
			return "(!ty_unbox_bool((void*)(" + x + ")))"
		}
		return "(!(" + x + "))"
	case "~":
		if _, ok := xt.(*ast.PrimType); !ok {
			return "(~(int32_t)ty_unbox_int((void*)(" + x + ")))"
		}
		return "(~(" + x + "))"
	case "++", "--":
		op := "+ 1"
		if v.Op == "--" {
			op = "- 1"
		}
		if v.Postfix {
			return "((" + e.lvalue(v.X) + ") += " + op + ", (" + e.lvalue(v.X) + ") - (" + op + "))"
		}
		return "((" + e.lvalue(v.X) + ") += " + op + ")"
	}
	return x
}

// lvalue renders an assignable expression.
func (e *Emitter) lvalue(x ast.Expr) string {
	switch v := x.(type) {
	case *ast.Ident:
		if f, ok := v.Ref.(*ast.Field); ok {
			return e.fieldAccess(f, "this")
		}
		return e.ident(v)
	case *ast.Select:
		if f, ok := v.Ref.(*ast.Field); ok {
			if f.Mods.Has(ast.ModStatic) {
				return e.fieldAccess(f, "")
			}
			return e.fieldAccess(f, e.tmpRef(e.expr(v.X)))
		}
		return "0"
	case *ast.Index:
		a := e.tmpRef(e.expr(v.X))
		i := e.expr(v.Index)
		elem := v.GetType()
		if e.isRef(elem) {
			return "((*((" + e.ctype(elem) + "*)ty_arr_slot_ref((tyarr*)" + a + ", " + i + "))))"
		}
		return "(((" + e.ctype(elem) + "*)ty_arr_ptr((tyarr*)" + a + ", " + i + "))[0])"
	}
	return e.expr(x)
}

func (e *Emitter) binary(v *ast.Binary) string {
	lt, rt := v.X.GetType(), v.Y.GetType()
	switch v.Op {
	case "==", "!=":
		return e.equality(v, lt, rt)
	case "&&":
		return "(" + e.cond(v.X) + " && " + e.cond(v.Y) + ")"
	case "||":
		return "(" + e.cond(v.X) + " || " + e.cond(v.Y) + ")"
	case "+":
		if e.isStringType(v.GetType()) {
			return e.concat(v)
		}
	case "/":
		return e.divExpr(v)
	case "%":
		return e.remExpr(v)
	}
	x := e.operand(v.X, v.OpType)
	y := e.operand(v.Y, v.OpType)
	return "(" + x + " " + v.Op + " " + y + ")"
}

func (e *Emitter) isStringType(t ast.Type) bool {
	ct, ok := t.(*ast.ClassType)
	return ok && ct.Class.Special == "String"
}

// operand coerces an operand of a numeric/bitwise operation.
func (e *Emitter) operand(x ast.Expr, op ast.Type) string {
	v := e.expr(x)
	if op == nil {
		return v
	}
	if _, ok := x.GetType().(*ast.PrimType); !ok {
		return e.unboxCall(v, x.GetType(), op.(*ast.PrimType))
	}
	return v
}

func (e *Emitter) equality(v *ast.Binary, lt, rt ast.Type) string {
	neg := ""
	if v.Op == "!=" {
		neg = "!"
	}
	ls, lsOk := lt.(*ast.PrimType)
	rs, rsOk := rt.(*ast.PrimType)
	_ = ls
	_ = rs
	switch {
	case lsOk && rsOk:
		return "(" + e.expr(v.X) + " " + v.Op + " " + e.expr(v.Y) + ")"
	case lsOk != rsOk:
		// boxed/unboxed comparison
		x, y := e.expr(v.X), e.expr(v.Y)
		if lsOk {
			y = e.unboxCall(y, rt, ls)
		} else {
			x = e.unboxCall(x, lt, rs)
		}
		return "(" + x + " " + v.Op + " " + y + ")"
	}
	if e.isStringType(lt) && e.isStringType(rt) {
		return "(" + neg + "ty_str_eq((tystr*)" + e.expr(v.X) + ", (tystr*)" + e.expr(v.Y) + "))"
	}
	return "(" + e.expr(v.X) + " " + v.Op + " " + e.expr(v.Y) + ")"
}

func (e *Emitter) divExpr(v *ast.Binary) string {
	x, y := e.expr(v.X), e.expr(v.Y)
	if ast.IsPrim(v.OpType, ast.Long) {
		return "ty_div_long(" + x + ", " + y + ")"
	}
	return "ty_div_int(" + x + ", " + y + ")"
}

func (e *Emitter) remExpr(v *ast.Binary) string {
	x, y := e.expr(v.X), e.expr(v.Y)
	if ast.IsPrim(v.OpType, ast.Long) {
		return "ty_rem_long(" + x + ", " + y + ")"
	}
	return "ty_rem_int(" + x + ", " + y + ")"
}

// concat renders Java string concatenation.
func (e *Emitter) concat(v *ast.Binary) string {
	parts := []string{}
	var collect func(ast.Expr)
	collect = func(x ast.Expr) {
		if b, ok := x.(*ast.Binary); ok && b.Op == "+" && e.isStringType(b.GetType()) {
			collect(b.X)
			collect(b.Y)
			return
		}
		parts = append(parts, e.stringOperand(x))
	}
	collect(v.X)
	collect(v.Y)
	out := parts[0]
	for _, p := range parts[1:] {
		out = "ty_str_concat(" + out + ", " + p + ")"
	}
	return out
}

func (e *Emitter) stringOperand(x ast.Expr) string {
	t := x.GetType()
	if e.isStringType(t) {
		return "(tystr*)" + e.expr(x)
	}
	if p, ok := t.(*ast.PrimType); ok {
		switch p.Kind {
		case ast.Boolean:
			return "ty_str_of_bool(" + e.expr(x) + ")"
		case ast.Char:
			return "ty_str_of_char(" + e.expr(x) + ")"
		case ast.Float:
			return "ty_str_of_float(" + e.expr(x) + ")"
		case ast.Double:
			return "ty_str_of_double(" + e.expr(x) + ")"
		case ast.Byte, ast.Short, ast.Int, ast.Long:
			return "ty_str_of_long(" + e.expr(x) + ")"
		}
	}
	return "ty_str_of_obj((tyobj*)" + e.expr(x) + ")"
}

func (e *Emitter) assign(v *ast.Assign) string {
	lv := e.lvalue(v.X)
	if v.Op == "=" {
		return "(" + lv + " = " + e.coerce(e.expr(v.Y), v.Y.GetType(), v.X.GetType()) + ")"
	}
	op := v.Op[:len(v.Op)-1]
	if op == "/" || op == "%" {
		xt := v.X.GetType()
		fn := "ty_div_int"
		if op == "%" {
			fn = "ty_rem_int"
		}
		if ast.IsPrim(xt, ast.Long) {
			if op == "%" {
				fn = "ty_rem_long"
			} else {
				fn = "ty_div_long"
			}
		}
		return "(" + lv + " = (" + e.ctype(xt) + ")" + fn + "(" + lv + ", " + e.expr(v.Y) + "))"
	}
	return "(" + lv + " " + op + "= " + e.operand(v.Y, v.OpType) + ")"
}

// ---------------------------------------------------------------- calls

func (e *Emitter) callExpr(v *ast.Call) string {
	m := v.Method
	if m == nil {
		return "0"
	}
	recv := ""
	if !m.IsStatic() {
		switch {
		case v.Recv == nil:
			recv = "this"
		case v.Super:
			recv = "((void*)this)"
		default:
			recv = e.expr(v.Recv)
		}
	}
	if _, ok := nativeTable[nativeKey(m)]; ok {
		return e.nativeCall(m, recv, v.Args)
	}
	name := e.cfunc(m)
	a := e.args(recv, v.Args, m)
	if m.External {
		return m.Native + "(" + a + ")"
	}
	if v.Static || m.IsStatic() {
		return name + "(" + a + ")"
	}
	if v.Recv == nil {
		if m.VIndex >= 0 {
			return e.virtCall(m, cname(m.Owner)+"*", "this", v.Args)
		}
		return name + "(" + a + ")"
	}
	if m.VIndex >= 0 && !m.Mods.Has(ast.ModPrivate) {
		return e.virtCall(m, cname(m.Owner)+"*", recv, v.Args)
	}
	return name + "(" + a + ")"
}

// virtCall dispatches through the vtable or, for interface receivers, the itable.
func (e *Emitter) virtCall(m *ast.Method, recvT, recv string, args []ast.Expr) string {
	if recvT == "" {
		recvT = "void*"
	}
	if m.Selector >= 0 {
		fn := "((void*)ty_itab((tyobj*)" + recv + ", " + fmt.Sprint(m.Selector) + "))"
		return e.indirect(m, fn, "void*", recv, args)
	}
	fn := "((" + recv + ")->obj.cls->vtable[" + fmt.Sprint(m.VIndex) + "])"
	return e.indirect(m, fn, recvT, recv, args)
}

// indirect builds a call through a runtime-resolved function pointer.
func (e *Emitter) indirect(m *ast.Method, fn, recvT, recv string, args []ast.Expr) string {
	ret := e.ctype(m.Result)
	if ret == "void" {
		ret = "void"
	}
	var ps []string
	if !m.IsStatic() {
		ps = append(ps, recvT)
	}
	for _, p := range m.Params {
		ps = append(ps, e.ctype(p))
	}
	if len(ps) == 0 {
		ps = append(ps, "void")
	}
	a := e.args(recv, args, m)
	sig := "(( " + ret + "(*)(" + strings.Join(ps, ", ") + "))" + fn + ")"
	if ret == "void" {
		if a == "" {
			return sig + "()"
		}
		return sig + "(" + a + ")"
	}
	return sig + "(" + a + ")"
}

// ---------------------------------------------------------------- new

func (e *Emitter) newExpr(v *ast.New) string {
	ct, ok := v.GetType().(*ast.ClassType)
	if !ok {
		if ct2, ok2 := e.prog.Erased(v.GetType()).(*ast.ClassType); ok2 {
			ct, ok = ct2, true
		} else {
			return "NULL"
		}
	}
	cl := ct.Class
	if fn, ok := specialNew[cl.Name]; ok && len(v.Args) == 0 {
		p := cname(cl) + "*"
		return "({ " + p + " _o = (" + p + ")" + fn + "(); _o->obj.cls = &cls_" + mangle(cl.Full) + "; _o; })"
	}
	n := e.tmpName()
	var b strings.Builder
	fmt.Fprintf(&b, "({ %s %s = (%s)ty_alloc(sizeof(%s)); %s->obj.cls = &cls_%s;",
		cname(cl)+"*", n, cname(cl)+"*", cname(cl), n, mangle(cl.Full))
	if cl.Inner && cl.OuterField != nil {
		fmt.Fprintf(&b, " %s->f_%s = (%s)%s;", n, mangle(cl.OuterField.Name), cname(cl.Outer), e.outerArg(v))
	}
	fmt.Fprintf(&b, " ty_clinit(&cls_%s);", mangle(cl.Full))
	if v.Ctor != nil {
		fmt.Fprintf(&b, " %s(%s);", e.cfunc(v.Ctor), e.args(n, v.Args, v.Ctor))
	}
	fmt.Fprintf(&b, " %s; })", n)
	return b.String()
}

func (e *Emitter) outerArg(v *ast.New) string {
	if v.Outer != nil {
		return e.expr(v.Outer)
	}
	return "this"
}

func (e *Emitter) newArray(v *ast.NewArray) string {
	elem := v.Elem.Resolved
	es := e.elemSize(elem)
	refs := "0"
	if e.isRefElem(elem) {
		refs = "1"
	}
	if v.Init != nil {
		return e.arrayInitOf(v.Init, elem)
	}
	if len(v.Dims) == 0 {
		return "({ tyarr* _a = ty_array_new(0, " + es + "); _a->refs = " + refs + "; _a; })"
	}
	dim := e.expr(v.Dims[0])
	return "({ tyarr* _a = ty_array_new(" + dim + ", " + es + "); _a->refs = " + refs + "; _a; })"
}

func (e *Emitter) arrayInit(v *ast.ArrayInit) string {
	return e.arrayInitOf(v, v.Elem)
}

func (e *Emitter) arrayInitOf(v *ast.ArrayInit, elem ast.Type) string {
	es := e.elemSize(elem)
	refs := "0"
	if e.isRefElem(elem) {
		refs = "1"
	}
	var b strings.Builder
	n := e.tmpName()
	fmt.Fprintf(&b, "({ tyarr* %s = ty_array_new(%d, %s); %s->refs = %s;", n, len(v.Elems), es, n, refs)
	for i, el := range v.Elems {
		val := e.arrayElemValue(el, elem)
		if e.isRefElem(elem) {
			fmt.Fprintf(&b, " ((void*)%s->data)[%d] = (void*)%s;", n, i, val)
		} else {
			fmt.Fprintf(&b, " ((%s*)%s->data)[%d] = %s;", e.ctype(elem), n, i, val)
		}
	}
	fmt.Fprintf(&b, " %s; })", n)
	return b.String()
}

func (e *Emitter) arrayElemValue(el ast.Expr, elem ast.Type) string {
	if ai, ok := el.(*ast.ArrayInit); ok {
		return e.arrayInitOf(ai, elem)
	}
	return e.coerce(e.expr(el), el.GetType(), elem)
}

// ---------------------------------------------------------------- lambdas

func (e *Emitter) lambdaExpr(lam *ast.Lambda) string {
	cl := lam.Class
	if cl == nil {
		return "NULL"
	}
	n := e.tmpName()
	var b strings.Builder
	fmt.Fprintf(&b, "({ %s %s = (%s)ty_alloc(sizeof(%s)); %s->obj.cls = &cls_%s;",
		cname(cl)+"*", n, cname(cl)+"*", cname(cl), n, mangle(cl.Full))
	for _, v := range sortedCaps(cl) {
		fmt.Fprintf(&b, " %s->cap_%s = %s;", n, mangle(v.Name), e.localName(v))
	}
	if lam.CapThis {
		fmt.Fprintf(&b, " %s->cap_this = (%s*)this;", n, cname(cl))
	}
	fmt.Fprintf(&b, " %s; })", n)
	return b.String()
}

func (e *Emitter) emitLambdaMethod(cl *ast.Class, m *ast.Method) {
	lam := m.Lambda
	if lam == nil || m.IsCtor {
		return
	}
	fmt.Fprintf(&e.fns, "static %s;\n", e.signature(m))
	e.indent = 0
	fmt.Fprintf(&e.code, "static %s {\n", e.signature(m))
	e.indent++
	switch b := lam.Body.(type) {
	case ast.Expr:
		e.line("return %s;\n", e.coerce(e.expr(b), b.GetType(), m.Result))
	case *ast.Block:
		e.emitBlockInner(b)
	}
	e.indent--
	e.code.WriteString("}\n\n")
}
