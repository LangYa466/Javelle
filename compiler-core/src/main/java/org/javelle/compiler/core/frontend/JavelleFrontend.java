package org.javelle.compiler.core.frontend;

import org.javelle.compiler.core.budget.*;
import org.javelle.compiler.core.source.SourceFile;

public final class JavelleFrontend {
  private final JavelleLexer lexer = new StatefulJavelleLexer();
  private final JavelleParser parser = new RecursiveJavelleParser();

  public ParseResult parse(
      SourceFile source,
      FrontendOptions options,
      ResourceBudget budget,
      CancellationToken cancellation) {
    var resources = new ResourceTracker(budget, cancellation, System::nanoTime);
    var lexical = lexer.lex(source, options, resources, cancellation);
    return parser.parse(source, lexical, options, resources, cancellation);
  }
}
