package sema

import (
	"fmt"
	"sort"
	"strings"

	"github.com/LangYa466/Teyru/internal/ast"
	"github.com/LangYa466/Teyru/internal/util"
)

// This file implements the Lombok compatibility layer. Annotation-driven
// members are generated as ordinary syntax trees and then type-checked like
// user code, so there is no separate lowering path that could drift from the
// language semantics.
//
// Reference: https://projectlombok.org/features/ (all annotations are listed in
// docs/lombok.md, including the ones that cannot work without a runtime the
// Teyru standard library does not ship).

// lombokMods are the access levels Lombok's AccessLevel maps onto.
func annoAccess(a *ast.Annotation) (ast.Mods, bool) {
	if a == nil {
		return 0, false
	}
	v := a.Value()
	if v == nil {
		return 0, false
	}
	name := ""
	switch x := v.Value.(type) {
	case *ast.Select:
		name = x.Name
	case *ast.Ident:
		name = x.Name
	}
	switch name {
	case "PRIVATE":
		return ast.ModPrivate, true
	case "PROTECTED":
		return ast.ModProtected, true
	case "PUBLIC":
		return ast.ModPublic, true
	case "PACKAGE":
		return 0, true
	case "NONE":
		return 0, false
	}
	return 0, false
}

// annoAccessArg reads an AccessLevel value from a named argument.
func annoAccessArg(arg *ast.AnnoArg) (ast.Mods, bool) {
	if arg == nil {
		return 0, false
	}
	name := ""
	switch x := arg.Value.(type) {
	case *ast.Select:
		name = x.Name
	case *ast.Ident:
		name = x.Name
	}
	switch name {
	case "PRIVATE":
		return ast.ModPrivate, true
	case "PROTECTED":
		return ast.ModProtected, true
	case "PUBLIC":
		return ast.ModPublic, true
	case "PACKAGE":
		return 0, true
	}
	return 0, false
}

// annoBool reads a boolean argument.
func annoBool(a *ast.Annotation, name string, def bool) bool {
	if a == nil {
		return def
	}
	arg := a.Arg(name)
	if arg == nil {
		return def
	}
	if lit, ok := arg.Value.(*ast.Literal); ok && lit.Kind == ast.LitBool {
		return lit.Bool
	}
	return def
}

// annoString reads a string argument.
func annoString(a *ast.Annotation, name string) string {
	if a == nil {
		return ""
	}
	arg := a.Arg(name)
	if arg == nil {
		return ""
	}
	if lit, ok := arg.Value.(*ast.Literal); ok && lit.Kind == ast.LitString {
		return lit.Str
	}
	return ""
}

// annoStringList reads a `String[]` argument such as `of = {"a", "b"}`.
func annoStringList(a *ast.Annotation, name string) []string {
	if a == nil {
		return nil
	}
	arg := a.Arg(name)
	if arg == nil {
		return nil
	}
	var out []string
	switch v := arg.Value.(type) {
	case *ast.ArrayInit:
		for _, e := range v.Elems {
			if lit, ok := e.(*ast.Literal); ok && lit.Kind == ast.LitString {
				out = append(out, lit.Str)
			}
		}
	case *ast.Literal:
		if v.Kind == ast.LitString {
			out = append(out, v.Str)
		}
	}
	return out
}

// annoClasses reads a `Class[]` argument such as `@ExtensionMethod({A.class})`.
func annoClasses(a *ast.Annotation) []*ast.TypeExpr {
	if a == nil {
		return nil
	}
	var out []*ast.TypeExpr
	add := func(e ast.Expr) {
		if cl, ok := e.(*ast.ClassLit); ok {
			out = append(out, cl.Type)
		}
	}
	if v := a.Value(); v != nil {
		switch x := v.Value.(type) {
		case *ast.ArrayInit:
			for _, e := range x.Elems {
				add(e)
			}
		case *ast.ClassLit:
			out = append(out, x.Type)
		}
	}
	if arg := a.Arg("value"); arg != nil {
		switch x := arg.Value.(type) {
		case *ast.ArrayInit:
			for _, e := range x.Elems {
				add(e)
			}
		case *ast.ClassLit:
			out = append(out, x.Type)
		}
	}
	return out
}

// hasAnno reports whether any annotation in the list matches.
func hasAnno(annos []*ast.Annotation, names ...string) *ast.Annotation {
	for _, a := range annos {
		if a.Is(names...) {
			return a
		}
	}
	return nil
}

// lombokTargets are the fields an annotation applies to: the annotated field,
// or every instance field of the annotated class.
type lombokTargets struct {
	fields  []*ast.Field
	forAll  bool
	anno    *ast.Annotation
	ownerCl *ast.Class
}

// accessorsOptions holds @Accessors settings, which also drive @Getter/@Setter.
type accessorsOptions struct {
	chain  bool
	fluent bool
	prefix []string
}

func (c *Checker) accessorsOf(annos []*ast.Annotation) accessorsOptions {
	o := accessorsOptions{}
	if a := hasAnno(annos, "Accessors"); a != nil {
		o.chain = annoBool(a, "chain", false)
		o.fluent = annoBool(a, "fluent", false)
		o.prefix = annoStringList(a, "prefix")
	}
	return o
}

// stripPrefix removes the configured prefixes from a field name.
func (o accessorsOptions) stripPrefix(name string) string {
	for _, p := range o.prefix {
		if p != "" && strings.HasPrefix(name, p) && len(name) > len(p) {
			return strings.ToUpper(name[len(p):len(p)+1]) + name[len(p)+1:]
		}
	}
	return name
}

// applyLombok runs the annotation pass for one class. It is called at the end
// of resolveMembers, before vtable layout, so generated members take part in
// overriding and virtual dispatch like declared ones.
func (c *Checker) applyLombok(cl *ast.Class) {
	cd := cl.Decl
	if cd == nil {
		return
	}
	classAnnos := cd.Annos
	accessors := c.accessorsOf(classAnnos)

	// ---- class-level structural annotations
	if a := hasAnno(classAnnos, "UtilityClass"); a != nil {
		c.lombokUtilityClass(cl)
	}
	if a := hasAnno(classAnnos, "Value"); a != nil {
		c.lombokValue(cl, accessors, a)
	}
	if a := hasAnno(classAnnos, "FieldDefaults"); a != nil {
		c.lombokFieldDefaults(cl, a)
	}
	if hasAnno(classAnnos, "Data") != nil {
		c.lombokData(cl, accessors)
	}
	if hasAnno(classAnnos, "SuperBuilder") != nil {
		// handled together with @Builder below
	}

	// ---- member annotations
	c.lombokMembers(cl, accessors, classAnnos)

	// ---- class-level generators
	if a := hasAnno(classAnnos, "Getter"); a != nil {
		c.lombokGetter(cl, c.instanceAndStaticFields(cl), a, accessors)
	}
	if a := hasAnno(classAnnos, "Setter"); a != nil {
		c.lombokSetter(cl, c.instanceAndStaticFields(cl), a, accessors)
	}
	if a := hasAnno(classAnnos, "ToString"); a != nil {
		c.lombokToString(cl, a)
	}
	if a := hasAnno(classAnnos, "EqualsAndHashCode"); a != nil {
		c.lombokEqualsHashCode(cl, a)
	}
	if a := hasAnno(classAnnos, "RequiredArgsConstructor"); a != nil {
		c.lombokCtor(cl, a, "required")
	}
	if a := hasAnno(classAnnos, "AllArgsConstructor"); a != nil {
		c.lombokCtor(cl, a, "all")
	}
	if a := hasAnno(classAnnos, "NoArgsConstructor"); a != nil {
		c.lombokCtor(cl, a, "none")
	}
	if a := hasAnno(classAnnos, "Builder", "SuperBuilder"); a != nil {
		if a.Is("SuperBuilder") && cl.Super != nil && len(c.instanceAndStaticFields(cl.Super.Class)) > 0 {
			c.errf(cl.Decl.Pos, "TY-INT-0003",
				"@SuperBuilder cannot inherit builder fields from %s: declare them in a builder of the subclass or use a fieldless base class",
				cl.Super.Class.Name)
		}
		c.lombokBuilder(cl, a, classAnnos)
	}
	if a := hasAnno(classAnnos, "Singular"); a != nil {
		c.errf(cl.Decl.Pos, "TY-INT-0004", "@Singular requires a collection type; the Teyru standard library provides none")
	}
	if a := hasAnno(classAnnos, "StandardException"); a != nil && cl.Kind == ast.KindClass {
		c.lombokStandardException(cl)
	}
	if a := hasAnno(classAnnos, "FieldNameConstants"); a != nil {
		c.lombokFieldNameConstants(cl, a)
	}
	if a := hasAnno(classAnnos, "Helper"); a != nil {
		cl.Mods |= ast.ModStatic
	}
	if a := hasAnno(classAnnos, "Log", "Slf4j", "Log4j", "Log4j2", "CommonsLog", "JBossLog", "Flogger", "XSlf4j"); a != nil {
		c.lombokLog(cl, a)
	}
	c.lombokDelegates(cl)
	if hasAnno(classAnnos, "Jacksonized") != nil {
		// No Jackson serialization exists in Teyru; the annotation is accepted
		// and has no effect (documented in docs/lombok.md).
	}
	if hasAnno(classAnnos, "Var") != nil {
		// deprecated Lombok alias for `var`; nothing to generate
	}
	if hasAnno(classAnnos, "NonFinal") != nil {
		cl.Mods &^= ast.ModFinal
	}
	if hasAnno(classAnnos, "PackagePrivate") != nil {
		for _, f := range cl.Fields {
			f.Mods &^= ast.ModPublic | ast.ModPrivate | ast.ModProtected
		}
		for _, ms := range cl.Methods {
			for _, m := range ms {
				m.Mods &^= ast.ModPublic | ast.ModPrivate | ast.ModProtected
			}
		}
	}
}

// instanceAndStaticFields returns the non-synthetic fields of a class.
func (c *Checker) instanceAndStaticFields(cl *ast.Class) []*ast.Field {
	var out []*ast.Field
	for _, f := range cl.Fields {
		if f.IsProp || f.Anno != "" {
			continue
		}
		out = append(out, f)
	}
	return out
}

// lombokDelegates generates delegating methods for @Delegate fields.
func (c *Checker) lombokDelegates(cl *ast.Class) {
	for _, f := range cl.Fields {
		ann := delegateAnnoOf(cl, f.Name)
		if ann == nil {
			continue
		}
		ct, ok := f.Type.(*ast.ClassType)
		if !ok {
			continue
		}
		target := ct.Class
		methods := map[string]*ast.Method{}
		collect := func(k *ast.Class) {
			for _, ms := range k.Methods {
				for _, m := range ms {
					if m.IsStatic() || m.Mods.Has(ast.ModPrivate) || m.IsCtor {
						continue
					}
					if _, dup := methods[m.Name]; dup {
						continue
					}
					methods[m.Name] = m
				}
			}
		}
		for k := target; k != nil; k = k.Super.Class {
			if k.Super == nil {
				collect(k)
				break
			}
			collect(k)
		}
		c.eachInterface(target, func(i *ast.Class) bool {
			collect(i)
			return true
		})
		names := make([]string, 0, len(methods))
		for n := range methods {
			names = append(names, n)
		}
		sort.Strings(names)
		for _, n := range names {
			m := methods[n]
			if hasMethodDecl(cl.Decl, m.Name, len(m.Params)) {
				continue
			}
			var params []ast.Type
			var names2 []string
			var args []ast.Expr
			for i, pt := range m.Params {
				pn := fmt.Sprintf("arg%d", i)
				if i < len(m.ParamNames) {
					pn = m.ParamNames[i]
				}
				params = append(params, pt)
				names2 = append(names2, pn)
				args = append(args, id(pn))
			}
			var body *ast.Block
			if m.Result == ast.TVoid {
				body = blockOf(exprStmtOf(callNew(sel(&ast.This{ExprBase: ast.ExprBase{Pos: pos()}}, f.Name), m.Name, args...)))
			} else {
				body = blockOf(returnOf(callNew(sel(&ast.This{ExprBase: ast.ExprBase{Pos: pos()}}, f.Name), m.Name, args...)))
			}
			dm := c.newSynthMethod(cl, m.Name, ast.ModPublic, m.Result, params, names2, body, "@Delegate")
			c.addSynthMethod(cl, dm)
		}
	}
}

// delegateAnnoOf finds @Delegate on a field declaration.
func delegateAnnoOf(cl *ast.Class, field string) *ast.Annotation {
	if cl.Decl == nil {
		return nil
	}
	for _, mem := range cl.Decl.Members {
		fd, ok := mem.(*ast.FieldDecl)
		if !ok {
			continue
		}
		for _, vd := range fd.Vars {
			if vd.Name == field {
				return hasAnno(fd.Annos, "Delegate")
			}
		}
	}
	return nil
}

// lombokMembers handles annotations placed on individual members.
func (c *Checker) lombokMembers(cl *ast.Class, accessors accessorsOptions, classAnnos []*ast.Annotation) {
	cd := cl.Decl
	for _, mem := range cd.Members {
		switch d := mem.(type) {
		case *ast.FieldDecl:
			for _, vd := range d.Vars {
				f := vd.Fld
				if f == nil {
					continue
				}
				if a := hasAnno(d.Annos, "Getter"); a != nil {
					c.lombokGetter(cl, []*ast.Field{f}, a, accessors)
				}
				if a := hasAnno(d.Annos, "Setter"); a != nil {
					c.lombokSetter(cl, []*ast.Field{f}, a, accessors)
				}
				if hasAnno(d.Annos, "NonNull") != nil {
					f.NonNull = true
				}
				if a := hasAnno(d.Annos, "With"); a != nil {
					c.lombokWith(cl, f, a)
				}
				if a := hasAnno(d.Annos, "FieldNameConstants"); a != nil {
					// field-level variant adds one constant
					c.lombokFieldNameConstants(cl, a)
				}
			}
		case *ast.MethodDecl:
			if d.Sym == nil {
				continue
			}
			if hasAnno(d.Annos, "SneakyThrows") != nil {
				c.lombokSneakyThrows(d)
			}
			if hasAnno(d.Annos, "Synchronized") != nil {
				c.lombokSynchronized(cl, d)
			}
			if a := hasAnno(d.Annos, "Locked"); a != nil {
				c.lombokLocked(cl, d, a)
			}
			if hasAnno(d.Annos, "NonNull") != nil {
				c.lombokNonNullParams(d)
			}
			if a := hasAnno(d.Annos, "Builder"); a != nil {
				c.lombokMethodBuilder(cl, d, a)
			}
			if hasAnno(d.Annos, "Tolerate") != nil {
				d.Sym.Tolerate = true
			}
		}
	}
	// @NonNull on parameters
	for _, mem := range cd.Members {
		md, ok := mem.(*ast.MethodDecl)
		if !ok {
			continue
		}
		if hasAnno(md.Annos, "NonNull") != nil {
			c.lombokNonNullParams(md)
		}
	}
}

// ---------------------------------------------------------------- getters

func (c *Checker) lombokGetter(cl *ast.Class, fields []*ast.Field, a *ast.Annotation, o accessorsOptions) {
	if _, none := annoAccess(a); !none && a.Value() != nil && len(a.Value().Value.(*ast.Ident).Name) == 0 {
		return
	}
	mods, ok := annoAccess(a)
	if !ok && a.Value() != nil {
		// AccessLevel.NONE
		if _, isNone := accessLevelName(a); isNone {
			return
		}
	}
	if mods == 0 {
		mods = ast.ModPublic
	}
	lazy := annoBool(a, "lazy", false)
	for _, f := range fields {
		if f.Mods.Has(ast.ModStatic) {
			mods |= ast.ModStatic
		}
		name := getterName(f, o)
		if lazy {
			c.lombokLazyGetter(cl, f, mods, name)
			continue
		}
		m := c.newSynthMethod(cl, name, mods, f.Type, nil, nil,
			blockOf(returnOf(thisField(f))), "@Getter")
		m.Anno = "@Getter"
		m.Prop = f
		c.addSynthMethod(cl, m)
	}
}

// getterName applies the JavaBeans rule and @Accessors settings.
func getterName(f *ast.Field, o accessorsOptions) string {
	base := o.stripPrefix(f.Name)
	if o.fluent {
		return base
	}
	prefix := "get"
	if ast.IsPrim(f.Type, ast.Boolean) {
		prefix = "is"
	}
	return prefix + util.Capitalize(base)
}

func setterName(f *ast.Field, o accessorsOptions) string {
	base := o.stripPrefix(f.Name)
	if o.fluent {
		return base
	}
	return "set" + util.Capitalize(base)
}

// accessLevelName reports AccessLevel.NONE.
func accessLevelName(a *ast.Annotation) (string, bool) {
	v := a.Value()
	if v == nil {
		return "", false
	}
	switch x := v.Value.(type) {
	case *ast.Select:
		return x.Name, x.Name == "NONE"
	case *ast.Ident:
		return x.Name, x.Name == "NONE"
	}
	return "", false
}

func (c *Checker) lombokSetter(cl *ast.Class, fields []*ast.Field, a *ast.Annotation, o accessorsOptions) {
	if _, isNone := accessLevelName(a); isNone {
		return
	}
	mods, ok := annoAccess(a)
	if !ok {
		mods = ast.ModPublic
	}
	for _, f := range fields {
		if f.Mods.Has(ast.ModFinal) {
			continue
		}
		mmods := mods
		if f.Mods.Has(ast.ModStatic) {
			mmods |= ast.ModStatic
		}
		stmts := []ast.Stmt{}
		if f.NonNull {
			msg := f.Name + " is marked non-null but is null"
			stmts = append(stmts, ifOf(isNull(id("value")), throwOf(newObj(c.b.NPE, strLit(msg))), nil))
		}
		stmts = append(stmts, exprStmtOf(assignTo(thisField(f), id("value"))))
		var result ast.Type = ast.TVoid
		if o.chain {
			result = &ast.ClassType{Class: cl, Args: typeVarArgs(cl)}
			stmts = append(stmts, returnOf(thisStat(cl)))
		}
		m := c.newSynthMethod(cl, setterName(f, o), mmods, result, []ast.Type{f.Type}, []string{"value"},
			blockOf(stmts...), "")
		m.Anno = "@Setter"
		c.addSynthMethod(cl, m)
	}
}

// lombokLazyGetter implements @Getter(lazy = true): the value is computed once
// and cached in a synthesized holder field.
func (c *Checker) lombokLazyGetter(cl *ast.Class, f *ast.Field, mods ast.Mods, name string) {
	holder := &ast.Field{
		Name: "__lazy$" + f.Name, Type: f.Type,
		Mods: ast.ModPrivate | ast.ModVolatile, Pos: f.Pos, Storage: true,
		Anno: "@Getter(lazy)",
	}
	c.addSynthField(cl, holder)
	var init ast.Expr = nullLit()
	if f.Decl != nil && f.Decl.Init != nil {
		init = f.Decl.Init
	}
	body := blockOf(
		ifOf(isNull(thisField(holder)),
			blockOf(
				exprStmtOf(assignTo(thisField(holder), init)),
			), nil),
		returnOf(thisField(holder)),
	)
	m := c.newSynthMethod(cl, name, mods, f.Type, nil, nil, body, "")
	m.Anno = "@Getter(lazy)"
	c.addSynthMethod(cl, m)
}

// ---------------------------------------------------------------- tostring

func (c *Checker) lombokToString(cl *ast.Class, a *ast.Annotation) {
	fields := c.toStringFields(cl, a)
	if hasMethodDecl(cl.Decl, "toString", 0) {
		return
	}
	includeNames := annoBool(a, "includeFieldNames", true)
	parts := []ast.Expr{strLit(cl.Name + "(")}
	first := true
	for _, f := range fields {
		label := ""
		if includeNames {
			label = f.Name + "="
		}
		if !first {
			parts = append(parts, strLit(", "+label))
		} else if label != "" {
			parts = append(parts, strLit(label))
		}
		first = false
		parts = append(parts, thisField(f))
	}
	if annoBool(a, "callSuper", false) && cl.Super != nil {
		parts = append(parts, strLit("; super="), superCall("toString"))
	}
	parts = append(parts, strLit(")"))
	m := c.newSynthMethod(cl, "toString", ast.ModPublic, c.strType, nil, nil,
		blockOf(returnOf(concatStr(parts...))), "")
	m.Anno = "@ToString"
	c.addSynthMethod(cl, m)
}

// toStringFields applies of/exclude/onlyExplicitlyIncluded.
func (c *Checker) toStringFields(cl *ast.Class, a *ast.Annotation) []*ast.Field {
	of := annoStringList(a, "of")
	exclude := map[string]bool{}
	for _, n := range annoStringList(a, "exclude") {
		exclude[n] = true
	}
	only := annoBool(a, "onlyExplicitlyIncluded", false)
	ofSet := map[string]bool{}
	for _, n := range of {
		ofSet[n] = true
	}
	var out []*ast.Field
	for _, f := range c.instanceAndStaticFields(cl) {
		if f.Mods.Has(ast.ModStatic) {
			continue
		}
		if len(of) > 0 {
			if ofSet[f.Name] {
				out = append(out, f)
			}
			continue
		}
		if exclude[f.Name] {
			continue
		}
		if only && !f.Include {
			continue
		}
		out = append(out, f)
	}
	return out
}

// ---------------------------------------------------------------- equals/hashCode

func (c *Checker) lombokEqualsHashCode(cl *ast.Class, a *ast.Annotation) {
	of := annoStringList(a, "of")
	exclude := map[string]bool{}
	for _, n := range annoStringList(a, "exclude") {
		exclude[n] = true
	}
	ofSet := map[string]bool{}
	for _, n := range of {
		ofSet[n] = true
	}
	callSuper := annoBool(a, "callSuper", false)
	var fields []*ast.Field
	for _, f := range c.instanceAndStaticFields(cl) {
		if f.Mods.Has(ast.ModStatic) {
			continue
		}
		if len(of) > 0 {
			if ofSet[f.Name] {
				fields = append(fields, f)
			}
			continue
		}
		if !exclude[f.Name] {
			fields = append(fields, f)
		}
	}
	if !hasMethodDecl(cl.Decl, "equals", 1) {
		c.lombokEquals(cl, fields, callSuper)
	}
	if !hasMethodDecl(cl.Decl, "hashCode", 0) {
		c.lombokHashCode(cl, fields, callSuper)
	}
}

func (c *Checker) lombokEquals(cl *ast.Class, fields []*ast.Field, callSuper bool) {
	self := &ast.ClassType{Class: cl, Args: typeVarArgs(cl)}
	objType := &ast.ClassType{Class: c.b.Object}
	stmts := []ast.Stmt{
		ifOf(eq(thisStat(cl), id("o")), blockOf(returnOf(boolLit(true))), nil),
		ifOf(binop("||", isNull(id("o")),
			&ast.Unary{ExprBase: ast.ExprBase{Pos: pos()}, Op: "!",
				X: &ast.InstanceOf{ExprBase: ast.ExprBase{Pos: pos()}, X: id("o"),
					Type: &ast.TypeExpr{Pos: pos(), Name: cl.Name, Resolved: self}}}),
			blockOf(returnOf(boolLit(false))), nil),
	}
	if callSuper && cl.Super != nil {
		stmts = append(stmts, ifOf(
			&ast.Unary{ExprBase: ast.ExprBase{Pos: pos()}, Op: "!",
				X: superCall("equals", id("o"))},
			blockOf(returnOf(boolLit(false))), nil))
	}
	if len(fields) > 0 {
		other := &ast.VarDeclarator{Pos: pos(), Name: "other",
			Init: &ast.Cast{ExprBase: ast.ExprBase{Pos: pos()}, Type: &ast.TypeExpr{Pos: pos(), Name: cl.Name, Resolved: self}, X: id("o")}}
		stmts = append(stmts, &ast.LocalVar{Pos: pos(), Type: &ast.TypeExpr{Pos: pos(), Name: cl.Name, Resolved: self}, Vars: []*ast.VarDeclarator{other}})
	}
	for _, f := range fields {
		other := sel(id("other"), f.Name)
		mine := thisField(f)
		var differ ast.Expr
		switch {
		case ast.IsPrim(f.Type, ast.Double):
			differ = binop("!=", callNew(id("Double"), "compare", mine, other), intLit(0))
		case ast.IsPrim(f.Type, ast.Float):
			differ = binop("!=", callNew(id("Float"), "compare", mine, other), intLit(0))
		case util.IsPrim(f.Type):
			differ = binop("!=", mine, other)
		default:
			// reference: null-safe equality
			differ = binop("||",
				binop("&&", isNull(mine), binop("!=", other, nullLit())),
				binop("&&", binop("!=", mine, nullLit()),
					&ast.Unary{ExprBase: ast.ExprBase{Pos: pos()}, Op: "!",
						X: callNew(mine, "equals", other)}))
		}
		stmts = append(stmts, ifOf(differ, blockOf(returnOf(boolLit(false))), nil))
	}
	stmts = append(stmts, returnOf(boolLit(true)))
	m := c.newSynthMethod(cl, "equals", ast.ModPublic, ast.TBoolean,
		[]ast.Type{objType}, []string{"o"}, blockOf(stmts...), "")
	m.Anno = "@EqualsAndHashCode"
	c.addSynthMethod(cl, m)
}

func (c *Checker) lombokHashCode(cl *ast.Class, fields []*ast.Field, callSuper bool) {
	start := ast.Expr(intLit(1))
	if callSuper && cl.Super != nil {
		start = superCall("hashCode")
	}
	stmts := []ast.Stmt{
		&ast.LocalVar{Pos: pos(), Type: &ast.TypeExpr{Pos: pos(), Name: "int", Resolved: ast.TInt},
			Vars: []*ast.VarDeclarator{{Pos: pos(), Name: "result", Init: start}}},
	}
	for _, f := range fields {
		var h ast.Expr
		switch {
		case ast.IsPrim(f.Type, ast.Boolean):
			h = &ast.Cond{ExprBase: ast.ExprBase{Pos: pos()}, C: thisField(f), X: intLit(1231), Y: intLit(1237)}
		case ast.IsPrim(f.Type, ast.Long):
			h = &ast.Cast{ExprBase: ast.ExprBase{Pos: pos()}, Type: &ast.TypeExpr{Pos: pos(), Name: "int", Resolved: ast.TInt},
				X: binop("^", thisField(f), binop(">>>", thisField(f), intLit(32)))}
		case ast.IsPrim(f.Type, ast.Double):
			h = callNew(id("Double"), "hashCode", thisField(f))
		case ast.IsPrim(f.Type, ast.Float):
			h = callNew(id("Float"), "hashCode", thisField(f))
		case util.IsPrim(f.Type):
			h = thisField(f)
		default:
			h = &ast.Cond{ExprBase: ast.ExprBase{Pos: pos()}, C: isNull(thisField(f)), X: intLit(0), Y: callNew(thisField(f), "hashCode")}
		}
		stmts = append(stmts, exprStmtOf(assignTo(id("result"),
			binop("+", binop("*", intLit(31), id("result")), h))))
	}
	stmts = append(stmts, returnOf(id("result")))
	m := c.newSynthMethod(cl, "hashCode", ast.ModPublic, ast.TInt, nil, nil, blockOf(stmts...), "")
	m.Anno = "@EqualsAndHashCode"
	c.addSynthMethod(cl, m)
}

// ---------------------------------------------------------------- constructors

// ctorFields selects the fields a generated constructor initializes.
func (c *Checker) ctorFields(cl *ast.Class, kind string) []*ast.Field {
	var out []*ast.Field
	for _, f := range c.instanceAndStaticFields(cl) {
		if f.Mods.Has(ast.ModStatic) {
			continue
		}
		switch kind {
		case "all":
			if f.Decl != nil && f.Decl.Init != nil && f.Mods.Has(ast.ModFinal) {
				continue
			}
			out = append(out, f)
		case "required":
			hasInit := f.Decl != nil && f.Decl.Init != nil
			if (f.Mods.Has(ast.ModFinal) && !hasInit) || f.NonNull {
				out = append(out, f)
			}
		}
	}
	return out
}

func (c *Checker) lombokCtor(cl *ast.Class, a *ast.Annotation, kind string) {
	fields := c.ctorFields(cl, kind)
	var params []ast.Type
	var names []string
	stmts := []ast.Stmt{}
	for _, f := range fields {
		params = append(params, f.Type)
		names = append(names, f.Name)
		if f.NonNull {
			msg := f.Name + " is marked non-null but is null"
			stmts = append(stmts, ifOf(isNull(id(f.Name)), throwOf(newObj(c.b.NPE, strLit(msg))), nil))
		}
		stmts = append(stmts, exprStmtOf(assignTo(thisField(f), id(f.Name))))
	}
	mods, ok := annoAccess(a)
	if !ok {
		mods = ast.ModPublic
	}
	if s := annoString(a, "staticName"); s != "" {
		c.lombokStaticFactory(cl, s, params, names, stmts, mods)
		return
	}
	m := &ast.Method{Name: "<init>", Owner: cl, IsCtor: true, Mods: mods,
		Result: ast.TVoid, Params: params, ParamNames: names, Body: blockOf(stmts...), Pos: pos()}
	m.Anno = "@" + kind + "ArgsConstructor"
	c.addSynthCtor(cl, m)
}

// lombokStaticFactory emits `static Cls of(args) { return new Cls(args) }`.
func (c *Checker) lombokStaticFactory(cl *ast.Class, name string, params []ast.Type, names []string, ctorStmts []ast.Stmt, mods ast.Mods) {
	inner := &ast.Method{Name: "<init>", Owner: cl, IsCtor: true, Mods: ast.ModPrivate,
		Result: ast.TVoid, Params: params, ParamNames: names, Body: blockOf(ctorStmts...), Pos: pos()}
	c.addSynthCtor(cl, inner)
	args := make([]ast.Expr, len(names))
	for i, n := range names {
		args[i] = id(n)
	}
	m := c.newSynthMethod(cl, name, (mods&^(ast.ModProtected))|ast.ModStatic, &ast.ClassType{Class: cl, Args: typeVarArgs(cl)},
		params, names, blockOf(returnOf(newObj(cl, args...))), "")
	m.Anno = "staticConstructor"
	c.addSynthMethod(cl, m)
}

func (c *Checker) lombokData(cl *ast.Class, o accessorsOptions) {
	cl.Mods &^= ast.ModFinal
	if !hasMethodDecl(cl.Decl, "toString", 0) {
		c.lombokToStringNoAnno(cl)
	}
	c.lombokEqualsHashCodeNoAnno(cl)
	fields := c.instanceAndStaticFields(cl)
	for _, f := range fields {
		if f.Mods.Has(ast.ModStatic) {
			continue
		}
		f.Mods |= ast.ModPrivate
		c.lombokGetter(cl, []*ast.Field{f}, &ast.Annotation{Name: "Getter"}, o)
		if !f.Mods.Has(ast.ModFinal) {
			c.lombokSetter(cl, []*ast.Field{f}, &ast.Annotation{Name: "Setter"}, o)
		}
	}
	c.lombokCtor(cl, &ast.Annotation{Name: "RequiredArgsConstructor"}, "required")
}

func (c *Checker) lombokValue(cl *ast.Class, o accessorsOptions, a *ast.Annotation) {
	cl.Mods |= ast.ModFinal
	for _, f := range c.instanceAndStaticFields(cl) {
		f.Mods |= ast.ModPrivate | ast.ModFinal
	}
	c.lombokGetter(cl, c.instanceAndStaticFields(cl), &ast.Annotation{Name: "Getter"}, o)
	if !hasMethodDecl(cl.Decl, "toString", 0) {
		c.lombokToStringNoAnno(cl)
	}
	c.lombokEqualsHashCodeNoAnno(cl)
	cta := &ast.Annotation{Name: "AllArgsConstructor"}
	if sn := annoString(a, "staticConstructor"); sn != "" {
		cta.Args = append(cta.Args, &ast.AnnoArg{Name: "staticName", Value: strLit(sn)})
	}
	c.lombokCtor(cl, cta, "all")
}

func (c *Checker) lombokToStringNoAnno(cl *ast.Class) {
	c.lombokToString(cl, &ast.Annotation{Name: "ToString"})
}

func (c *Checker) lombokEqualsHashCodeNoAnno(cl *ast.Class) {
	c.lombokEqualsHashCode(cl, &ast.Annotation{Name: "EqualsAndHashCode"})
}

func (c *Checker) lombokUtilityClass(cl *ast.Class) {
	cl.Mods |= ast.ModFinal
	for _, f := range cl.Fields {
		f.Mods |= ast.ModStatic
	}
	for _, ms := range cl.Methods {
		for _, m := range ms {
			if !m.IsCtor {
				m.Mods |= ast.ModStatic
			}
		}
	}
	for _, ctor := range cl.Ctors {
		ctor.Mods = (ctor.Mods &^ (ast.ModPublic | ast.ModProtected)) | ast.ModPrivate
	}
	cl.Utility = true
}

func (c *Checker) lombokFieldDefaults(cl *ast.Class, a *ast.Annotation) {
	level, _ := annoAccessArg(a.Arg("level"))
	makeFinal := annoBool(a, "makeFinal", false)
	for _, f := range c.instanceAndStaticFields(cl) {
		if f.Mods&(ast.ModPublic|ast.ModPrivate|ast.ModProtected) == 0 {
			f.Mods |= level
		}
		if makeFinal {
			f.Mods |= ast.ModFinal
		}
	}
}

// ---------------------------------------------------------------- builder

// lombokBuilder generates a nested Builder class for @Builder.
func (c *Checker) lombokBuilder(cl *ast.Class, a *ast.Annotation, classAnnos []*ast.Annotation) {
	fields := c.ctorFields(cl, "all")
	if len(fields) == 0 {
		fields = c.instanceAndStaticFields(cl)
	}
	builderName := annoString(a, "builderClassName")
	if builderName == "" {
		builderName = cl.Name + "Builder"
	}
	buildName := annoString(a, "buildMethodName")
	if buildName == "" {
		buildName = "build"
	}
	factoryName := annoString(a, "builderMethodName")
	if factoryName == "" {
		factoryName = "builder"
	}
	toBuilder := annoBool(a, "toBuilder", false)
	setterPrefix := annoString(a, "setterPrefix")

	// an all-args constructor the builder can call
	allArgs := &ast.Method{Name: "<init>", Owner: cl, IsCtor: true, Mods: ast.ModPublic, Result: ast.TVoid, Pos: pos()}
	for _, f := range fields {
		allArgs.Params = append(allArgs.Params, f.Type)
		allArgs.ParamNames = append(allArgs.ParamNames, f.Name)
		var stmts []ast.Stmt
		if f.NonNull {
			msg := f.Name + " is marked non-null but is null"
			stmts = append(stmts, ifOf(isNull(id(f.Name)), throwOf(newObj(c.b.NPE, strLit(msg))), nil))
		}
		stmts = append(stmts, exprStmtOf(assignTo(thisField(f), id(f.Name))))
		allArgs.Body = blockOf(append(stmtsFrom(allArgs.Body), stmts...)...)
	}
	c.addSynthCtor(cl, allArgs)

	// the builder class itself
	b := c.newBuilderClass(cl, builderName)
	for _, f := range fields {
		bf := &ast.Field{Name: f.Name, Type: f.Type, Mods: ast.ModPrivate, Pos: pos(), Storage: true, Owner: b}
		if f.Decl != nil && f.Decl.Init != nil {
			bf.DefaultExpr = f.Decl.Init // @Builder.Default
		}
		c.addSynthField(b, bf)
		// Builder field(T value) { this.f = value; return this }
		body := blockOf(
			exprStmtOf(assignTo(sel(&ast.This{ExprBase: ast.ExprBase{Pos: pos()}}, f.Name), id("value"))),
			returnOf(&ast.This{ExprBase: ast.ExprBase{Pos: pos(), T: &ast.ClassType{Class: b}}}),
		)
		bm := c.newSynthMethod(b, setterPrefix+f.Name, ast.ModPublic, &ast.ClassType{Class: b}, []ast.Type{f.Type}, []string{"value"}, body, "")
		bm.Anno = "@Builder"
		c.addSynthMethod(b, bm)
	}
	// build()
	args := make([]ast.Expr, len(fields))
	for i, f := range fields {
		args[i] = sel(&ast.This{ExprBase: ast.ExprBase{Pos: pos()}}, f.Name)
	}
	build := c.newSynthMethod(b, buildName, ast.ModPublic, &ast.ClassType{Class: cl, Args: typeVarArgs(cl)}, nil, nil,
		blockOf(returnOf(newObj(cl, args...))), "")
	build.Anno = "@Builder"
	c.addSynthMethod(b, build)

	// static builder() on the annotated class
	if !hasMethodDecl(cl.Decl, factoryName, 0) {
		fac := c.newSynthMethod(cl, factoryName, (ast.ModPublic|ast.ModStatic), &ast.ClassType{Class: b}, nil, nil,
			blockOf(returnOf(newObj(b))), "")
		fac.Anno = "@Builder"
		c.addSynthMethod(cl, fac)
	}
	if toBuilder {
		tb := c.newSynthMethod(cl, "toBuilder", ast.ModPublic, &ast.ClassType{Class: b}, nil, nil,
			blockOf(returnOf(newObj(b))), "")
		tb.Anno = "@Builder"
		c.addSynthMethod(cl, tb)
	}
	// initialize @Builder.Default fields
	for _, bf := range b.Fields {
		if bf.DefaultExpr == nil {
			continue
		}
		for _, ctor := range b.Ctors {
			ctor.Body = blockOf(append(ctor.Body.Stmts,
				exprStmtOf(assignTo(sel(&ast.This{ExprBase: ast.ExprBase{Pos: pos()}}, bf.Name), bf.DefaultExpr)))...)
		}
	}
	c.layout(b)
}

func stmtsFrom(b *ast.Block) []ast.Stmt {
	if b == nil {
		return nil
	}
	return b.Stmts
}

// newBuilderClass creates the nested builder type.
func (c *Checker) newBuilderClass(owner *ast.Class, name string) *ast.Class {
	cd := &ast.ClassDecl{Pos: pos(), Kind: ast.KindClass, Name: name, Mods: ast.ModPublic | ast.ModStatic}
	b := c.newClass(name, owner.Full+"$"+name, ast.KindClass)
	b.Decl = cd
	b.File = owner.File
	b.Owner = owner
	b.Mods = cd.Mods
	b.Builtin = owner.Builtin
	cd.Sym = b
	b.Super = c.objType
	b.Resolved = true
	owner.Nested[name] = b
	c.addSynthCtor(b, &ast.Method{Name: "<init>", Owner: b, IsCtor: true, Mods: ast.ModPublic, Result: ast.TVoid, Pos: pos()})
	return b
}

// lombokMethodBuilder supports @Builder on a constructor or static method.
func (c *Checker) lombokMethodBuilder(cl *ast.Class, d *ast.MethodDecl, a *ast.Annotation) {
	builderName := annoString(a, "builderClassName")
	if builderName == "" {
		builderName = cl.Name + "Builder"
	}
	buildName := annoString(a, "buildMethodName")
	if buildName == "" {
		buildName = "build"
	}
	factoryName := annoString(a, "builderMethodName")
	if factoryName == "" {
		factoryName = "builder"
	}
	if _, exists := cl.Nested[builderName]; exists {
		return
	}
	b := c.newBuilderClass(cl, builderName)
	var params []ast.Type
	var names []string
	for _, p := range d.Params {
		t := c.resolveType(c.classEnv(cl), p.Type)
		params = append(params, t)
		names = append(names, p.Name)
		bf := &ast.Field{Name: p.Name, Type: t, Mods: ast.ModPrivate, Pos: p.Pos, Storage: true, Owner: b}
		c.addSynthField(b, bf)
		body := blockOf(
			exprStmtOf(assignTo(sel(&ast.This{ExprBase: ast.ExprBase{Pos: pos()}}, p.Name), id("value"))),
			returnOf(&ast.This{ExprBase: ast.ExprBase{Pos: pos(), T: &ast.ClassType{Class: b}}}),
		)
		bm := c.newSynthMethod(b, p.Name, ast.ModPublic, &ast.ClassType{Class: b}, []ast.Type{t}, []string{"value"}, body, "")
		bm.Anno = "@Builder"
		c.addSynthMethod(b, bm)
	}
	args := make([]ast.Expr, len(names))
	for i, n := range names {
		args[i] = sel(&ast.This{ExprBase: ast.ExprBase{Pos: pos()}}, n)
	}
	var buildExpr ast.Expr
	if d.IsCtor {
		buildExpr = newObj(cl, args...)
	} else {
		buildExpr = callNew(nil, d.Name, args...)
	}
	result := d.Sym.Result
	if d.IsCtor {
		result = &ast.ClassType{Class: cl, Args: typeVarArgs(cl)}
	}
	build := c.newSynthMethod(b, buildName, ast.ModPublic, result, nil, nil,
		blockOf(returnOf(buildExpr)), "")
	build.Anno = "@Builder"
	c.addSynthMethod(b, build)

	fac := c.newSynthMethod(cl, factoryName, ast.ModPublic|ast.ModStatic, &ast.ClassType{Class: b}, nil, nil,
		blockOf(returnOf(newObj(b))), "")
	fac.Anno = "@Builder"
	c.addSynthMethod(cl, fac)
	c.layout(b)
}

// ---------------------------------------------------------------- misc

func (c *Checker) lombokWith(cl *ast.Class, f *ast.Field, a *ast.Annotation) {
	fields := c.ctorFields(cl, "all")
	var params []ast.Type
	var names []string
	for _, x := range fields {
		params = append(params, x.Type)
		names = append(names, x.Name)
	}
	args := make([]ast.Expr, len(fields))
	for i, x := range fields {
		if x == f {
			args[i] = id("value")
			continue
		}
		args[i] = sel(&ast.This{ExprBase: ast.ExprBase{Pos: pos()}}, x.Name)
	}
	m := c.newSynthMethod(cl, "with"+util.Capitalize(f.Name), ast.ModPublic, &ast.ClassType{Class: cl, Args: typeVarArgs(cl)},
		[]ast.Type{f.Type}, []string{"value"}, blockOf(returnOf(newObj(cl, args...))), "")
	m.Anno = "@With"
	c.addSynthMethod(cl, m)
}

func (c *Checker) lombokNonNullParams(d *ast.MethodDecl) {
	if d.Body == nil || d.Sym == nil {
		return
	}
	var pre []ast.Stmt
	for _, p := range d.Params {
		if hasAnno(p.Annos, "NonNull") == nil {
			continue
		}
		msg := p.Name + " is marked non-null but is null"
		pre = append(pre, ifOf(isNull(id(p.Name)),
			throwOf(newObj(c.b.NPE, strLit(msg))), nil))
	}
	d.Body.Stmts = append(pre, d.Body.Stmts...)
}

func (c *Checker) lombokSneakyThrows(d *ast.MethodDecl) {
	if d.Body == nil {
		return
	}
	cat := &ast.Catch{Pos: pos(), Types: []*ast.TypeExpr{{Pos: pos(), Name: "Throwable"}}, Name: "t", Body: blockOf(throwOf(id("t")))}
	t := &ast.Try{Pos: pos(), Body: d.Body, Catches: []*ast.Catch{cat}}
	d.Body = blockOf(t)
}

func (c *Checker) lombokSynchronized(cl *ast.Class, d *ast.MethodDecl) {
	if d.Body == nil {
		return
	}
	var lock ast.Expr = &ast.This{ExprBase: ast.ExprBase{Pos: pos()}}
	if d.Sym != nil && d.Sym.IsStatic() {
		lock = id("__lock$" + cl.Name)
		lf := cl.FieldMap["__lock$"+cl.Name]
		if lf == nil {
			lf = &ast.Field{Name: "__lock$" + cl.Name, Type: c.objType,
				Mods: ast.ModPrivate | ast.ModStatic | ast.ModFinal, Pos: pos(), Storage: true, Anno: "@Synchronized"}
			c.addSynthField(cl, lf)
		}
		d.Sym.SyncOn = lf
		lock = id(lf.Name)
	}
	d.Body = blockOf(&ast.Sync{Pos: pos(), Lock: lock, Body: d.Body})
}

// lombokLocked wraps the body in a synchronized block on a named field.
func (c *Checker) lombokLocked(cl *ast.Class, d *ast.MethodDecl, a *ast.Annotation) {
	if d.Body == nil {
		return
	}
	name := annoString(a, "value")
	if name == "" {
		name = "__lock$" + cl.Name
	}
	lf := cl.FieldMap[name]
	if lf == nil {
		lf = &ast.Field{Name: name, Type: c.objType,
			Mods: ast.ModPrivate | ast.ModStatic | ast.ModFinal, Pos: pos(), Storage: true, Anno: "@Locked"}
		c.addSynthField(cl, lf)
	}
	d.Body = blockOf(&ast.Sync{Pos: pos(), Lock: id(name), Body: d.Body})
}

// lombokLog creates the `log` field for the @Log family.
func (c *Checker) lombokLog(cl *ast.Class, a *ast.Annotation) {
	if cl.FieldMap["log"] != nil {
		return
	}
	logCls := c.global["Logger"]
	if logCls == nil {
		return
	}
	f := &ast.Field{Name: "log", Type: &ast.ClassType{Class: logCls},
		Mods: ast.ModPrivate | ast.ModStatic | ast.ModFinal, Pos: pos(), Storage: true, Anno: "@" + a.Name}
	c.addSynthField(cl, f)
	if cl.ClInit == nil {
		cl.ClInit = &ast.Method{Name: "<clinit>", Owner: cl, Mods: ast.ModStatic, Result: ast.TVoid, Pos: pos(), SynthKind: "clinit"}
	}
	init := newObj(logCls, strLit(cl.Name))
	init.SetType(&ast.ClassType{Class: logCls})
	init.Ctor = c.simpleCtor(logCls, init.Args)
	f.InitExpr = init
}

func (c *Checker) lombokStandardException(cl *ast.Class) {
	strT := c.strType
	c.addSynthCtor(cl, &ast.Method{Name: "<init>", Owner: cl, IsCtor: true, Mods: ast.ModPublic,
		Result: ast.TVoid, Pos: pos(), Body: blockOf()})
	c.addSynthCtor(cl, &ast.Method{Name: "<init>", Owner: cl, IsCtor: true, Mods: ast.ModPublic,
		Result: ast.TVoid, Params: []ast.Type{strT}, ParamNames: []string{"message"},
		Body: blockOf(exprStmtOf(callNew(nil, "super", id("message")))), Pos: pos()})
	thr := &ast.ClassType{Class: c.b.Throwable}
	c.addSynthCtor(cl, &ast.Method{Name: "<init>", Owner: cl, IsCtor: true, Mods: ast.ModPublic,
		Result: ast.TVoid, Params: []ast.Type{strT, thr}, ParamNames: []string{"message", "cause"},
		Body: blockOf(
			exprStmtOf(callNew(nil, "super", id("message"))),
			exprStmtOf(assignTo(sel(&ast.This{ExprBase: ast.ExprBase{Pos: pos()}}, "cause"), id("cause"))),
		), Pos: pos()})
	c.addSynthCtor(cl, &ast.Method{Name: "<init>", Owner: cl, IsCtor: true, Mods: ast.ModPublic,
		Result: ast.TVoid, Params: []ast.Type{thr}, ParamNames: []string{"cause"},
		Body: blockOf(exprStmtOf(assignTo(sel(&ast.This{ExprBase: ast.ExprBase{Pos: pos()}}, "cause"), id("cause")))), Pos: pos()})
}

func (c *Checker) lombokFieldNameConstants(cl *ast.Class, a *ast.Annotation) {
	const name = "Fields"
	if _, exists := cl.Nested[name]; exists {
		return
	}
	cd := &ast.ClassDecl{Pos: pos(), Kind: ast.KindClass, Name: name, Mods: ast.ModPublic | ast.ModStatic | ast.ModFinal}
	f := c.newClass(name, cl.Full+"$"+name, ast.KindClass)
	f.Decl = cd
	f.File = cl.File
	f.Owner = cl
	f.Mods = cd.Mods
	f.Builtin = cl.Builtin
	cd.Sym = f
	f.Super = c.objType
	f.Resolved = true
	cl.Nested[name] = f
	prefix := annoString(a, "prefix")
	for _, fld := range c.instanceAndStaticFields(cl) {
		cf := &ast.Field{Name: fld.Name, Type: c.strType,
			Mods: ast.ModPublic | ast.ModStatic | ast.ModFinal, Pos: pos(), Storage: true,
			Anno: "@FieldNameConstants"}
		cf.ConstVal = constValue{s: prefix + fld.Name, kind: ast.LitString, ok: true}
		cf.InitExpr = strLit(prefix + fld.Name)
		if f.ClInit == nil {
			f.ClInit = &ast.Method{Name: "<clinit>", Owner: f, Mods: ast.ModStatic,
				Result: ast.TVoid, Pos: pos(), SynthKind: "clinit"}
		}
		c.addSynthField(f, cf)
	}
	c.layout(f)
}

// lombokExtensionMethods records @ExtensionMethod classes for call rewriting.
func (c *Checker) lombokExtensionMethods(cl *ast.Class) []*ast.Class {
	cd := cl.Decl
	if cd == nil {
		return nil
	}
	a := hasAnno(cd.Annos, "ExtensionMethod")
	if a == nil {
		return nil
	}
	var out []*ast.Class
	for _, te := range annoClasses(a) {
		if x := c.resolveType(c.classEnv(cl), te); x != nil {
			if ct, ok := x.(*ast.ClassType); ok {
				out = append(out, ct.Class)
			}
		}
	}
	return out
}

// applyLombokToProgram runs the pass over every class, twice for nested
// builders that only appear once their owner is processed.
func (c *Checker) applyLombokToProgram() {
	seen := map[*ast.Class]bool{}
	for i := 0; i < 2; i++ {
		for _, cl := range append([]*ast.Class(nil), c.classes...) {
			if cl.Builtin || seen[cl] {
				continue
			}
			seen[cl] = true
			c.applyLombok(cl)
		}
	}
	c.extensions = map[*ast.Class][]*ast.Class{}
	for _, cl := range c.classes {
		if cl.Builtin {
			continue
		}
		if exts := c.lombokExtensionMethods(cl); len(exts) > 0 {
			c.extensions[cl] = exts
			for _, e := range cl.Subclasses {
				c.extensions[e] = exts
			}
		}
	}
}

// sortedFieldNames is used by deterministic field iteration.
func sortedFieldNames(fields []*ast.Field) []string {
	out := make([]string, 0, len(fields))
	for _, f := range fields {
		out = append(out, f.Name)
	}
	sort.Strings(out)
	return out
}
