package org.javelle.compiler.core.diagnostic;

import java.util.*;

final class JsonParser {
  private final String s;
  private final int maxDepth;
  private int p;

  JsonParser(byte[] bytes, int maxBytes, int maxDepth) {
    if (bytes.length > maxBytes) throw new IllegalArgumentException("JSON_SIZE_LIMIT");
    this.s = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    this.maxDepth = maxDepth;
  }

  Object parse() {
    Object v = value(0);
    ws();
    if (p != s.length()) fail("trailing input");
    return v;
  }

  private Object value(int d) {
    if (d > maxDepth) fail("JSON_NESTING_LIMIT");
    ws();
    if (p >= s.length()) fail("missing value");
    char c = s.charAt(p);
    if (c == '{') return object(d + 1);
    if (c == '[') return array(d + 1);
    if (c == '\"') return string();
    if (s.startsWith("true", p)) {
      p += 4;
      return true;
    }
    if (s.startsWith("false", p)) {
      p += 5;
      return false;
    }
    if (s.startsWith("null", p)) {
      p += 4;
      return null;
    }
    return number();
  }

  private Map<String, Object> object(int d) {
    p++;
    var m = new LinkedHashMap<String, Object>();
    ws();
    if (take('}')) return m;
    do {
      ws();
      if (p >= s.length() || s.charAt(p) != '\"') fail("object key");
      String k = string();
      if (m.containsKey(k)) fail("duplicate key: " + k);
      ws();
      need(':');
      m.put(k, value(d));
      ws();
    } while (take(','));
    need('}');
    return m;
  }

  private List<Object> array(int d) {
    p++;
    var a = new ArrayList<>();
    ws();
    if (take(']')) return a;
    do {
      a.add(value(d));
      ws();
    } while (take(','));
    need(']');
    return a;
  }

  private String string() {
    need('\"');
    var b = new StringBuilder();
    while (p < s.length()) {
      char c = s.charAt(p++);
      if (c == '\"') return b.toString();
      if (c == '\\') {
        if (p >= s.length()) fail("escape");
        char e = s.charAt(p++);
        switch (e) {
          case '\"', '\\', '/' -> b.append(e);
          case 'b' -> b.append('\b');
          case 'f' -> b.append('\f');
          case 'n' -> b.append('\n');
          case 'r' -> b.append('\r');
          case 't' -> b.append('\t');
          case 'u' -> {
            if (p + 4 > s.length()) fail("unicode");
            try {
              b.append((char) Integer.parseInt(s.substring(p, p + 4), 16));
            } catch (NumberFormatException x) {
              fail("unicode");
            }
            p += 4;
          }
          default -> fail("escape");
        }
      } else {
        if (c < 0x20) fail("control");
        b.append(c);
      }
    }
    fail("unterminated string");
    return "";
  }

  private Number number() {
    int a = p;
    if (take('-')) {}
    while (p < s.length() && Character.isDigit(s.charAt(p))) p++;
    if (a == p) fail("number");
    try {
      return Long.parseLong(s.substring(a, p));
    } catch (NumberFormatException e) {
      fail("number overflow");
      return 0;
    }
  }

  private void ws() {
    while (p < s.length() && " \n\r\t".indexOf(s.charAt(p)) >= 0) p++;
  }

  private boolean take(char c) {
    if (p < s.length() && s.charAt(p) == c) {
      p++;
      return true;
    }
    return false;
  }

  private void need(char c) {
    if (!take(c)) fail("expected " + c);
  }

  private void fail(String m) {
    throw new IllegalArgumentException("invalid JSON at " + p + ": " + m);
  }
}
