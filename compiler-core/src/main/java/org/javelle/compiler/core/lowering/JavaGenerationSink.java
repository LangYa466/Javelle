package org.javelle.compiler.core.lowering;

import org.javelle.compiler.core.sourcemap.GeneratedRange;

public interface JavaGenerationSink {
  GeneratedRange emit(JavaFragment fragment, SourceOrigin origin);
}
