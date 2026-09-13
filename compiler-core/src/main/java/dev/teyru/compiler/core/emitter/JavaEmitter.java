package dev.teyru.compiler.core.emitter;

import dev.teyru.compiler.core.budget.*;
import dev.teyru.compiler.core.semantic.BoundCompilationUnit;

public interface JavaEmitter {
  EmitResult emit(
      BoundCompilationUnit unit,
      EmitOptions options,
      ResourceTracker resources,
      CancellationToken cancellation);
}
