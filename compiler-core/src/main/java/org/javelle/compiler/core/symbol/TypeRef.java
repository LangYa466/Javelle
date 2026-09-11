package org.javelle.compiler.core.symbol;

public sealed interface TypeRef
    permits PrimitiveType,
        DeclaredType,
        ArrayType,
        TypeVariable,
        WildcardType,
        IntersectionType,
        CapturedType,
        AnonymousType,
        AnnotatedType,
        NullType,
        ErrorType {
  String displayName();
}
