package dev.teyru.compiler.core.semantic;

import java.util.*;
import dev.teyru.compiler.core.diagnostic.*;
import dev.teyru.compiler.core.semantic.BoundCompilationUnit.*;

public final class EarlyTypeChecker {
  public List<Diagnostic> check(BoundCompilationUnit unit) {
    var out = new ArrayList<Diagnostic>();
    for (var type : unit.classes())
      for (var member : type.members()) {
        if (member.type().contains("<") || type.name().contains("<"))
          unsupported(unit, member.node(), "generic", out);
        if (member instanceof BoundMethod m)
          for (var statement : m.body()) checkStatement(unit, statement, out);
        if (member instanceof BoundProperty p) {
          p.getter().ifPresent(a -> a.body().forEach(s -> checkStatement(unit, s, out)));
          p.setter().ifPresent(a -> a.body().forEach(s -> checkStatement(unit, s, out)));
        }
      }
    return List.copyOf(out);
  }

  private void checkStatement(
      BoundCompilationUnit unit, BoundStatement statement, List<Diagnostic> out) {
    if (statement instanceof BoundExpressionStatement e) checkExpression(unit, e.expression(), out);
    else if (statement instanceof BoundReturn r)
      r.value().ifPresent(x -> checkExpression(unit, x, out));
    else if (statement instanceof BoundLocal l) checkExpression(unit, l.initializer(), out);
    else if (statement instanceof BoundIf i) {
      checkExpression(unit, i.condition(), out);
      i.whenTrue().forEach(x -> checkStatement(unit, x, out));
      i.whenFalse().forEach(x -> checkStatement(unit, x, out));
    }
  }

  private void checkExpression(
      BoundCompilationUnit unit, BoundExpression expression, List<Diagnostic> out) {
    if (expression instanceof Binary b
        && Set.of("++", "--", "+=", "-=", "*=", "/=").contains(b.operator()))
      unsupported(unit, b.node(), "compound-operation", out);
    if (expression instanceof Assignment a && !type(a.target()).equals(type(a.value())))
      out.add(
          diag(
              unit,
              a.target() instanceof Name n ? n.node() : ((Member) a.target()).node(),
              "TY-TYP-0001",
              "assignment type mismatch"));
  }

  private static dev.teyru.compiler.core.symbol.TypeRef type(BoundExpression e) {
    if (e instanceof Literal x) return x.type();
    if (e instanceof Name x) return x.type();
    if (e instanceof Member x) return x.type();
    if (e instanceof Call x) return x.type();
    if (e instanceof NewObject x) return x.type();
    if (e instanceof Cast x) return x.type();
    if (e instanceof Unary x) return x.type();
    if (e instanceof Binary x) return x.type();
    return ((Assignment) e).type();
  }

  private void unsupported(
      BoundCompilationUnit u,
      dev.teyru.compiler.core.syntax.NodeId n,
      String f,
      List<Diagnostic> o) {
    o.add(
        new Diagnostic(
            1,
            new DiagnosticCode("TY-DEV-0001"),
            Severity.ERROR,
            u.source(),
            n.rawRange(),
            "unsupported " + f,
            List.of(),
            Map.of("feature", f, "stage", "P06")));
  }

  private Diagnostic diag(
      BoundCompilationUnit u, dev.teyru.compiler.core.syntax.NodeId n, String c, String m) {
    return new Diagnostic(
        1, new DiagnosticCode(c), Severity.ERROR, u.source(), n.rawRange(), m, List.of(), Map.of());
  }
}
