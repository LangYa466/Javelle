package dev.teyru.compiler.core.frontend;

import dev.teyru.compiler.core.budget.*;
import dev.teyru.compiler.core.source.SourceFile;

public final class TeyruFrontend {
  private final TeyruLexer lexer = new StatefulTeyruLexer();
  private final TeyruParser parser = new RecursiveTeyruParser();

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
