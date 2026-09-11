/* SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0 */
package org.javelle.language.protocol;

import java.util.*;

/** Bounded JSON codec for the editor-neutral protocol transport. */
public final class Json {
  private Json() {}

  public static Object parse(String text) {
    return new Parser(text).parse();
  }

  public static String write(Object value) {
    if (value == null) return "null";
    if (value instanceof String s) return '"' + escape(s) + '"';
    if (value instanceof Number || value instanceof Boolean) return value.toString();
    if (value instanceof Map<?, ?> m) {
      var out = new StringBuilder("{");
      boolean first = true;
      for (var e : m.entrySet()) {
        if (!first) out.append(',');
        first = false;
        out.append(write(e.getKey().toString())).append(':').append(write(e.getValue()));
      }
      return out.append('}').toString();
    }
    if (value instanceof Collection<?> c) {
      var out = new StringBuilder("[");
      boolean first = true;
      for (Object x : c) {
        if (!first) out.append(',');
        first = false;
        out.append(write(x));
      }
      return out.append(']').toString();
    }
    throw new IllegalArgumentException("unsupported JSON value");
  }

  private static String escape(String s) {
    var o = new StringBuilder();
    for (char c : s.toCharArray())
      switch (c) {
        case '"' -> o.append("\\\"");
        case '\\' -> o.append("\\\\");
        case '\n' -> o.append("\\n");
        case '\r' -> o.append("\\r");
        case '\t' -> o.append("\\t");
        default -> {
          if (c < 32) o.append(String.format("\\u%04x", (int) c));
          else o.append(c);
        }
      }
    return o.toString();
  }

  private static final class Parser {
    private final String s;
    private int i;
    private int depth;

    Parser(String s) {
      if (s.length() > 16_777_216) throw new IllegalArgumentException("JSON too large");
      this.s = s;
    }

    Object parse() {
      Object v = value();
      ws();
      if (i != s.length()) bad();
      return v;
    }

    Object value() {
      ws();
      if (++depth > 128) bad();
      try {
        if (i >= s.length()) bad();
        char c = s.charAt(i);
        if (c == '{') return object();
        if (c == '[') return array();
        if (c == '"') return string();
        if (s.startsWith("true", i)) {
          i += 4;
          return true;
        }
        if (s.startsWith("false", i)) {
          i += 5;
          return false;
        }
        if (s.startsWith("null", i)) {
          i += 4;
          return null;
        }
        return number();
      } finally {
        depth--;
      }
    }

    Map<String, Object> object() {
      i++;
      var m = new LinkedHashMap<String, Object>();
      ws();
      if (take('}')) return m;
      do {
        ws();
        String k = string();
        if (m.containsKey(k)) bad();
        ws();
        if (!take(':')) bad();
        m.put(k, value());
        ws();
      } while (take(','));
      if (!take('}')) bad();
      return m;
    }

    List<Object> array() {
      i++;
      var a = new ArrayList<>();
      ws();
      if (take(']')) return a;
      do {
        a.add(value());
        ws();
      } while (take(','));
      if (!take(']')) bad();
      return a;
    }

    String string() {
      if (!take('"')) bad();
      var o = new StringBuilder();
      while (i < s.length()) {
        char c = s.charAt(i++);
        if (c == '"') return o.toString();
        if (c == '\\') {
          if (i >= s.length()) bad();
          char e = s.charAt(i++);
          switch (e) {
            case '"', '\\', '/' -> o.append(e);
            case 'b' -> o.append('\b');
            case 'f' -> o.append('\f');
            case 'n' -> o.append('\n');
            case 'r' -> o.append('\r');
            case 't' -> o.append('\t');
            case 'u' -> {
              if (i + 4 > s.length()) bad();
              try {
                o.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
              } catch (NumberFormatException x) {
                bad();
              }
              i += 4;
            }
            default -> bad();
          }
        } else {
          if (c < 32) bad();
          o.append(c);
        }
      }
      bad();
      return null;
    }

    Number number() {
      int a = i;
      if (take('-')) {}
      while (i < s.length() && Character.isDigit(s.charAt(i))) i++;
      boolean decimal = false;
      if (take('.')) {
        decimal = true;
        while (i < s.length() && Character.isDigit(s.charAt(i))) i++;
      }
      if (i < s.length() && (s.charAt(i) == 'e' || s.charAt(i) == 'E')) {
        decimal = true;
        i++;
        if (i < s.length() && (s.charAt(i) == '+' || s.charAt(i) == '-')) i++;
        while (i < s.length() && Character.isDigit(s.charAt(i))) i++;
      }
      if (a == i) bad();
      try {
        if (decimal) return Double.parseDouble(s.substring(a, i));
        return Long.parseLong(s.substring(a, i));
      } catch (NumberFormatException e) {
        bad();
        return 0;
      }
    }

    void ws() {
      while (i < s.length() && " \t\r\n".indexOf(s.charAt(i)) >= 0) i++;
    }

    boolean take(char c) {
      if (i < s.length() && s.charAt(i) == c) {
        i++;
        return true;
      }
      return false;
    }

    void bad() {
      throw new IllegalArgumentException("invalid JSON at " + i);
    }
  }
}
