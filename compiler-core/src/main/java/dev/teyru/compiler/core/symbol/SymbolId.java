package dev.teyru.compiler.core.symbol;

public record SymbolId(
    String moduleId,
    String ownerBinaryName,
    SymbolKind kind,
    String simpleName,
    String erasedDescriptor,
    int declarationOrdinal) {
  public SymbolId {
    if (moduleId.isBlank()
        || ownerBinaryName.isBlank()
        || simpleName.isBlank()
        || erasedDescriptor.isBlank()
        || declarationOrdinal < 0) throw new IllegalArgumentException("invalid symbol");
  }
}
