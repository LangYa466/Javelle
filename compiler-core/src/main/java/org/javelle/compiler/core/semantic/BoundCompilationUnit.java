package org.javelle.compiler.core.semantic;

import java.util.*;
import org.javelle.compiler.core.source.*;
import org.javelle.compiler.core.symbol.*;
import org.javelle.compiler.core.syntax.NodeId;

public record BoundCompilationUnit(
    SourceFile sourceFile, String packageName, List<String> imports, List<BoundClass> classes) {
  public BoundCompilationUnit {
    Objects.requireNonNull(sourceFile);
    packageName = Objects.requireNonNull(packageName);
    imports =
        imports.stream()
            .distinct()
            .sorted(
                Comparator.comparing((String value) -> value.startsWith("static "))
                    .thenComparing(Comparator.naturalOrder()))
            .toList();
    classes = List.copyOf(classes);
  }

  public SourceId source() {
    return sourceFile.id();
  }

  public record BoundClass(NodeId node, String visibility, String name, List<BoundMember> members) {
    public BoundClass {
      members = List.copyOf(members);
    }
  }

  public sealed interface BoundMember permits BoundField, BoundMethod, BoundProperty {
    NodeId node();

    String visibility();

    String type();

    String name();
  }

  public record BoundField(
      NodeId node,
      String visibility,
      List<String> modifiers,
      String type,
      String name,
      Optional<BoundExpression> initializer)
      implements BoundMember {
    public BoundField {
      modifiers = List.copyOf(modifiers);
      initializer = Objects.requireNonNull(initializer);
    }
  }

  public record BoundMethod(
      NodeId node,
      String visibility,
      List<String> modifiers,
      String type,
      String name,
      List<BoundParameter> parameters,
      List<BoundStatement> body)
      implements BoundMember {
    public BoundMethod {
      modifiers = List.copyOf(modifiers);
      parameters = List.copyOf(parameters);
      body = List.copyOf(body);
    }
  }

  public record BoundParameter(String type, String name) {}

  public record BoundProperty(
      NodeId node,
      String visibility,
      List<String> modifiers,
      String type,
      String name,
      Optional<BoundExpression> initializer,
      Optional<BoundAccessor> getter,
      Optional<BoundAccessor> setter,
      PropertyDescriptor descriptor)
      implements BoundMember {
    public BoundProperty {
      modifiers = List.copyOf(modifiers);
      initializer = Objects.requireNonNull(initializer);
      getter = Objects.requireNonNull(getter);
      setter = Objects.requireNonNull(setter);
      Objects.requireNonNull(descriptor);
    }
  }

  public record BoundAccessor(
      String visibility, String parameterName, List<BoundStatement> body, boolean defaultBody) {
    public BoundAccessor {
      body = List.copyOf(body);
    }
  }

  public sealed interface BoundStatement
      permits BoundLocal, BoundReturn, BoundExpressionStatement, BoundIf {}

  public record BoundLocal(
      String keyword, String explicitType, String name, BoundExpression initializer)
      implements BoundStatement {}

  public record BoundReturn(Optional<BoundExpression> value) implements BoundStatement {
    public BoundReturn {
      value = Objects.requireNonNull(value);
    }
  }

  public record BoundExpressionStatement(BoundExpression expression) implements BoundStatement {}

  public record BoundIf(
      BoundExpression condition, List<BoundStatement> whenTrue, List<BoundStatement> whenFalse)
      implements BoundStatement {
    public BoundIf {
      whenTrue = List.copyOf(whenTrue);
      whenFalse = List.copyOf(whenFalse);
    }
  }

  public sealed interface BoundExpression
      permits Literal, Name, Member, Call, NewObject, Cast, Unary, Binary, Assignment {
    TypeRef type();

    NodeId node();
  }

  public record Literal(String javaText, TypeRef type, NodeId node) implements BoundExpression {}

  public record Name(String javaName, TypeRef type, NodeId node, Optional<SymbolId> symbol)
      implements BoundExpression {
    public Name {
      symbol = Objects.requireNonNull(symbol);
    }
  }

  public record Member(
      BoundExpression receiver,
      String javaName,
      TypeRef type,
      NodeId node,
      Optional<PropertyDescriptor> property)
      implements BoundExpression {
    public Member {
      property = Objects.requireNonNull(property);
    }
  }

  public record Call(
      BoundExpression target, List<BoundExpression> arguments, TypeRef type, NodeId node)
      implements BoundExpression {
    public Call {
      arguments = List.copyOf(arguments);
    }
  }

  public record NewObject(
      String typeName, List<BoundExpression> arguments, TypeRef type, NodeId node)
      implements BoundExpression {
    public NewObject {
      arguments = List.copyOf(arguments);
    }
  }

  public record Cast(String typeName, BoundExpression value, TypeRef type, NodeId node)
      implements BoundExpression {}

  public record Unary(String operator, BoundExpression value, TypeRef type, NodeId node)
      implements BoundExpression {}

  public record Binary(
      String operator, BoundExpression left, BoundExpression right, TypeRef type, NodeId node)
      implements BoundExpression {}

  public record Assignment(BoundExpression target, BoundExpression value, TypeRef type, NodeId node)
      implements BoundExpression {}
}
