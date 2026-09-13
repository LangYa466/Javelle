/* SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0 */
package dev.teyru.language.server;

import java.io.*;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import dev.teyru.language.protocol.Json;
import dev.teyru.language.tooling.DocumentWorkspace;
import dev.teyru.workspace.model.*;

public final class TeyruLanguageServerMain {
  private final DocumentWorkspace docs = new DocumentWorkspace();
  private final ExecutorService worker = Executors.newSingleThreadExecutor();
  private final ConcurrentMap<Object, FutureTask<Void>> pending = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, Future<?>> activeAnalysis = new ConcurrentHashMap<>();
  private final Set<Object> terminal = ConcurrentHashMap.newKeySet();
  private final OutputStream out;
  private State state = State.PRE_INITIALIZE;

  private enum State {
    PRE_INITIALIZE,
    INITIALIZING,
    RUNNING,
    SHUTDOWN
  }

  private TeyruLanguageServerMain(OutputStream out) {
    this.out = out;
  }

  public static void main(String[] args) throws Exception {
    if (args.length != 1 || !args[0].equals("--stdio")) {
      System.err.println("usage: teyru-lsp --stdio");
      System.exit(2);
    }
    try {
      new TeyruLanguageServerMain(System.out).run(System.in);
    } catch (IOException e) {
      System.err.println("teyru-lsp transport error: " + e.getMessage());
      System.exit(1);
    }
  }

  private void run(InputStream in) throws Exception {
    try {
      while (true) {
        byte[] body = frame(in);
        if (body == null) break;
        Object parsed;
        try {
          parsed = Json.parse(decodeUtf8(body));
        } catch (CharacterCodingException | RuntimeException e) {
          error(null, -32700, "Parse error");
          continue;
        }
        try {
          if (!(parsed instanceof Map<?, ?> raw)) {
            error(null, -32600, "Invalid Request");
            continue;
          }
          dispatch(cast(raw));
        } catch (IllegalArgumentException | ClassCastException | NullPointerException e) {
          Object id = parsed instanceof Map<?, ?> raw ? raw.get("id") : null;
          error(id, -32602, "Invalid params");
        } catch (RuntimeException e) {
          Object id = parsed instanceof Map<?, ?> raw ? raw.get("id") : null;
          error(id, -32603, "Internal error");
        }
      }
    } finally {
      worker.shutdownNow();
      docs.close();
    }
  }

  private void dispatch(Map<String, Object> m) {
    String method = (String) m.get("method");
    Object id = m.get("id");
    Map<String, Object> p = map(m.get("params"));
    if (method == null) {
      error(id, -32600, "Invalid Request");
      return;
    }
    if (state == State.SHUTDOWN && !method.equals("exit")) {
      if (id != null) error(id, -32600, "Server has shut down");
      return;
    }
    if (!method.equals("initialize")
        && !method.equals("initialized")
        && !method.equals("exit")
        && state != State.RUNNING) {
      if (id != null) error(id, -32002, "Server not initialized");
      return;
    }
    switch (method) {
      case "initialize" -> {
        if (state != State.PRE_INITIALIZE) {
          error(id, -32600, "already initialized");
          return;
        }
        try {
          validateWorkspace(p).ifPresent(docs::configure);
        } catch (RuntimeException | IOException e) {
          error(id, -32602, "invalid workspace model");
          return;
        }
        state = State.INITIALIZING;
        respond(
            id,
            Map.of(
                "capabilities",
                capabilities(),
                "serverInfo",
                Map.of("name", "Teyru", "version", "0.1.0-SNAPSHOT")));
      }
      case "initialized" -> {
        if (state != State.INITIALIZING) throw new IllegalArgumentException();
        state = State.RUNNING;
      }
      case "shutdown" -> {
        state = State.SHUTDOWN;
        respond(id, null);
      }
      case "exit" -> {
        worker.shutdownNow();
        System.exit(state == State.SHUTDOWN ? 0 : 1);
      }
      case "textDocument/didOpen" -> {
        var d = map(p.get("textDocument"));
        publish(docs.open(str(d, "uri"), num(d, "version"), str(d, "text")));
      }
      case "textDocument/didChange" -> {
        var d = map(p.get("textDocument"));
        var cs = new ArrayList<DocumentWorkspace.Change>();
        for (Object x : list(p.get("contentChanges"))) {
          var c = map(x);
          cs.add(
              new DocumentWorkspace.Change(
                  c.containsKey("range")
                      ? Optional.of(range(map(c.get("range"))))
                      : Optional.empty(),
                  str(c, "text")));
        }
        docs.change(str(d, "uri"), num(d, "version"), cs).ifPresent(this::publish);
      }
      case "textDocument/didClose" -> {
        String uri = str(map(p.get("textDocument")), "uri");
        docs.close(uri);
        Future<?> stale = activeAnalysis.remove(uri);
        if (stale != null) stale.cancel(true);
        notify("textDocument/publishDiagnostics", Map.of("uri", uri, "diagnostics", List.of()));
      }
      case "textDocument/didSave" -> {
        // Save carries no text because the advertised save capability has includeText=false.
        map(p.get("textDocument"));
      }
      case "textDocument/completion" -> {
        var d = map(p.get("textDocument"));
        request(
            id,
            () ->
                docs.completion(str(d, "uri"), position(map(p.get("position")))).stream()
                    .map(x -> Map.of("label", x))
                    .toList());
      }
      case "textDocument/hover" -> {
        var d = map(p.get("textDocument"));
        request(
            id,
            () ->
                docs.hover(str(d, "uri"), position(map(p.get("position"))))
                    .<Object>map(x -> Map.of("contents", Map.of("kind", "markdown", "value", x)))
                    .orElse(null));
      }
      case "$/cancelRequest" -> {
        Object cancelledId = p.get("id");
        FutureTask<Void> task = pending.remove(cancelledId);
        if (task != null && task.cancel(true) && terminal.add(cancelledId))
          error(cancelledId, -32800, "Request cancelled");
      }
      default -> {
        if (id != null) error(id, -32601, "Method not found");
      }
    }
  }

  private Map<String, Object> capabilities() {
    return Map.of(
        "positionEncoding",
        "utf-16",
        "textDocumentSync",
        Map.of("openClose", true, "change", 2, "save", Map.of("includeText", false)),
        "hoverProvider",
        true,
        "completionProvider",
        Map.of("resolveProvider", false, "triggerCharacters", List.of(".")));
  }

  private static Optional<WorkspaceModel> validateWorkspace(Map<String, Object> params)
      throws IOException {
    var options = map(params.get("initializationOptions"));
    var teyru = map(options.get("teyru"));
    Object value = teyru.get("workspaceModelUri");
    if (value == null) return Optional.empty();
    var uri = URI.create(value.toString());
    if (!"file".equals(uri.getScheme()) || uri.getQuery() != null || uri.getFragment() != null)
      throw new IllegalArgumentException();
    byte[] bytes = Files.readAllBytes(Path.of(uri));
    var result = new DefaultWorkspaceModelCodec().read(bytes, WorkspaceReadOptions.defaults());
    if (!result.success()) throw new IllegalArgumentException("workspace validation failed");
    return result.model();
  }

  private static String decodeUtf8(byte[] bytes) throws CharacterCodingException {
    return StandardCharsets.UTF_8
        .newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes))
        .toString();
  }

  private void publish(DocumentWorkspace.Snapshot s) {
    Future<?> stale = activeAnalysis.remove(s.uri());
    if (stale != null) stale.cancel(true);
    Future<?> future =
        worker.submit(
            () -> {
              var found = docs.analyze(s);
              if (!docs.isCurrent(s)) return;
              var ds =
                  found.stream()
                      .map(
                          x ->
                              Map.of(
                                  "range",
                                  wire(x.range()),
                                  "severity",
                                  severity(x.severity()),
                                  "code",
                                  x.code(),
                                  "source",
                                  "teyru",
                                  "message",
                                  x.message()))
                      .toList();
              notify(
                  "textDocument/publishDiagnostics",
                  Map.of("uri", s.uri(), "version", s.version(), "diagnostics", ds));
            });
    activeAnalysis.put(s.uri(), future);
  }

  private void request(Object id, Supplier<Object> operation) {
    if (id == null) throw new IllegalArgumentException("request id required");
    var task =
        new FutureTask<Void>(
            () -> {
              try {
                Object result = operation.get();
                if (!Thread.currentThread().isInterrupted() && terminal.add(id))
                  respond(id, result);
              } catch (IllegalArgumentException | ClassCastException | NullPointerException e) {
                if (terminal.add(id)) error(id, -32602, "Invalid params");
              } catch (RuntimeException e) {
                if (terminal.add(id)) error(id, -32603, "Internal error");
              } finally {
                pending.remove(id);
              }
              return null;
            });
    if (pending.putIfAbsent(id, task) != null)
      throw new IllegalArgumentException("duplicate request id");
    worker.execute(task);
  }

  private synchronized void send(Object message) {
    try {
      byte[] b = Json.write(message).getBytes(StandardCharsets.UTF_8);
      out.write(("Content-Length: " + b.length + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
      out.write(b);
      out.flush();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private void respond(Object id, Object result) {
    var m = new LinkedHashMap<String, Object>();
    m.put("jsonrpc", "2.0");
    m.put("id", id);
    m.put("result", result);
    send(m);
  }

  private void error(Object id, int code, String msg) {
    var message = new LinkedHashMap<String, Object>();
    message.put("jsonrpc", "2.0");
    message.put("id", id);
    message.put("error", Map.of("code", code, "message", msg));
    send(message);
  }

  private void notify(String method, Object params) {
    send(Map.of("jsonrpc", "2.0", "method", method, "params", params));
  }

  private static byte[] frame(InputStream in) throws IOException {
    var h = new ByteArrayOutputStream();
    int state = 0, c;
    while ((c = in.read()) != -1) {
      h.write(c);
      if (h.size() > 8192) throw new IOException("header too large");
      state =
          (state == 0 && c == '\r')
              ? 1
              : (state == 1 && c == '\n')
                  ? 2
                  : (state == 2 && c == '\r') ? 3 : (state == 3 && c == '\n') ? 4 : 0;
      if (state == 4) break;
    }
    if (c == -1) {
      if (h.size() == 0) return null;
      throw new EOFException("partial header");
    }
    String head = h.toString(StandardCharsets.US_ASCII);
    long length = -1;
    for (String line : head.substring(0, head.length() - 4).split("\r\n")) {
      int at = line.indexOf(':');
      if (at < 1) throw new IOException("bad header");
      if (line.substring(0, at).equalsIgnoreCase("Content-Length")) {
        if (length != -1) throw new IOException("duplicate length");
        try {
          length = Long.parseLong(line.substring(at + 1).trim());
        } catch (NumberFormatException e) {
          throw new IOException("bad length");
        }
      }
    }
    if (length < 0 || length > 16_777_216) throw new IOException("invalid length");
    return readExactly(in, (int) length);
  }

  private static byte[] readExactly(InputStream in, int n) throws IOException {
    byte[] b = in.readNBytes(n);
    if (b.length != n) throw new EOFException("partial body");
    return b;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> cast(Map<?, ?> m) {
    return (Map<String, Object>) m;
  }

  private static Map<String, Object> map(Object o) {
    return o instanceof Map<?, ?> m ? cast(m) : Map.of();
  }

  @SuppressWarnings("unchecked")
  private static List<Object> list(Object o) {
    return (List<Object>) o;
  }

  private static String str(Map<String, Object> m, String k) {
    return (String) m.get(k);
  }

  private static int num(Map<String, Object> m, String k) {
    return ((Number) m.get(k)).intValue();
  }

  private static DocumentWorkspace.Position position(Map<String, Object> p) {
    return new DocumentWorkspace.Position(num(p, "line"), num(p, "character"));
  }

  private static DocumentWorkspace.Range range(Map<String, Object> r) {
    return new DocumentWorkspace.Range(position(map(r.get("start"))), position(map(r.get("end"))));
  }

  private static Map<String, Object> wire(DocumentWorkspace.Range r) {
    return Map.of(
        "start",
        Map.of("line", r.start().line(), "character", r.start().character()),
        "end",
        Map.of("line", r.end().line(), "character", r.end().character()));
  }

  private static int severity(String s) {
    return switch (s) {
      case "ERROR" -> 1;
      case "WARNING" -> 2;
      case "INFORMATION" -> 3;
      default -> 4;
    };
  }
}
