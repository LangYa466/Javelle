/* SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0 */
package org.javelle.compiler.driver;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.*;
import java.util.*;
import org.javelle.compiler.core.budget.*;
import org.javelle.compiler.core.frontend.*;
import org.javelle.compiler.core.semantic.*;
import org.javelle.compiler.core.source.*;
import org.javelle.workspace.model.*;

/** CLI-facing facade that keeps compiler-core types behind the driver boundary. */
public final class CliCompilerFacade {
  public List<SourceInput> loadProject(Path modelPath) throws java.io.IOException {
    Path file = modelPath.toAbsolutePath().normalize();
    byte[] json = java.nio.file.Files.readAllBytes(file);
    var read = new DefaultWorkspaceModelCodec().read(json, WorkspaceReadOptions.defaults());
    var model =
        read.model()
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        read.diagnostics().isEmpty()
                            ? "JV-WS-SCHEMA-INVALID"
                            : read.diagnostics().getFirst().code()));
    Path base = file.getParent().resolve(model.workspace().logicalRoot()).normalize();
    var result = new ArrayList<SourceInput>();
    for (var module : model.modules())
      for (var sourceSet : module.sourceSets())
        for (var rootRef : sourceSet.javelleRoots()) {
          Path root = base.resolve(rootRef.logicalPath()).normalize();
          if (!root.startsWith(base) || !java.nio.file.Files.isDirectory(root)) continue;
          try (var paths = java.nio.file.Files.walk(root)) {
            for (Path source :
                paths
                    .filter(
                        p ->
                            java.nio.file.Files.isRegularFile(p)
                                && p.toString().endsWith(".javelle"))
                    .sorted()
                    .toList()) {
              if (java.nio.file.Files.isSymbolicLink(source))
                throw new IllegalArgumentException("JV-WS-PATH-SYMLINK");
              result.add(
                  new SourceInput(
                      base.relativize(source).toString().replace('\\', '/'),
                      java.nio.file.Files.readAllBytes(source)));
            }
          }
        }
    if (result.isEmpty()) throw new IllegalArgumentException("JV-WS-NO-SOURCES");
    return List.copyOf(result);
  }

  public Result compile(List<SourceInput> sources, Path generated, Path classes, int release) {
    return compile(sources, generated, classes, release, Long.MAX_VALUE);
  }

  public Result compile(
      List<SourceInput> sources, Path generated, Path classes, int release, long deadlineNanos) {
    var decoded = new HashMap<String, SourceFile>();
    for (var input : sources)
      decoded.put(
          input.relativeUri(),
          SourceFile.decode(
              SourceId.forContent(input.relativeUri(), input.utf8()),
              input.utf8(),
              new ResourceBudget(20_000_000, 200_000, 200_000, 500, 500, Long.MAX_VALUE)));
    var request =
        new CompileRequest(
            sources,
            List.of(),
            List.of(),
            List.of(),
            generated,
            classes,
            release,
            false,
            List.of(),
            new ResourceBudget(20_000_000, 200_000, 200_000, 500, 500, deadlineNanos));
    var result = new DefaultCompilerDriver().compile(request, CancellationToken.none());
    var metadata = diagnosticMetadata(decoded);
    var diagnostics =
        result.diagnostics().stream()
            .map(
                d -> {
                  var original = d.originalLocations().stream().findFirst();
                  int start =
                      original
                          .map(x -> x.range().startOffset())
                          .orElse(d.generatedRange().startOffset());
                  int end =
                      original
                          .map(x -> x.range().endOffset())
                          .orElse(d.generatedRange().endOffset());
                  if (original.isPresent()) {
                    var file = decoded.get(original.orElseThrow().source().workspaceRelativeUri());
                    if (file != null) {
                      start =
                          file.convertBoundary(
                              start,
                              OffsetUnit.UNICODE_CODE_POINT,
                              OffsetUnit.RAW_UTF16,
                              Bias.START);
                      end =
                          file.convertBoundary(
                              end, OffsetUnit.UNICODE_CODE_POINT, OffsetUnit.RAW_UTF16, Bias.END);
                    }
                  }
                  return new Message(
                      d.code().value(),
                      d.severity().name(),
                      original.map(x -> x.source().workspaceRelativeUri()).orElse("generated"),
                      original.map(x -> x.source().contentSha256()).orElse("0".repeat(64)),
                      start,
                      end,
                      d.message(),
                      metadata.getOrDefault(d.code().value(), new Metadata("[]", "[]", "{}")));
                })
            .toList();
    var files =
        result.generatedFiles().stream()
            .map(
                f -> {
                  String map = sourceMapJson(f);
                  return new JavaFile(
                      f.relativeUri(), f.javaText(), f.contentSha256(), map, sha(map));
                })
            .toList();
    String inputFingerprint =
        sha(
            sources.stream()
                .sorted(Comparator.comparing(SourceInput::relativeUri))
                .map(s -> s.relativeUri() + "\0" + HexFormat.of().formatHex(s.utf8()))
                .collect(java.util.stream.Collectors.joining("\0")));
    return new Result(
        result.success(), result.javacExitCode(), diagnostics, files, inputFingerprint);
  }

  public record Result(
      boolean success,
      int javacExitCode,
      List<Message> diagnostics,
      List<JavaFile> generatedFiles,
      String inputFingerprint) {
    public Result {
      diagnostics = List.copyOf(diagnostics);
      generatedFiles = List.copyOf(generatedFiles);
    }
  }

  public record Message(
      String code,
      String severity,
      String sourceUri,
      String sourceSha256,
      int startRawUtf16,
      int endRawUtf16,
      String message,
      Metadata metadata) {}

  public record Metadata(String relatedJson, String fixesJson, String dataJson) {}

  public record JavaFile(
      String relativeUri, String javaText, String sha256, String sourceMapJson, String mapSha256) {}

  private static String sourceMapJson(org.javelle.compiler.core.lowering.GeneratedFile file) {
    var out =
        new StringBuilder("{\"schemaVersion\":1,\"generatedUri\":\"")
            .append(esc(file.relativeUri()))
            .append("\",\"segments\":[");
    for (int i = 0; i < file.sourceMap().segments().size(); i++) {
      if (i > 0) out.append(',');
      var s = file.sourceMap().segments().get(i);
      out.append("{\"start\":")
          .append(s.generated().range().startOffset())
          .append(",\"end\":")
          .append(s.generated().range().endOffset())
          .append(",\"kind\":\"")
          .append(s.kind())
          .append("\",\"originals\":[");
      for (int j = 0; j < s.originals().size(); j++) {
        if (j > 0) out.append(',');
        var o = s.originals().get(j);
        out.append("{\"uri\":\"")
            .append(esc(o.source().workspaceRelativeUri()))
            .append("\",\"sha256\":\"")
            .append(o.source().contentSha256())
            .append("\",\"start\":")
            .append(o.range().startOffset())
            .append(",\"end\":")
            .append(o.range().endOffset())
            .append('}');
      }
      out.append("],\"reason\":\"").append(esc(s.reason())).append("\"}");
    }
    return out.append("]}\n").toString();
  }

  private static String sha(String text) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException impossible) {
      throw new AssertionError(impossible);
    }
  }

  private static Map<String, Metadata> diagnosticMetadata(Map<String, SourceFile> sources) {
    var out = new HashMap<String, Metadata>();
    var budget = new ResourceBudget(20_000_000, 200_000, 200_000, 500, 500, Long.MAX_VALUE);
    for (var source : sources.values()) {
      var parsed =
          new JavelleFrontend()
              .parse(source, new FrontendOptions(1, 500), budget, CancellationToken.none());
      var binding = new EarlySemanticBinder().bind(parsed, source, "main");
      for (var d : binding.diagnostics()) out.putIfAbsent(d.code().value(), metadata(d));
    }
    return out;
  }

  private static Metadata metadata(org.javelle.compiler.core.diagnostic.Diagnostic d) {
    String related =
        d.related().stream()
            .map(
                r ->
                    "{\"source\":{\"uri\":\""
                        + esc(r.source().workspaceRelativeUri())
                        + "\",\"sha256\":\""
                        + r.source().contentSha256()
                        + "\"},\"range\":{\"unit\":\"RAW_UTF16\",\"start\":"
                        + r.range().startOffset()
                        + ",\"end\":"
                        + r.range().endOffset()
                        + "},\"message\":\""
                        + esc(r.message())
                        + "\"}")
            .collect(java.util.stream.Collectors.joining(",", "[", "]"));
    String fixes =
        d.fixes().stream()
            .map(
                f ->
                    "{\"id\":\""
                        + esc(f.id())
                        + "\",\"title\":\""
                        + esc(f.title())
                        + "\",\"kind\":\""
                        + f.kind()
                        + "\",\"edits\":["
                        + f.edits().stream()
                            .map(
                                e ->
                                    "{\"source\":{\"uri\":\""
                                        + esc(e.source().workspaceRelativeUri())
                                        + "\",\"sha256\":\""
                                        + e.source().contentSha256()
                                        + "\"},\"range\":{\"unit\":\"RAW_UTF16\",\"start\":"
                                        + e.range().startOffset()
                                        + ",\"end\":"
                                        + e.range().endOffset()
                                        + "},\"replacement\":\""
                                        + esc(e.replacement())
                                        + "\"}")
                            .collect(java.util.stream.Collectors.joining(","))
                        + "]}")
            .collect(java.util.stream.Collectors.joining(",", "[", "]"));
    String data =
        d.data().entrySet().stream()
            .map(e -> "\"" + esc(e.getKey()) + "\":\"" + esc(e.getValue()) + "\"")
            .collect(java.util.stream.Collectors.joining(",", "{", "}"));
    return new Metadata(related, fixes, data);
  }

  private static String esc(String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
  }
}
