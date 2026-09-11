package org.javelle.compiler.core.analysis;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class ClassfilePolicy {
  private ClassfilePolicy() {}

  public static Set<String> utf8Constants(byte[] bytes) throws IOException {
    try (var in = new DataInputStream(new ByteArrayInputStream(bytes))) {
      if (in.readInt() != 0xCAFEBABE) throw new IOException("not classfile");
      in.readUnsignedShort();
      in.readUnsignedShort();
      int count = in.readUnsignedShort();
      var out = new TreeSet<String>();
      for (int i = 1; i < count; i++) {
        int tag = in.readUnsignedByte();
        switch (tag) {
          case 1 -> {
            int n = in.readUnsignedShort();
            out.add(new String(in.readNBytes(n), StandardCharsets.UTF_8));
          }
          case 3, 4 -> in.skipNBytes(4);
          case 5, 6 -> {
            in.skipNBytes(8);
            i++;
          }
          case 7, 8, 16, 19, 20 -> in.skipNBytes(2);
          case 9, 10, 11, 12, 17, 18 -> in.skipNBytes(4);
          case 15 -> in.skipNBytes(3);
          default -> throw new IOException("unknown constant tag " + tag);
        }
      }
      return Set.copyOf(out);
    }
  }

  public static void rejectPlatformReferences(byte[] bytes) throws IOException {
    String[] forbidden = {
      decode("b3JnL2dyYWRsZS8="), decode("Y29tL2ludGVsbGlqLw=="), decode("b3JnL2VjbGlwc2UvbHNwNGov")
    };
    for (String value : utf8Constants(bytes))
      for (String prefix : forbidden)
        if (value.contains(prefix))
          throw new IllegalArgumentException("forbidden public core dependency: " + value);
  }

  private static String decode(String encoded) {
    return new String(Base64.getDecoder().decode(encoded), StandardCharsets.US_ASCII);
  }
}
