package org.javelle.compiler.core.sourcemap;

import java.util.*;

public final class SourceMap {
  private final List<SourceMapSegment> segments;
  private final Map<String, IntervalNode> indexes;
  private final Map<String, OriginalIntervalNode> reverseIndexes;
  private final int schemaVersion;
  private final Map<String, String> generatedFingerprints;

  public SourceMap(Collection<SourceMapSegment> s) {
    this(1, Map.of(), s);
  }

  public SourceMap(
      int schemaVersion,
      Map<String, String> generatedFingerprints,
      Collection<SourceMapSegment> s) {
    if (schemaVersion != 1) throw new IllegalArgumentException("unsupported source-map schema");
    this.schemaVersion = schemaVersion;
    this.generatedFingerprints = Map.copyOf(generatedFingerprints);
    this.generatedFingerprints.forEach(
        (uri, hash) -> {
          new org.javelle.compiler.core.source.SourceId(uri, hash);
        });
    segments =
        s.stream()
            .sorted(
                Comparator.comparingInt(SourceMapSegment::priority)
                    .thenComparing(x -> x.kind().ordinal())
                    .thenComparingInt(x -> x.generated().range().length())
                    .thenComparing(x -> x.node().toString()))
            .toList();
    var grouped = new HashMap<String, List<SourceMapSegment>>();
    for (var segment : segments)
      grouped
          .computeIfAbsent(segment.generated().generatedRelativeUri(), ignored -> new ArrayList<>())
          .add(segment);
    var built = new HashMap<String, IntervalNode>();
    grouped.forEach((uri, values) -> built.put(uri, IntervalNode.build(values)));
    indexes = Map.copyOf(built);
    var reverse = new HashMap<String, List<OriginalEntry>>();
    for (var segment : segments)
      for (var original : segment.originals())
        reverse
            .computeIfAbsent(original.source().workspaceRelativeUri(), ignored -> new ArrayList<>())
            .add(new OriginalEntry(original, segment));
    var reverseBuilt = new HashMap<String, OriginalIntervalNode>();
    reverse.forEach((uri, values) -> reverseBuilt.put(uri, OriginalIntervalNode.build(values)));
    reverseIndexes = Map.copyOf(reverseBuilt);
  }

  public List<SourceMapSegment> generatedAt(String uri, int offset) {
    var found = new ArrayList<SourceMapSegment>();
    var index = indexes.get(uri);
    if (index != null) index.point(offset, found);
    found.sort(order());
    return List.copyOf(found);
  }

  public List<SourceMapSegment> generatedOverlapping(GeneratedRange r) {
    var found = new ArrayList<SourceMapSegment>();
    var index = indexes.get(r.generatedRelativeUri());
    if (index != null) index.overlap(r.range().startOffset(), r.range().endOffset(), found);
    found.sort(order());
    return List.copyOf(found);
  }

  public List<SourceMapSegment> originalAt(SourceLocation l) {
    var found = new ArrayList<SourceMapSegment>();
    var index = reverseIndexes.get(l.source().workspaceRelativeUri());
    if (index != null) index.overlap(l, found);
    return found.stream().distinct().sorted(order()).toList();
  }

  public List<SourceMapSegment> segments() {
    return segments;
  }

  public int schemaVersion() {
    return schemaVersion;
  }

  public Map<String, String> generatedFingerprints() {
    return generatedFingerprints;
  }

  public SourceMap compose(SourceMap next) {
    if (schemaVersion != next.schemaVersion)
      throw new IllegalArgumentException("source-map schema mismatch");
    var composed = new ArrayList<SourceMapSegment>();
    for (var later : next.segments) {
      var origins = new LinkedHashSet<SourceLocation>();
      MappingKind kind = later.kind();
      for (var intermediate : later.originals()) {
        String expected = generatedFingerprints.get(intermediate.source().workspaceRelativeUri());
        if (expected != null && !expected.equals(intermediate.source().contentSha256()))
          throw new IllegalArgumentException("source-map intermediate fingerprint mismatch");
        var hits =
            generatedOverlapping(
                new GeneratedRange(
                    intermediate.source().workspaceRelativeUri(), intermediate.range()));
        for (var hit : hits) {
          origins.addAll(hit.originals());
          kind = conservative(kind, hit.kind());
        }
      }
      if (origins.isEmpty() && kind != MappingKind.SYNTHETIC)
        throw new IllegalArgumentException("source-map composition has no intermediate mapping");
      composed.add(
          new SourceMapSegment(
              later.generated(),
              List.copyOf(origins),
              kind,
              later.node(),
              later.memberOrigin(),
              later.priority(),
              later.reason().isBlank() ? "composed" : later.reason()));
    }
    return new SourceMap(schemaVersion, next.generatedFingerprints, composed);
  }

  private static MappingKind conservative(MappingKind a, MappingKind b) {
    if (a == MappingKind.SYNTHETIC || b == MappingKind.SYNTHETIC) return MappingKind.SYNTHETIC;
    if (a == MappingKind.EXPANDED || b == MappingKind.EXPANDED) return MappingKind.EXPANDED;
    if (a == MappingKind.RELATED || b == MappingKind.RELATED) return MappingKind.RELATED;
    return MappingKind.DIRECT;
  }

  private static Comparator<SourceMapSegment> order() {
    return Comparator.comparingInt(SourceMapSegment::priority)
        .thenComparing(x -> x.kind().ordinal())
        .thenComparingInt(x -> x.generated().range().length())
        .thenComparing(x -> x.node().toString());
  }

  private static final class IntervalNode {
    final int center;
    final List<SourceMapSegment> crossing;
    final IntervalNode left, right;

    IntervalNode(int c, List<SourceMapSegment> x, IntervalNode l, IntervalNode r) {
      center = c;
      crossing = x;
      left = l;
      right = r;
    }

    static IntervalNode build(List<SourceMapSegment> s) {
      if (s.isEmpty()) return null;
      int center =
          s.stream()
              .mapToInt(x -> x.generated().range().startOffset())
              .sorted()
              .skip(s.size() / 2)
              .findFirst()
              .orElseThrow();
      var left = new ArrayList<SourceMapSegment>();
      var right = new ArrayList<SourceMapSegment>();
      var cross = new ArrayList<SourceMapSegment>();
      for (var x : s) {
        var r = x.generated().range();
        if (r.endOffset() < center) left.add(x);
        else if (r.startOffset() > center) right.add(x);
        else cross.add(x);
      }
      return new IntervalNode(center, List.copyOf(cross), build(left), build(right));
    }

    void point(int p, List<SourceMapSegment> out) {
      for (var x : crossing) {
        var r = x.generated().range();
        if (r.contains(p) || r.length() == 0 && r.startOffset() == p) out.add(x);
      }
      if (p < center && left != null) left.point(p, out);
      else if (p > center && right != null) right.point(p, out);
    }

    void overlap(int a, int b, List<SourceMapSegment> out) {
      for (var x : crossing) {
        var r = x.generated().range();
        if (r.startOffset() < b && a < r.endOffset()) out.add(x);
      }
      if (a < center && left != null) left.overlap(a, b, out);
      if (b > center && right != null) right.overlap(a, b, out);
    }
  }

  private record OriginalEntry(SourceLocation location, SourceMapSegment segment) {}

  private static final class OriginalIntervalNode {
    final int center;
    final List<OriginalEntry> crossing;
    final OriginalIntervalNode left, right;

    OriginalIntervalNode(
        int center,
        List<OriginalEntry> crossing,
        OriginalIntervalNode left,
        OriginalIntervalNode right) {
      this.center = center;
      this.crossing = crossing;
      this.left = left;
      this.right = right;
    }

    static OriginalIntervalNode build(List<OriginalEntry> entries) {
      if (entries.isEmpty()) return null;
      int center =
          entries.stream()
              .mapToInt(x -> x.location().range().startOffset())
              .sorted()
              .skip(entries.size() / 2)
              .findFirst()
              .orElseThrow();
      var left = new ArrayList<OriginalEntry>();
      var right = new ArrayList<OriginalEntry>();
      var crossing = new ArrayList<OriginalEntry>();
      for (var entry : entries) {
        var range = entry.location().range();
        if (range.endOffset() < center) left.add(entry);
        else if (range.startOffset() > center) right.add(entry);
        else crossing.add(entry);
      }
      return new OriginalIntervalNode(center, List.copyOf(crossing), build(left), build(right));
    }

    void overlap(SourceLocation query, List<SourceMapSegment> out) {
      int start = query.range().startOffset(), end = query.range().endOffset();
      for (var entry : crossing) {
        var location = entry.location();
        var range = location.range();
        if (location.source().equals(query.source())
            && (range.intersects(query.range())
                || range.length() == 0 && start == end && range.startOffset() == start))
          out.add(entry.segment());
      }
      if (start <= center && left != null) left.overlap(query, out);
      if (end >= center && right != null) right.overlap(query, out);
    }
  }
}
