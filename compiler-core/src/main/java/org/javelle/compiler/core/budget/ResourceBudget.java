package org.javelle.compiler.core.budget;

public record ResourceBudget(
    long maxBytes,
    int maxTokens,
    int maxNodes,
    int maxDiagnostics,
    int maxNesting,
    long deadlineNanos) {
  public ResourceBudget {
    if (maxBytes < 1
        || maxTokens < 1
        || maxNodes < 1
        || maxDiagnostics < 1
        || maxNesting < 1
        || deadlineNanos < 1) throw new IllegalArgumentException("budget values must be positive");
  }

  public void checkBytes(long bytes) {
    if (bytes > maxBytes) throw new ResourceLimitException("JVL-RESOURCE-BYTES", maxBytes, bytes);
  }

  public void checkDeadline(long now) {
    if (now > deadlineNanos)
      throw new ResourceLimitException("JVL-RESOURCE-DEADLINE", deadlineNanos, now);
  }
}
