package org.javelle.compiler.core.frontend;

import org.javelle.compiler.core.budget.*;
import org.javelle.compiler.core.source.SourceFile;

public interface JavelleParser {
  ParseResult parse(
      SourceFile source,
      LexResult lexical,
      FrontendOptions options,
      ResourceTracker resources,
      CancellationToken cancellation);
}
