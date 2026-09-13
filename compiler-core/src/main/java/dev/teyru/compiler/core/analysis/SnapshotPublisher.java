package dev.teyru.compiler.core.analysis;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import dev.teyru.compiler.core.budget.CancellationToken;

public final class SnapshotPublisher {
  private final AtomicReference<AnalysisSnapshot> current = new AtomicReference<>();

  public AnalysisSnapshot compute(Supplier<AnalysisSnapshot> operation, CancellationToken token) {
    token.throwIfCancelled();
    AnalysisSnapshot candidate = operation.get();
    token.throwIfCancelled();
    current.set(candidate);
    return candidate;
  }

  public Optional<AnalysisSnapshot> current() {
    return Optional.ofNullable(current.get());
  }
}
