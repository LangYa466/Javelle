package org.javelle.compiler.core.budget;

import java.util.concurrent.CancellationException;

@FunctionalInterface
public interface CancellationToken {
  boolean isCancelled();

  default void throwIfCancelled() {
    if (isCancelled()) throw new CancellationException("operation cancelled");
  }

  static CancellationToken none() {
    return () -> false;
  }
}
