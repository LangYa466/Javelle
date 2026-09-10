package org.javelle.compiler.core.frontend;

import org.javelle.compiler.core.budget.*;
import org.javelle.compiler.core.source.SourceFile;

public interface JavelleLexer {
  LexResult lex(
      SourceFile source,
      FrontendOptions options,
      ResourceTracker resources,
      CancellationToken cancellation);
}
