/* SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0 */
package org.javelle.language.server;

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import org.javelle.language.protocol.Json;
import org.junit.jupiter.api.Test;

class P10BlackBoxTest {
  @Test
  void negativeContentLengthTerminatesBoundedly() throws Exception {
    assertBadHeader("Content-Length: -1\r\n\r\n");
  }

  @Test
  void nonDecimalContentLengthTerminatesBoundedly() throws Exception {
    assertBadHeader("Content-Length: nope\r\n\r\n");
  }

  @Test
  void invalidParamsAreNotReportedAsParseErrors() throws Exception {
    var p = launch();
    try {
      initialize(p);
      p.getOutputStream().write(frame(request(12, "textDocument/hover", Map.of())));
      p.getOutputStream().flush();
      assertEquals(-32602L, map(read(p.getInputStream()).get("error")).get("code"));
    } finally {
      p.destroyForcibly();
    }
  }

  @Test
  void unknownRequestErrorsAndUnknownNotificationIsIgnored() throws Exception {
    var p = launch();
    try {
      initialize(p);
      p.getOutputStream()
          .write(
              concat(
                  frame(message("unknown/notification", Map.of())),
                  frame(request(13, "unknown/request", Map.of()))));
      p.getOutputStream().flush();
      assertEquals(-32601L, map(read(p.getInputStream()).get("error")).get("code"));
    } finally {
      p.destroyForcibly();
    }
  }

  @Test
  void requestAfterShutdownIsInvalidRequest() throws Exception {
    var p = launch();
    try {
      initialize(p);
      p.getOutputStream()
          .write(
              concat(
                  frame(request(14, "shutdown", Map.of())),
                  frame(request(15, "textDocument/hover", Map.of()))));
      p.getOutputStream().flush();
      read(p.getInputStream());
      assertEquals(-32600L, map(read(p.getInputStream()).get("error")).get("code"));
    } finally {
      p.destroyForcibly();
    }
  }

  @Test
  void strictUtf8RejectsReplacementByte() throws Exception {
    var p = launch();
    try {
      byte[] prefix =
          "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"x\":\""
              .getBytes(StandardCharsets.UTF_8);
      byte[] body =
          concat(prefix, new byte[] {(byte) 0xff}, "\"}}".getBytes(StandardCharsets.UTF_8));
      p.getOutputStream()
          .write(
              concat(
                  ("Content-Length: " + body.length + "\r\n\r\n")
                      .getBytes(StandardCharsets.US_ASCII),
                  body));
      p.getOutputStream().flush();
      assertEquals(-32700L, map(read(p.getInputStream()).get("error")).get("code"));
    } finally {
      p.destroyForcibly();
    }
  }

  @Test
  void requestBetweenInitializeAndInitializedIsRejected() throws Exception {
    var p = launch();
    try {
      p.getOutputStream()
          .write(
              concat(
                  frame(request(1, "initialize", Map.of())),
                  frame(request(2, "textDocument/hover", Map.of()))));
      p.getOutputStream().flush();
      read(p.getInputStream());
      assertEquals(-32002L, map(read(p.getInputStream()).get("error")).get("code"));
    } finally {
      p.destroyForcibly();
    }
  }

  @Test
  void syntaxDiagnosticAtEmojiCrLfRangeIsClearedByFixedNewerVersion() throws Exception {
    var p = launch();
    String uri = "file:///workspace/Fix.javelle";
    try {
      initialize(p);
      String bad = "class A {\r\n  String s = \"😀\";\r\n}\r\n";
      p.getOutputStream()
          .write(
              frame(
                  message(
                      "textDocument/didOpen",
                      Map.of(
                          "textDocument",
                          Map.of(
                              "uri", uri, "languageId", "javelle", "version", 1, "text", bad)))));
      p.getOutputStream().flush();
      Map<String, Object> first = nextDiagnostics(p);
      var params = map(first.get("params"));
      assertEquals(uri, params.get("uri"));
      assertEquals(1L, params.get("version"));
      assertFalse(list(params.get("diagnostics")).isEmpty());
      var diagnostic = map(list(params.get("diagnostics")).getFirst());
      var diagnosticRange = map(diagnostic.get("range"));
      assertEquals(Map.of("line", 1L, "character", 17L), map(diagnosticRange.get("start")));
      assertEquals(Map.of("line", 1L, "character", 18L), map(diagnosticRange.get("end")));
      p.getOutputStream()
          .write(
              frame(
                  message(
                      "textDocument/didChange",
                      Map.of(
                          "textDocument",
                          Map.of("uri", uri, "version", 2),
                          "contentChanges",
                          List.of(Map.of("text", "class A {\r\n}\r\n"))))));
      p.getOutputStream().flush();
      Map<String, Object> fixed = nextDiagnostics(p);
      var fp = map(fixed.get("params"));
      assertEquals(2L, fp.get("version"));
      assertTrue(list(fp.get("diagnostics")).isEmpty());
    } finally {
      p.destroyForcibly();
    }
  }

  @Test
  void cancelledOldAnalysisCannotPublishAndNewerDiagnosticWinsOnce() throws Exception {
    Path marker = Files.createTempDirectory("p10-running-cancel-").resolve("entered");
    var builder =
        new ProcessBuilder(Path.of(System.getProperty("javelleLsp")).toString(), "--stdio");
    builder.environment().put("JAVELLE_LSP_TEST_ANALYSIS_MARKER", marker.toString());
    builder.environment().put("JAVELLE_LSP_TEST_ANALYSIS_CHECKPOINT", "3");
    var p = builder.start();
    String uri = "file:///workspace/Cancel.javelle";
    try {
      initialize(p);
      p.getOutputStream()
          .write(
              frame(
                  message(
                      "textDocument/didOpen",
                      Map.of(
                          "textDocument",
                          Map.of(
                              "uri",
                              uri,
                              "languageId",
                              "javelle",
                              "version",
                              1,
                              "text",
                              "class A {\r\n String s\r\n}\r\n")))));
      p.getOutputStream().flush();
      assertEquals(1L, map(nextDiagnostics(p).get("params")).get("version"));
      p.getOutputStream()
          .write(
              concat(
                  frame(
                      message(
                          "textDocument/didChange",
                          Map.of(
                              "textDocument", Map.of("uri", uri, "version", 2),
                              "contentChanges", List.of(Map.of("text", "class A { String s }"))))),
                  frame(
                      request(
                          8,
                          "textDocument/hover",
                          Map.of(
                              "textDocument",
                              Map.of("uri", uri),
                              "position",
                              Map.of("line", 1, "character", 2)))),
                  new byte[0]));
      p.getOutputStream().flush();
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
      while (!Files.exists(marker) && System.nanoTime() < deadline) Thread.sleep(10);
      assertTrue(Files.exists(marker), "hover analysis must enter the running compiler stage");
      p.getOutputStream()
          .write(
              concat(
                  frame(message("$/cancelRequest", Map.of("id", 8))),
                  frame(
                      message(
                          "textDocument/didChange",
                          Map.of(
                              "textDocument",
                              Map.of("uri", uri, "version", 3),
                              "contentChanges",
                              List.of(Map.of("text", "class A {;\r\n}\r\n")))))));
      p.getOutputStream().flush();
      boolean cancelled = false, newer = false;
      while (!cancelled || !newer) {
        var x = read(p.getInputStream());
        if (Objects.equals(x.get("id"), 8L)) {
          assertEquals(-32800L, map(x.get("error")).get("code"));
          assertFalse(cancelled, "cancel response must be terminal and unique");
          cancelled = true;
        } else if (Objects.equals(x.get("method"), "textDocument/publishDiagnostics")) {
          var params = map(x.get("params"));
          assertEquals(3L, params.get("version"), "stale version must never publish");
          assertFalse(list(params.get("diagnostics")).isEmpty());
          assertFalse(newer, "newer snapshot publishes once");
          newer = true;
        }
      }
    } finally {
      p.destroyForcibly();
    }
  }

  @Test
  void fragmentedCoalescedUtf16StaleClearHoverCompletionShutdownAndStdoutPurity() throws Exception {
    var process =
        new ProcessBuilder(Path.of(System.getProperty("javelleLsp")).toString(), "--stdio").start();
    try {
      byte[] initialize =
          frame("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}");
      for (byte b : initialize) {
        process.getOutputStream().write(b);
        process.getOutputStream().flush();
      }
      var init = read(process.getInputStream());
      var caps = map(map(init.get("result")).get("capabilities"));
      assertEquals("utf-16", caps.get("positionEncoding"));
      assertEquals(Boolean.TRUE, caps.get("hoverProvider"));
      assertFalse(caps.containsKey("definitionProvider"));
      assertFalse(caps.containsKey("diagnosticProvider"));
      process.getOutputStream().write(frame(message("initialized", Map.of())));
      process.getOutputStream().flush();

      String uri = "file:///workspace/Emoji.javelle";
      String opened =
          message(
              "textDocument/didOpen",
              Map.of(
                  "textDocument",
                  Map.of(
                      "uri",
                      uri,
                      "languageId",
                      "javelle",
                      "version",
                      1,
                      "text",
                      "class A {\r\n  String s = \"😀\"\r\n}\r\n")));
      String hover =
          request(
              2,
              "textDocument/hover",
              Map.of(
                  "textDocument",
                  Map.of("uri", uri),
                  "position",
                  Map.of("line", 1, "character", 3)));
      process.getOutputStream().write(concat(frame(opened), frame(hover)));
      process.getOutputStream().flush();
      Map<String, Object> a = read(process.getInputStream()), b = read(process.getInputStream());
      Map<String, Object> hoverResponse = a.containsKey("id") ? a : b;
      assertEquals(2L, hoverResponse.get("id"));
      assertTrue(Json.write(hoverResponse).contains("java.lang.String"));

      process
          .getOutputStream()
          .write(
              frame(
                  request(
                      3,
                      "textDocument/completion",
                      Map.of(
                          "textDocument",
                          Map.of("uri", uri),
                          "position",
                          Map.of("line", 1, "character", 2)))));
      process.getOutputStream().flush();
      while (true) {
        var x = read(process.getInputStream());
        if (Objects.equals(x.get("id"), 3L)) {
          assertTrue(Json.write(x).contains("String"));
          break;
        }
      }

      String stale =
          message(
              "textDocument/didChange",
              Map.of(
                  "textDocument",
                  Map.of("uri", uri, "version", 1),
                  "contentChanges",
                  List.of(Map.of("text", "stale"))));
      String close = message("textDocument/didClose", Map.of("textDocument", Map.of("uri", uri)));
      process
          .getOutputStream()
          .write(concat(frame(stale), frame(close), frame(request(4, "shutdown", Map.of()))));
      process.getOutputStream().flush();
      boolean cleared = false, shutdown = false;
      for (int i = 0; i < 5 && !shutdown; i++) {
        var x = read(process.getInputStream());
        String wire = Json.write(x);
        cleared |= wire.contains("publishDiagnostics") && wire.contains("\"diagnostics\":[]");
        shutdown = Objects.equals(x.get("id"), 4L);
      }
      assertTrue(cleared);
      assertTrue(shutdown);
      process.getOutputStream().write(frame(message("exit", Map.of())));
      process.getOutputStream().flush();
      assertTrue(process.waitFor(10, TimeUnit.SECONDS));
      assertEquals(0, process.exitValue());
      assertFalse(
          new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8)
              .contains("Exception"));
    } finally {
      process.destroyForcibly();
    }
  }

  @Test
  void malformedJsonGetsFramedParseErrorAndEarlyExitIsOne() throws Exception {
    var process =
        new ProcessBuilder(Path.of(System.getProperty("javelleLsp")).toString(), "--stdio").start();
    try {
      process.getOutputStream().write(frame("{"));
      process.getOutputStream().write(frame(message("exit", Map.of())));
      process.getOutputStream().flush();
      assertEquals(-32700L, map(read(process.getInputStream()).get("error")).get("code"));
      assertTrue(process.waitFor(10, TimeUnit.SECONDS));
      assertEquals(1, process.exitValue());
    } finally {
      process.destroyForcibly();
    }
  }

  @Test
  void malformedHeadersPartialEofAndRepeatedRestartsTerminateWithoutProtocolNoise()
      throws Exception {
    for (byte[] bad :
        List.of(
            "X: 1\r\n\r\n{}".getBytes(StandardCharsets.US_ASCII),
            "Content-Length: 2\r\nContent-Length: 2\r\n\r\n{}".getBytes(StandardCharsets.US_ASCII),
            "Content-Length: 999999999\r\n\r\n".getBytes(StandardCharsets.US_ASCII),
            "Content-Length: 10\r\n\r\n{}".getBytes(StandardCharsets.US_ASCII))) {
      var p =
          new ProcessBuilder(Path.of(System.getProperty("javelleLsp")).toString(), "--stdio")
              .start();
      p.getOutputStream().write(bad);
      p.getOutputStream().close();
      assertTrue(p.waitFor(10, TimeUnit.SECONDS));
      assertNotEquals(0, p.exitValue());
      assertEquals(0, p.getInputStream().readAllBytes().length);
      assertTrue(
          new String(p.getErrorStream().readAllBytes(), StandardCharsets.UTF_8)
              .startsWith("javelle-lsp transport error:"));
    }
    for (int i = 0; i < 10; i++) {
      long started = System.nanoTime();
      var p =
          new ProcessBuilder(Path.of(System.getProperty("javelleLsp")).toString(), "--stdio")
              .start();
      long pid = p.pid();
      ProcessHandle handle = p.toHandle();
      p.getOutputStream()
          .write(
              concat(
                  frame(request(1, "initialize", Map.of())),
                  frame(message("initialized", Map.of())),
                  frame(request(2, "shutdown", Map.of())),
                  frame(message("exit", Map.of()))));
      p.getOutputStream().flush();
      assertTrue(p.waitFor(10, TimeUnit.SECONDS));
      assertEquals(0, p.exitValue());
      assertFalse(handle.isAlive());
      assertTrue(handle.descendants().noneMatch(ProcessHandle::isAlive));
      assertFalse(Files.exists(Path.of("/proc", Long.toString(pid), "fd")));
      assertTrue(System.nanoTime() - started < TimeUnit.SECONDS.toNanos(10));
    }
    var untrusted =
        new ProcessBuilder(Path.of(System.getProperty("javelleLsp")).toString(), "--stdio").start();
    untrusted
        .getOutputStream()
        .write(
            frame(
                request(
                    9,
                    "initialize",
                    Map.of(
                        "initializationOptions",
                        Map.of(
                            "javelle",
                            Map.of("workspaceModelUri", "https://example.invalid/model.json"))))));
    untrusted.getOutputStream().flush();
    assertEquals(-32602L, map(read(untrusted.getInputStream()).get("error")).get("code"));
    untrusted.destroyForcibly();
  }

  private static String request(int id, String method, Object params) {
    return "{\"jsonrpc\":\"2.0\",\"id\":"
        + id
        + ",\"method\":"
        + Json.write(method)
        + ",\"params\":"
        + Json.write(params)
        + "}";
  }

  private static Process launch() throws IOException {
    return new ProcessBuilder(Path.of(System.getProperty("javelleLsp")).toString(), "--stdio")
        .start();
  }

  private static void assertBadHeader(String header) throws Exception {
    var p = launch();
    try {
      p.getOutputStream().write(header.getBytes(StandardCharsets.US_ASCII));
      p.getOutputStream().close();
      assertTrue(p.waitFor(10, TimeUnit.SECONDS));
      assertNotEquals(0, p.exitValue());
      assertEquals(0, p.getInputStream().readAllBytes().length);
    } finally {
      p.destroyForcibly();
    }
  }

  private static void initialize(Process p) throws Exception {
    p.getOutputStream()
        .write(
            concat(
                frame(request(1, "initialize", Map.of())),
                frame(message("initialized", Map.of()))));
    p.getOutputStream().flush();
    read(p.getInputStream());
  }

  private static Map<String, Object> nextDiagnostics(Process p) throws Exception {
    while (true) {
      var x = read(p.getInputStream());
      if ("textDocument/publishDiagnostics".equals(x.get("method"))) return x;
    }
  }

  private static String message(String method, Object params) {
    return "{\"jsonrpc\":\"2.0\",\"method\":"
        + Json.write(method)
        + ",\"params\":"
        + Json.write(params)
        + "}";
  }

  private static byte[] frame(String json) {
    byte[] b = json.getBytes(StandardCharsets.UTF_8);
    return concat(
        ("Content-Length: " + b.length + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII), b);
  }

  private static byte[] concat(byte[]... xs) {
    var o = new ByteArrayOutputStream();
    try {
      for (byte[] x : xs) o.write(x);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return o.toByteArray();
  }

  private static Map<String, Object> read(InputStream in) throws Exception {
    return CompletableFuture.supplyAsync(
            () -> {
              try {
                var h = new ByteArrayOutputStream();
                int s = 0, c;
                while ((c = in.read()) >= 0) {
                  h.write(c);
                  s =
                      (s == 0 && c == '\r')
                          ? 1
                          : (s == 1 && c == '\n')
                              ? 2
                              : (s == 2 && c == '\r') ? 3 : (s == 3 && c == '\n') ? 4 : 0;
                  if (s == 4) break;
                }
                String head = h.toString(StandardCharsets.US_ASCII);
                int n = Integer.parseInt(head.split(":")[1].trim());
                return map(Json.parse(new String(in.readNBytes(n), StandardCharsets.UTF_8)));
              } catch (Exception e) {
                throw new CompletionException(e);
              }
            })
        .get(20, TimeUnit.SECONDS);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> map(Object o) {
    return (Map<String, Object>) o;
  }

  @SuppressWarnings("unchecked")
  private static List<Object> list(Object o) {
    return (List<Object>) o;
  }
}
