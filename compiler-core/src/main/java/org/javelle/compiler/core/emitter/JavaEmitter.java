package org.javelle.compiler.core.emitter;

import org.javelle.compiler.core.budget.*;
import org.javelle.compiler.core.semantic.BoundCompilationUnit;

public interface JavaEmitter {
  EmitResult emit(
      BoundCompilationUnit unit,
      EmitOptions options,
      ResourceTracker resources,
      CancellationToken cancellation);
}
