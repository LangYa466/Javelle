package dev.teyru.compiler.core.lowering;

import dev.teyru.compiler.core.sourcemap.GeneratedRange;

public interface JavaGenerationSink {
  GeneratedRange emit(JavaFragment fragment, SourceOrigin origin);
}
