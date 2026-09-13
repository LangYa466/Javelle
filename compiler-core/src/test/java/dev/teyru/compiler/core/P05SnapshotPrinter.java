package dev.teyru.compiler.core;

import java.nio.charset.StandardCharsets;
import java.util.*;
import dev.teyru.compiler.core.budget.*;
import dev.teyru.compiler.core.frontend.*;
import dev.teyru.compiler.core.source.*;

public final class P05SnapshotPrinter {
  private P05SnapshotPrinter() {}

  public static void main(String[] args) throws Exception {
    byte[] json =
        Objects.requireNonNull(P05SnapshotPrinter.class.getResourceAsStream("/p05/cases.json"))
            .readAllBytes();
    Class<?> parser = Class.forName("dev.teyru.compiler.core.diagnostic.JsonParser");
    var constructor = parser.getDeclaredConstructor(byte[].class, int.class, int.class);
    constructor.setAccessible(true);
    Object instance = constructor.newInstance(json, json.length + 1, 50);
    var method = parser.getDeclaredMethod("parse");
    method.setAccessible(true);
    @SuppressWarnings("unchecked")
    var root = (Map<String, Object>) method.invoke(instance);
    @SuppressWarnings("unchecked")
    var cases = (List<Map<String, Object>>) root.get("cases");
    for (var fixture : cases) {
      String source = (String) fixture.get("source");
      byte[] bytes = source.getBytes(StandardCharsets.UTF_8);
      var file =
          SourceFile.decode(
              SourceId.forContent("case.teyru", bytes),
              bytes,
              new ResourceBudget(1_000_000, 10_000, 10_000, 100, 100, Long.MAX_VALUE));
      var result =
          new TeyruFrontend()
              .parse(
                  file,
                  new FrontendOptions(1, 10),
                  new ResourceBudget(1_000_000, 10_000, 10_000, 100, 100, Long.MAX_VALUE),
                  CancellationToken.none());
      System.out.println("=== " + fixture.get("id") + " ===");
      System.out.print(FrontendSnapshot.canonical(result));
    }
  }
}
