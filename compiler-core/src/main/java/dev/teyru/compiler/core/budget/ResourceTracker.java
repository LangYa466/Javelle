package dev.teyru.compiler.core.budget;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

public final class ResourceTracker {
  private final ResourceBudget budget;
  private final CancellationToken cancellation;
  private final LongSupplier clock;
  private final AtomicInteger tokens = new AtomicInteger(),
      nodes = new AtomicInteger(),
      diagnostics = new AtomicInteger();
  private final AtomicLong bytes = new AtomicLong();

  public ResourceTracker(
      ResourceBudget budget, CancellationToken cancellation, LongSupplier clock) {
    this.budget = budget;
    this.cancellation = cancellation;
    this.clock = clock;
  }

  public void checkpoint() {
    cancellation.throwIfCancelled();
    budget.checkDeadline(clock.getAsLong());
  }

  public void addBytes(long n) {
    if (n < 0) throw new IllegalArgumentException("byte increment must be non-negative");
    long total;
    try {
      total = bytes.updateAndGet(old -> Math.addExact(old, n));
    } catch (ArithmeticException overflow) {
      throw new ResourceLimitException("JVL-RESOURCE-BYTES", budget.maxBytes(), Long.MAX_VALUE);
    }
    budget.checkBytes(total);
    checkpoint();
  }

  public void token() {
    check("JVL-RESOURCE-TOKENS", tokens.incrementAndGet(), budget.maxTokens());
  }

  public void node() {
    check("JVL-RESOURCE-NODES", nodes.incrementAndGet(), budget.maxNodes());
  }

  public void diagnostic() {
    check("JVL-RESOURCE-DIAGNOSTICS", diagnostics.incrementAndGet(), budget.maxDiagnostics());
  }

  public void nesting(int depth) {
    if (depth < 0) throw new IllegalArgumentException("nesting must be non-negative");
    check("JVL-RESOURCE-NESTING", depth, budget.maxNesting());
  }

  private void check(String code, int actual, int limit) {
    checkpoint();
    if (actual > limit) throw new ResourceLimitException(code, limit, actual);
  }
}
