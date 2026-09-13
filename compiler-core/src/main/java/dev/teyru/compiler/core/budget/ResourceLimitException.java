package dev.teyru.compiler.core.budget;

public final class ResourceLimitException extends RuntimeException {
  private static final long serialVersionUID = 1L;
  private final String code;

  public ResourceLimitException(String code, long limit, long actual) {
    super(code + " limit=" + limit + " actual=" + actual);
    this.code = code;
  }

  public String code() {
    return code;
  }
}
