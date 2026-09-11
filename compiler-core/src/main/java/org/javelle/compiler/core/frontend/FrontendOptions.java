package org.javelle.compiler.core.frontend;

public record FrontendOptions(int languageMajor, int maxDiagnostics) {
  public FrontendOptions {
    if (languageMajor != 1 || maxDiagnostics < 1) throw new IllegalArgumentException();
  }
}
