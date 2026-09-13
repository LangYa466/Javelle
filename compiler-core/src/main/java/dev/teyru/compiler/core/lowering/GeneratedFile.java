package dev.teyru.compiler.core.lowering;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import dev.teyru.compiler.core.source.*;
import dev.teyru.compiler.core.sourcemap.SourceMap;

public record GeneratedFile(
    SourceId source,
    String relativeUri,
    String javaText,
    SourceMap sourceMap,
    String contentSha256) {
  public GeneratedFile {
    Objects.requireNonNull(source);
    Objects.requireNonNull(javaText);
    Objects.requireNonNull(sourceMap);
    new SourceId(relativeUri, "0".repeat(64));
    if (!contentSha256.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("hash");
    if (!contentSha256.equals(hash(javaText)))
      throw new IllegalArgumentException("generated content hash mismatch");
  }

  public static GeneratedFile create(
      SourceId source, String relativeUri, String javaText, SourceMap sourceMap) {
    return new GeneratedFile(source, relativeUri, javaText, sourceMap, hash(javaText));
  }

  private static String hash(String text) {
    try {
      return java.util.HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException impossible) {
      throw new AssertionError(impossible);
    }
  }
}
