package dev.teyru.compiler.core.diagnostic;

public record DiagnosticCode(String value) {
  public DiagnosticCode {
    if (value == null || !(value.matches("TY-[A-Z0-9-]+") || value.matches("JVL-[A-Z0-9-]+")))
      throw new IllegalArgumentException("invalid diagnostic code");
  }
}
