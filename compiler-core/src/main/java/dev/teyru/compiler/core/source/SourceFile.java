package dev.teyru.compiler.core.source;

import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.security.MessageDigest;
import java.util.*;
import dev.teyru.compiler.core.budget.ResourceBudget;

public final class SourceFile {
  private final SourceId id;
  private final byte[] utf8;
  private final String raw, translated;
  private final LineMap lineMap;
  private final UnicodeMap unicodeMap;
  private final InputFingerprint fingerprint;

  private SourceFile(SourceId id, byte[] bytes, String raw, Translation tr) {
    this.id = id;
    utf8 = bytes.clone();
    this.raw = raw;
    translated = tr.text;
    lineMap = new Lines(raw);
    unicodeMap = new Mapping(tr.rawAt, raw, translated);
    fingerprint = new InputFingerprint("SHA-256", sha(bytes));
  }

  public static SourceFile decode(SourceId id, byte[] bytes, ResourceBudget budget) {
    Objects.requireNonNull(id);
    Objects.requireNonNull(bytes);
    budget.checkBytes(bytes.length);
    String actualHash = sha(bytes);
    if (!id.contentSha256().equals(actualHash))
      throw new IllegalArgumentException("JVL-SOURCE-CONTENT-ID-MISMATCH");
    String raw;
    try {
      raw =
          StandardCharsets.UTF_8
              .newDecoder()
              .onMalformedInput(CodingErrorAction.REPORT)
              .onUnmappableCharacter(CodingErrorAction.REPORT)
              .decode(ByteBuffer.wrap(bytes))
              .toString();
    } catch (CharacterCodingException e) {
      throw new IllegalArgumentException("JVL-SOURCE-INVALID-UTF8", e);
    }
    Translation tr = translate(raw);
    return new SourceFile(id, bytes, raw, tr);
  }

  public SourceId id() {
    return id;
  }

  public byte[] utf8() {
    return utf8.clone();
  }

  public String rawText() {
    return raw;
  }

  public String translatedText() {
    return translated;
  }

  public LineMap rawLineMap() {
    return lineMap;
  }

  public UnicodeMap unicodeMap() {
    return unicodeMap;
  }

  public InputFingerprint fingerprint() {
    return fingerprint;
  }

  /** Converts a valid boundary explicitly; code-point/byte units refer to original source text. */
  public int convertBoundary(int offset, OffsetUnit from, OffsetUnit to, Bias bias) {
    Objects.requireNonNull(from);
    Objects.requireNonNull(to);
    Objects.requireNonNull(bias);
    int rawOffset =
        switch (from) {
          case RAW_UTF16 -> checkedUtf16(raw, offset);
          case TRANSLATED_UTF16 -> unicodeMap.rawBoundary(checkedUtf16(translated, offset), bias);
          case UNICODE_CODE_POINT -> utf16FromCodePoint(raw, offset);
          case UTF8_BYTE -> utf16FromUtf8(raw, offset);
        };
    return switch (to) {
      case RAW_UTF16 -> rawOffset;
      case TRANSLATED_UTF16 -> unicodeMap.translatedBoundary(rawOffset, bias);
      case UNICODE_CODE_POINT -> raw.codePointCount(0, checkedUtf16(raw, rawOffset));
      case UTF8_BYTE ->
          raw.substring(0, checkedUtf16(raw, rawOffset)).getBytes(StandardCharsets.UTF_8).length;
    };
  }

  private static int checkedUtf16(String text, int offset) {
    if (offset < 0
        || offset > text.length()
        || offset > 0
            && offset < text.length()
            && Character.isHighSurrogate(text.charAt(offset - 1))
            && Character.isLowSurrogate(text.charAt(offset)))
      throw new IllegalArgumentException("invalid Unicode boundary");
    return offset;
  }

  private static int utf16FromCodePoint(String text, int offset) {
    if (offset < 0 || offset > text.codePointCount(0, text.length()))
      throw new IllegalArgumentException("code-point boundary");
    return text.offsetByCodePoints(0, offset);
  }

  private static int utf16FromUtf8(String text, int offset) {
    if (offset < 0) throw new IllegalArgumentException("UTF-8 boundary");
    int bytes = 0;
    for (int i = 0; i < text.length(); ) {
      int cp = text.codePointAt(i),
          n = new String(Character.toChars(cp)).getBytes(StandardCharsets.UTF_8).length;
      if (bytes == offset) return i;
      if (bytes + n > offset) throw new IllegalArgumentException("inside UTF-8 sequence");
      bytes += n;
      i += Character.charCount(cp);
    }
    if (bytes == offset) return text.length();
    throw new IllegalArgumentException("UTF-8 boundary");
  }

  private static String sha(byte[] b) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private record Translation(String text, int[] rawAt) {}

  private static Translation translate(String s) {
    StringBuilder out = new StringBuilder();
    List<Integer> map = new ArrayList<>();
    map.add(0);
    int i = 0, slashes = 0;
    while (i < s.length()) {
      char c = s.charAt(i);
      if (c == '\\' && slashes % 2 == 0) {
        int u = i + 1;
        while (u < s.length() && s.charAt(u) == 'u') u++;
        if (u > i + 1 && u + 4 <= s.length()) {
          try {
            char v = (char) Integer.parseInt(s.substring(u, u + 4), 16);
            out.append(v);
            i = u + 4;
            map.add(i);
            slashes = v == '\\' ? slashes + 1 : 0;
            continue;
          } catch (NumberFormatException ignored) {
          }
        }
      }
      out.append(c);
      i++;
      map.add(i);
      slashes = c == '\\' ? slashes + 1 : 0;
    }
    return new Translation(out.toString(), map.stream().mapToInt(Integer::intValue).toArray());
  }

  private static final class Mapping implements UnicodeMap {
    private final int[] r;
    private final String raw;
    private final String translated;

    Mapping(int[] r, String raw, String translated) {
      this.r = r;
      this.raw = raw;
      this.translated = translated;
    }

    public int rawBoundary(int t, Bias b) {
      checkedUtf16(translated, t);
      if (t < 0 || t >= r.length) throw new IllegalArgumentException("boundary");
      return r[t];
    }

    public int translatedBoundary(int raw, Bias bias) {
      checkedUtf16(this.raw, raw);
      if (raw < 0 || raw > r[r.length - 1]) throw new IllegalArgumentException("boundary");
      int p = Arrays.binarySearch(r, raw);
      if (p >= 0) return p;
      int insertion = -p - 1;
      return bias == Bias.START ? insertion - 1 : insertion;
    }

    public TextRange rawRangeForTranslated(TextRange t) {
      if (t.unit() != OffsetUnit.TRANSLATED_UTF16) throw new IllegalArgumentException("unit");
      return new TextRange(
          OffsetUnit.RAW_UTF16,
          rawBoundary(t.startOffset(), Bias.START),
          rawBoundary(t.endOffset(), Bias.END));
    }

    public TextRange translatedRangeForRaw(TextRange x) {
      if (x.unit() != OffsetUnit.RAW_UTF16) throw new IllegalArgumentException("unit");
      return new TextRange(
          OffsetUnit.TRANSLATED_UTF16,
          translatedBoundary(x.startOffset(), Bias.START),
          translatedBoundary(x.endOffset(), Bias.END));
    }
  }

  private static final class Lines implements LineMap {
    private final String s;
    private final int[] starts;

    Lines(String s) {
      this.s = s;
      List<Integer> a = new ArrayList<>();
      a.add(0);
      for (int i = 0; i < s.length(); i++) {
        char c = s.charAt(i);
        if (c == '\r') {
          if (i + 1 < s.length() && s.charAt(i + 1) == '\n') i++;
          a.add(i + 1);
        } else if (c == '\n') a.add(i + 1);
      }
      starts = a.stream().mapToInt(Integer::intValue).toArray();
    }

    private void valid(int o) {
      if (o < 0
          || o > s.length()
          || (o > 0
              && o < s.length()
              && Character.isLowSurrogate(s.charAt(o))
              && Character.isHighSurrogate(s.charAt(o - 1)))
          || (o > 0 && o < s.length() && s.charAt(o - 1) == '\r' && s.charAt(o) == '\n'))
        throw new IllegalArgumentException("invalid UTF-16 boundary");
    }

    public LinePosition positionOf(int o) {
      valid(o);
      int p = Arrays.binarySearch(starts, o);
      int line = p >= 0 ? p : -p - 2;
      return new LinePosition(line, o - starts[line]);
    }

    public int offsetOf(LinePosition p) {
      if (p.line() >= starts.length) throw new IllegalArgumentException("line");
      int o = Math.addExact(starts[p.line()], p.utf16Character());
      int end = p.line() + 1 < starts.length ? starts[p.line() + 1] : s.length();
      if (o > end) throw new IllegalArgumentException("character");
      valid(o);
      return o;
    }

    public TextRange lineRange(int l) {
      if (l < 0 || l >= starts.length) throw new IllegalArgumentException("line");
      return new TextRange(
          OffsetUnit.RAW_UTF16, starts[l], l + 1 < starts.length ? starts[l + 1] : s.length());
    }
  }
}
