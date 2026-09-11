package org.javelle.compiler.core;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.javelle.compiler.core.analysis.SnapshotPublisher;
import org.javelle.compiler.core.budget.*;
import org.javelle.compiler.core.diagnostic.*;
import org.javelle.compiler.core.lowering.*;
import org.javelle.compiler.core.source.*;
import org.javelle.compiler.core.sourcemap.SourceMap;
import org.junit.jupiter.api.Test;

class DiagnosticGenerationBudgetRepairTest {
  private static final SourceId SOURCE = new SourceId("a.javelle", "0".repeat(64));

  private static TextRange range(int start, int end) {
    return new TextRange(OffsetUnit.RAW_UTF16, start, end);
  }

  @Test
  void legalReverseOrderedFixIsCanonicalizedAndKindRoundTrips() {
    var late = new TextEdit(SOURCE, range(4, 5), "z");
    var early = new TextEdit(SOURCE, range(1, 2), "a");
    var fix = new Fix("id", "title", FixKind.REFACTOR, List.of(late, early));
    assertEquals(List.of(early, late), fix.edits());
    var d =
        new Diagnostic(
            1,
            new DiagnosticCode("JVL-TEST"),
            Severity.ERROR,
            SOURCE,
            range(0, 5),
            "m",
            List.of(),
            List.of(fix),
            Map.of());
    assertEquals(d, DiagnosticJson.read(DiagnosticJson.write(d), 10_000, 20, Map.of(SOURCE, 5)));
  }

  @Test
  void overlapAndEveryFingerprintBoundRangeAreRejected() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new Fix(
                "id",
                "title",
                List.of(
                    new TextEdit(SOURCE, range(2, 4), "x"),
                    new TextEdit(SOURCE, range(3, 5), "y"))));
    var d =
        new Diagnostic(
            1,
            new DiagnosticCode("JVL-TEST"),
            Severity.ERROR,
            SOURCE,
            range(0, 5),
            "m",
            List.of(),
            List.of(),
            Map.of());
    assertThrows(
        IllegalArgumentException.class,
        () -> DiagnosticJson.read(DiagnosticJson.write(d), 10_000, 20, Map.of(SOURCE, 4)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            DiagnosticJson.read(
                DiagnosticJson.write(d),
                10_000,
                20,
                Map.of(new SourceId("a.javelle", "1".repeat(64)), 5)));
  }

  @Test
  void generatedHashPathCollisionAndAtomicPublicationAreEnforced() {
    var map = new SourceMap(List.of());
    var good = GeneratedFile.create(SOURCE, "p/A.java", "class A {}\n", map);
    assertThrows(
        IllegalArgumentException.class,
        () -> new GeneratedFile(SOURCE, "p/A.java", "class A {}\n", map, "0".repeat(64)));
    assertThrows(
        IllegalArgumentException.class,
        () -> GeneratedFile.create(SOURCE, "../A.java", "class A {}\n", map));
    var publisher = new GeneratedFilePublisher();
    assertEquals(List.of("p/A.java"), new ArrayList<>(publisher.publish(List.of(good)).keySet()));
    var conflicting = GeneratedFile.create(SOURCE, "p/A.java", "class B {}\n", map);
    assertThrows(
        IllegalArgumentException.class, () -> publisher.publish(List.of(good, conflicting)));
    assertSame(good, publisher.current().get("p/A.java"));
  }

  @Test
  void allCountersAreCumulativeAndOverflowFailsClosed() {
    var tracker =
        new ResourceTracker(
            new ResourceBudget(5, 2, 2, 2, 2, Long.MAX_VALUE), CancellationToken.none(), () -> 0);
    tracker.addBytes(3);
    assertThrows(ResourceLimitException.class, () -> tracker.addBytes(3));
    assertThrows(IllegalArgumentException.class, () -> tracker.addBytes(-1));
    tracker.token();
    tracker.token();
    assertThrows(ResourceLimitException.class, tracker::token);
    tracker.node();
    tracker.node();
    assertThrows(ResourceLimitException.class, tracker::node);
    tracker.diagnostic();
    tracker.diagnostic();
    assertThrows(ResourceLimitException.class, tracker::diagnostic);
    tracker.nesting(2);
    assertThrows(ResourceLimitException.class, () -> tracker.nesting(3));
    assertThrows(IllegalArgumentException.class, () -> tracker.nesting(-1));
  }

  @Test
  void deadlineCancellationAndFailedAnalysisNeverPublishPartialState() {
    var expired =
        new ResourceTracker(
            new ResourceBudget(1, 1, 1, 1, 1, 5), CancellationToken.none(), () -> 6);
    assertThrows(ResourceLimitException.class, expired::checkpoint);
    var publisher = new SnapshotPublisher();
    assertThrows(
        IllegalStateException.class,
        () ->
            publisher.compute(
                () -> {
                  throw new IllegalStateException("failed");
                },
                CancellationToken.none()));
    assertTrue(publisher.current().isEmpty());
  }

  @Test
  void jsonCumulativeLimitsDuplicateKeysTypesAndUnknownFieldsAreStrict() {
    var d =
        new Diagnostic(
            1,
            new DiagnosticCode("JVL-TEST"),
            Severity.ERROR,
            SOURCE,
            range(0, 1),
            "m",
            List.of(),
            List.of(),
            Map.of());
    byte[] valid = DiagnosticJson.write(d);
    assertThrows(
        IllegalArgumentException.class,
        () -> DiagnosticJson.read(valid, valid.length - 1, 20, Map.of(SOURCE, 1)));
    String duplicate =
        new String(valid, StandardCharsets.UTF_8)
            .replace("\"schemaVersion\":1", "\"schemaVersion\":1,\"schemaVersion\":1");
    assertThrows(
        IllegalArgumentException.class,
        () ->
            DiagnosticJson.read(
                duplicate.getBytes(StandardCharsets.UTF_8), 10_000, 20, Map.of(SOURCE, 1)));
    String wrongType =
        new String(valid, StandardCharsets.UTF_8).replace("\"code\":\"JVL-TEST\"", "\"code\":1");
    assertThrows(
        IllegalArgumentException.class,
        () ->
            DiagnosticJson.read(
                wrongType.getBytes(StandardCharsets.UTF_8), 10_000, 20, Map.of(SOURCE, 1)));
    String unknownDeep =
        new String(valid, StandardCharsets.UTF_8).replaceFirst("\\}$", ",\"future\":[[[[0]]]]}");
    assertThrows(
        IllegalArgumentException.class,
        () ->
            DiagnosticJson.read(
                unknownDeep.getBytes(StandardCharsets.UTF_8), 10_000, 2, Map.of(SOURCE, 1)));
  }
}
