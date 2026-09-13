package dev.teyru.compiler.core.frontend;

import dev.teyru.compiler.core.budget.*;
import dev.teyru.compiler.core.source.SourceFile;

public interface TeyruLexer {
  LexResult lex(
      SourceFile source,
      FrontendOptions options,
      ResourceTracker resources,
      CancellationToken cancellation);
}
