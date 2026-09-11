/* SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0 */
package org.javelle.intellij.plugin;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.javelle.language.protocol.Json;
import org.junit.jupiter.api.Test;

/**
 * Proves the launcher script bundled inside the real packaged plugin ZIP starts the real P10
 * server, not a stub. No IntelliJ platform runtime is needed for this: it exercises the packaged
 * artifact as an external process, the same way P10's own black-box suite does.
 */
class P11BundledLspLauncherTest {
  @Test
  void bundledLauncherInsidePluginZipStartsRealServer() throws Exception {
    Path launcher = extractBundledLauncher();
    assertTrue(Files.isExecutable(launcher), "bundled launcher must be executable: " + launcher);

    var process = new ProcessBuilder(launcher.toString(), "--stdio").start();
    try {
      write(process, message("initialize", Map.of()));
      var init = read(process.getInputStream());
      assertTrue(Json.write(init).contains("\"capabilities\""), Json.write(init));

      write(process, message("initialized", Map.of()));
      String uri = "file:///workspace/P11.javelle";
      write(
          process,
          message(
              "textDocument/didOpen",
              Map.of(
                  "textDocument",
                  Map.of("uri", uri, "languageId", "javelle", "version", 1, "text", "class A {"))));

      Map<String, Object> diagnostics = null;
      for (int i = 0; i < 5 && diagnostics == null; i++) {
        var next = read(process.getInputStream());
        if ("textDocument/publishDiagnostics".equals(next.get("method"))) diagnostics = next;
      }
      assertNotNull(diagnostics, "expected a real publishDiagnostics notification");
    } finally {
      process.destroyForcibly();
    }
  }

  private static Path extractBundledLauncher() throws IOException {
    Path zip = Path.of(System.getProperty("javelleIntellijPluginZip"));
    Path dest = Files.createTempDirectory("p11-plugin-zip-");
    try (var in = new ZipInputStream(Files.newInputStream(zip))) {
      ZipEntry entry;
      while ((entry = in.getNextEntry()) != null) {
        Path target = dest.resolve(entry.getName());
        if (entry.isDirectory()) {
          Files.createDirectories(target);
        } else {
          Files.createDirectories(target.getParent());
          Files.copy(in, target);
          if (entry.getName().endsWith("/bin/javelle-lsp")
              || entry.getName().endsWith("/bin/javelle-lsp.bat"))
            target.toFile().setExecutable(true);
        }
      }
    }
    try (var found = Files.walk(dest)) {
      return found
          .filter(
              p ->
                  p.getFileName().toString().equals("javelle-lsp")
                      && p.getParent().getFileName().toString().equals("bin"))
          .findFirst()
          .orElseThrow(() -> new AssertionError("javelle-lsp launcher not found in plugin ZIP"));
    }
  }

  private static String message(String method, Object params) {
    return "{\"jsonrpc\":\"2.0\",\"method\":"
        + Json.write(method)
        + ",\"params\":"
        + Json.write(params)
        + "}";
  }

  private static void write(Process process, String json) throws IOException {
    byte[] body = json.getBytes(StandardCharsets.UTF_8);
    process
        .getOutputStream()
        .write(("Content-Length: " + body.length + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
    process.getOutputStream().write(body);
    process.getOutputStream().flush();
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> read(InputStream in) throws Exception {
    return CompletableFuture.supplyAsync(
            () -> {
              try {
                var h = new ByteArrayOutputStream();
                int state = 0, c;
                while ((c = in.read()) >= 0) {
                  h.write(c);
                  state =
                      (state == 0 && c == '\r')
                          ? 1
                          : (state == 1 && c == '\n')
                              ? 2
                              : (state == 2 && c == '\r') ? 3 : (state == 3 && c == '\n') ? 4 : 0;
                  if (state == 4) break;
                }
                String head = h.toString(StandardCharsets.US_ASCII);
                int n = Integer.parseInt(head.split(":")[1].trim());
                return (Map<String, Object>)
                    Json.parse(new String(in.readNBytes(n), StandardCharsets.UTF_8));
              } catch (Exception e) {
                throw new CompletionException(e);
              }
            })
        .get(20, TimeUnit.SECONDS);
  }
}
