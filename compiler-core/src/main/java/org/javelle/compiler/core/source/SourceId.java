package org.javelle.compiler.core.source;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;

public record SourceId(String workspaceRelativeUri, String contentSha256) {
  public SourceId {
    Objects.requireNonNull(workspaceRelativeUri);
    Objects.requireNonNull(contentSha256);
    String decoded =
        URLDecoder.decode(workspaceRelativeUri, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
    if (workspaceRelativeUri.isBlank()
        || workspaceRelativeUri.startsWith("/")
        || workspaceRelativeUri.contains("\\")
        || workspaceRelativeUri.indexOf('\0') >= 0
        || workspaceRelativeUri.contains("?")
        || workspaceRelativeUri.contains("#")
        || workspaceRelativeUri.contains("!/")
        || decoded.equals("..")
        || decoded.startsWith("../")
        || decoded.contains("/../")
        || workspaceRelativeUri.matches("^[A-Za-z]:.*")
        || workspaceRelativeUri.startsWith("//"))
      throw new IllegalArgumentException("unsafe workspace-relative URI");
    if (!contentSha256.matches("[0-9a-f]{64}"))
      throw new IllegalArgumentException("invalid SHA-256");
  }

  public static SourceId forContent(String workspaceRelativeUri, byte[] utf8) {
    Objects.requireNonNull(utf8);
    try {
      return new SourceId(
          workspaceRelativeUri,
          HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(utf8)));
    } catch (java.security.NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }
}
