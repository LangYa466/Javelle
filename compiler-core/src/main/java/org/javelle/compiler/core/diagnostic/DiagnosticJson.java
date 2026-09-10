package org.javelle.compiler.core.diagnostic;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.javelle.compiler.core.source.*;

public final class DiagnosticJson {
  private DiagnosticJson() {}

  public static byte[] write(Diagnostic d) {
    StringBuilder b = new StringBuilder("{\"schemaVersion\":1,\"code\":");
    q(b, d.code().value());
    b.append(",\"severity\":");
    q(b, d.severity().name());
    b.append(",\"source\":");
    source(b, d.source());
    b.append(",\"range\":");
    range(b, d.range());
    b.append(",\"message\":");
    q(b, d.message());
    b.append(",\"related\":[");
    for (int i = 0; i < d.related().size(); i++) {
      if (i > 0) b.append(',');
      var r = d.related().get(i);
      b.append("{\"source\":");
      source(b, r.source());
      b.append(",\"range\":");
      range(b, r.range());
      b.append(",\"message\":");
      q(b, r.message());
      b.append('}');
    }
    b.append("],\"fixes\":[");
    for (int i = 0; i < d.fixes().size(); i++) {
      if (i > 0) b.append(',');
      var f = d.fixes().get(i);
      b.append("{\"id\":");
      q(b, f.id());
      b.append(",\"title\":");
      q(b, f.title());
      b.append(",\"kind\":");
      q(b, f.kind().name());
      b.append(",\"edits\":[");
      for (int j = 0; j < f.edits().size(); j++) {
        if (j > 0) b.append(',');
        var e = f.edits().get(j);
        b.append("{\"source\":");
        source(b, e.source());
        b.append(",\"range\":");
        range(b, e.range());
        b.append(",\"replacement\":");
        q(b, e.replacement());
        b.append('}');
      }
      b.append("]}");
    }
    b.append("],\"data\":{");
    int i = 0;
    for (var e : d.data().entrySet()) {
      if (i++ > 0) b.append(',');
      q(b, e.getKey());
      b.append(':');
      q(b, e.getValue());
    }
    return b.append("}}").toString().getBytes(StandardCharsets.UTF_8);
  }

  public static Diagnostic read(
      byte[] json, int maxBytes, int maxDepth, Map<SourceId, Integer> lengths) {
    Map<String, Object> m = obj(new JsonParser(json, maxBytes, maxDepth).parse());
    int version = integer(req(m, "schemaVersion"));
    if (version != 1) throw new IllegalArgumentException("JV-SCHEMA-UNSUPPORTED");
    var source = source(req(m, "source"));
    var range = range(req(m, "range"));
    var related = new ArrayList<RelatedInformation>();
    for (Object x : list(req(m, "related"))) {
      var o = obj(x);
      related.add(
          new RelatedInformation(
              source(req(o, "source")), range(req(o, "range")), str(req(o, "message"))));
    }
    var fixes = new ArrayList<Fix>();
    for (Object x : list(req(m, "fixes"))) {
      var o = obj(x);
      var edits = new ArrayList<TextEdit>();
      for (Object y : list(req(o, "edits"))) {
        var e = obj(y);
        edits.add(
            new TextEdit(
                source(req(e, "source")), range(req(e, "range")), str(req(e, "replacement"))));
      }
      var f =
          new Fix(
              str(req(o, "id")), str(req(o, "title")), FixKind.valueOf(str(req(o, "kind"))), edits);
      f.validateBounds(lengths);
      fixes.add(f);
    }
    var data = new TreeMap<String, String>();
    for (var e : obj(req(m, "data")).entrySet()) data.put(e.getKey(), str(e.getValue()));
    validateLocation(source, range, lengths, "primary");
    for (var r : related) validateLocation(r.source(), r.range(), lengths, "related");
    return new Diagnostic(
        version,
        new DiagnosticCode(str(req(m, "code"))),
        Severity.valueOf(str(req(m, "severity"))),
        source,
        range,
        str(req(m, "message")),
        related,
        fixes,
        data);
  }

  private static void validateLocation(
      SourceId source, TextRange range, Map<SourceId, Integer> lengths, String role) {
    Integer length = lengths.get(source);
    if (length == null || range.unit() != OffsetUnit.RAW_UTF16 || range.endOffset() > length)
      throw new IllegalArgumentException(role + " range outside fingerprint-bound source");
  }

  private static Object req(Map<String, Object> m, String k) {
    if (!m.containsKey(k)) throw new IllegalArgumentException("missing required field: " + k);
    return m.get(k);
  }

  private static Map<String, Object> obj(Object x) {
    if (!(x instanceof Map<?, ?> a)) throw new IllegalArgumentException("expected object");
    var m = new LinkedHashMap<String, Object>();
    for (var e : a.entrySet()) {
      if (!(e.getKey() instanceof String k)) throw new IllegalArgumentException("key type");
      m.put(k, e.getValue());
    }
    return m;
  }

  private static List<Object> list(Object x) {
    if (!(x instanceof List<?> l)) throw new IllegalArgumentException("expected array");
    return new ArrayList<>(l);
  }

  private static String str(Object x) {
    if (!(x instanceof String s)) throw new IllegalArgumentException("expected string");
    return s;
  }

  private static int integer(Object x) {
    if (!(x instanceof Long n) || n < 0 || n > Integer.MAX_VALUE)
      throw new IllegalArgumentException("expected integer");
    return n.intValue();
  }

  private static SourceId source(Object x) {
    var m = obj(x);
    return new SourceId(str(req(m, "uri")), str(req(m, "sha256")));
  }

  private static TextRange range(Object x) {
    var m = obj(x);
    return new TextRange(
        OffsetUnit.valueOf(str(req(m, "unit"))), integer(req(m, "start")), integer(req(m, "end")));
  }

  private static void source(StringBuilder b, SourceId s) {
    b.append("{\"uri\":");
    q(b, s.workspaceRelativeUri());
    b.append(",\"sha256\":");
    q(b, s.contentSha256());
    b.append('}');
  }

  private static void range(StringBuilder b, TextRange r) {
    b.append("{\"unit\":");
    q(b, r.unit().name());
    b.append(",\"start\":")
        .append(r.startOffset())
        .append(",\"end\":")
        .append(r.endOffset())
        .append('}');
  }

  private static void q(StringBuilder b, String s) {
    b.append('\"');
    for (char c : s.toCharArray()) {
      switch (c) {
        case '\"' -> b.append("\\\"");
        case '\\' -> b.append("\\\\");
        case '\n' -> b.append("\\n");
        case '\r' -> b.append("\\r");
        case '\t' -> b.append("\\t");
        default -> {
          if (c < 32) b.append(String.format("\\u%04x", (int) c));
          else b.append(c);
        }
      }
    }
    b.append('\"');
  }
}
