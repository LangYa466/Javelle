package org.javelle.compiler.core.emitter;

public record EmitOptions(
    int languageMajor,
    int targetRelease,
    String generatorVersion,
    String indent,
    String lineSeparator) {
  public EmitOptions {
    if (languageMajor != 1
        || targetRelease != 21 && targetRelease != 25
        || generatorVersion.isBlank()
        || indent.isEmpty()
        || !lineSeparator.equals("\n"))
      throw new IllegalArgumentException("unsupported emission options");
  }
}
