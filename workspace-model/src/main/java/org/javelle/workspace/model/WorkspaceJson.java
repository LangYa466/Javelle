/* SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0 */
package org.javelle.workspace.model;

import java.math.BigDecimal;
import java.nio.charset.*;
import java.util.*;

final class WorkspaceJson {
  static JsonValue.ObjectValue parse(byte[] bytes, WorkspaceReadOptions limits) {
    if (bytes.length > limits.maxBytes()) throw new IllegalArgumentException("JV-WS-LIMIT-BYTES");
    String text;
    try {
      text =
          StandardCharsets.UTF_8
              .newDecoder()
              .onMalformedInput(CodingErrorAction.REPORT)
              .decode(java.nio.ByteBuffer.wrap(bytes))
              .toString();
    } catch (CharacterCodingException e) {
      throw new IllegalArgumentException("JV-WS-SCHEMA-UTF8", e);
    }
    Parser p = new Parser(text, limits);
    JsonValue v = p.value(0);
    p.ws();
    if (p.i != text.length()) throw p.bad("trailing input");
    if (!(v instanceof JsonValue.ObjectValue o)) throw p.bad("root object required");
    return o;
  }

  static byte[] write(JsonValue value) {
    return (render(value) + "\n").getBytes(StandardCharsets.UTF_8);
  }

  private static String render(JsonValue v) {
    if (v instanceof JsonValue.StringValue s) return quote(s.value());
    if (v instanceof JsonValue.NumberValue n) return n.value().toPlainString();
    if (v instanceof JsonValue.BooleanValue b) return Boolean.toString(b.value());
    if (v instanceof JsonValue.NullValue) return "null";
    if (v instanceof JsonValue.ArrayValue a)
      return a.values().stream()
          .map(WorkspaceJson::render)
          .collect(java.util.stream.Collectors.joining(",", "[", "]"));
    var o = (JsonValue.ObjectValue) v;
    return o.values().entrySet().stream()
        .map(e -> quote(e.getKey()) + ":" + render(e.getValue()))
        .collect(java.util.stream.Collectors.joining(",", "{", "}"));
  }

  private static String quote(String s) {
    StringBuilder b = new StringBuilder("\"");
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      switch (c) {
        case '"' -> b.append("\\\"");
        case '\\' -> b.append("\\\\");
        case '\b' -> b.append("\\b");
        case '\f' -> b.append("\\f");
        case '\n' -> b.append("\\n");
        case '\r' -> b.append("\\r");
        case '\t' -> b.append("\\t");
        default -> {
          if (c < 32) b.append(String.format("\\u%04x", (int) c));
          else b.append(c);
        }
      }
    }
    return b.append('"').toString();
  }

  private static final class Parser {
    final String s;
    final WorkspaceReadOptions l;
    int i, entries;

    Parser(String s, WorkspaceReadOptions l) {
      this.s = s;
      this.l = l;
    }

    void ws() {
      while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
    }

    IllegalArgumentException bad(String m) {
      return new IllegalArgumentException("JV-WS-SCHEMA-JSON at " + i + ": " + m);
    }

    JsonValue value(int depth) {
      if (depth > l.maxDepth()) throw bad("depth");
      ws();
      if (i >= s.length()) throw bad("eof");
      char c = s.charAt(i);
      if (c == '{') return object(depth);
      if (c == '[') return array(depth);
      if (c == '"') return new JsonValue.StringValue(string());
      if (c == 't' && take("true")) return new JsonValue.BooleanValue(true);
      if (c == 'f' && take("false")) return new JsonValue.BooleanValue(false);
      if (c == 'n' && take("null")) return JsonValue.NullValue.INSTANCE;
      return number();
    }

    boolean take(String x) {
      if (s.startsWith(x, i)) {
        i += x.length();
        return true;
      }
      return false;
    }

    JsonValue.ObjectValue object(int d) {
      i++;
      ws();
      Map<String, JsonValue> m = new TreeMap<>();
      if (i < s.length() && s.charAt(i) == '}') {
        i++;
        return new JsonValue.ObjectValue(m);
      }
      while (true) {
        ws();
        if (i >= s.length() || s.charAt(i) != '"') throw bad("key");
        String k = string();
        if (m.containsKey(k)) throw bad("duplicate key " + k);
        ws();
        if (i >= s.length() || s.charAt(i++) != ':') throw bad(":");
        count();
        m.put(k, value(d + 1));
        ws();
        if (i < s.length() && s.charAt(i) == '}') {
          i++;
          return new JsonValue.ObjectValue(m);
        }
        if (i >= s.length() || s.charAt(i++) != ',') throw bad(",");
      }
    }

    JsonValue.ArrayValue array(int d) {
      i++;
      ws();
      List<JsonValue> a = new ArrayList<>();
      if (i < s.length() && s.charAt(i) == ']') {
        i++;
        return new JsonValue.ArrayValue(a);
      }
      while (true) {
        count();
        a.add(value(d + 1));
        ws();
        if (i < s.length() && s.charAt(i) == ']') {
          i++;
          return new JsonValue.ArrayValue(a);
        }
        if (i >= s.length() || s.charAt(i++) != ',') throw bad(",");
      }
    }

    void count() {
      if (++entries > l.maxEntries()) throw bad("entry limit");
    }

    String string() {
      i++;
      StringBuilder b = new StringBuilder();
      while (i < s.length()) {
        char c = s.charAt(i++);
        if (c == '"') return b.toString();
        if (c == '\\') {
          if (i >= s.length()) throw bad("escape");
          char e = s.charAt(i++);
          switch (e) {
            case '"', '\\', '/' -> b.append(e);
            case 'b' -> b.append('\b');
            case 'f' -> b.append('\f');
            case 'n' -> b.append('\n');
            case 'r' -> b.append('\r');
            case 't' -> b.append('\t');
            case 'u' -> {
              if (i + 4 > s.length()) throw bad("unicode");
              int cp;
              try {
                cp = Integer.parseInt(s.substring(i, i + 4), 16);
              } catch (NumberFormatException x) {
                throw bad("unicode");
              }
              b.append((char) cp);
              i += 4;
            }
            default -> throw bad("escape");
          }
        } else {
          if (c < 32) throw bad("control");
          b.append(c);
        }
      }
      throw bad("string");
    }

    JsonValue.NumberValue number() {
      int a = i;
      if (i < s.length() && s.charAt(i) == '-') i++;
      if (i >= s.length() || !Character.isDigit(s.charAt(i))) throw bad("value");
      if (s.charAt(i) == '0') i++;
      else while (i < s.length() && Character.isDigit(s.charAt(i))) i++;
      if (i < s.length() && s.charAt(i) == '.') {
        i++;
        if (i >= s.length() || !Character.isDigit(s.charAt(i))) throw bad("number");
        while (i < s.length() && Character.isDigit(s.charAt(i))) i++;
      }
      if (i < s.length() && (s.charAt(i) == 'e' || s.charAt(i) == 'E')) {
        i++;
        if (i < s.length() && (s.charAt(i) == '+' || s.charAt(i) == '-')) i++;
        if (i >= s.length() || !Character.isDigit(s.charAt(i))) throw bad("number");
        while (i < s.length() && Character.isDigit(s.charAt(i))) i++;
      }
      return new JsonValue.NumberValue(new BigDecimal(s.substring(a, i)));
    }
  }
}
