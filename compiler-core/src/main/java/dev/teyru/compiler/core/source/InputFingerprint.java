package dev.teyru.compiler.core.source;

import java.util.Objects;

public record InputFingerprint(String algorithm, String hex) {
  public InputFingerprint {
    Objects.requireNonNull(algorithm);
    Objects.requireNonNull(hex);
    if (!algorithm.equals("SHA-256") || !hex.matches("[0-9a-f]{64}"))
      throw new IllegalArgumentException("invalid fingerprint");
  }
}
