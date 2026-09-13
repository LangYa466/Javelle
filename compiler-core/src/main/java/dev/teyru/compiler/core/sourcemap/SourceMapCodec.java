package dev.teyru.compiler.core.sourcemap;

import java.io.*;
import java.util.*;
import dev.teyru.compiler.core.source.*;
import dev.teyru.compiler.core.symbol.GeneratedMemberOrigin;
import dev.teyru.compiler.core.syntax.NodeId;

/** Deterministic, versioned persistence for source-map identities, segments, and member origins. */
public final class SourceMapCodec {
  private static final int MAGIC = 0x4a564d50;

  private SourceMapCodec() {}

  public static byte[] write(SourceMap map) {
    try {
      var bytes = new ByteArrayOutputStream();
      var out = new DataOutputStream(bytes);
      out.writeInt(MAGIC);
      out.writeInt(map.schemaVersion());
      out.writeInt(map.generatedFingerprints().size());
      for (var entry : new TreeMap<>(map.generatedFingerprints()).entrySet()) {
        out.writeUTF(entry.getKey());
        out.writeUTF(entry.getValue());
      }
      out.writeInt(map.segments().size());
      for (var segment : map.segments()) writeSegment(out, segment);
      return bytes.toByteArray();
    } catch (IOException impossible) {
      throw new UncheckedIOException(impossible);
    }
  }

  public static SourceMap read(byte[] bytes, int maxSegments) throws IOException {
    Objects.requireNonNull(bytes);
    if (maxSegments < 0) throw new IllegalArgumentException("negative segment budget");
    try (var in = new DataInputStream(new ByteArrayInputStream(bytes))) {
      if (in.readInt() != MAGIC) throw new IOException("invalid source-map magic");
      int version = in.readInt();
      if (version != 1) throw new IOException("unsupported source-map schema " + version);
      int fingerprintCount = bounded(in.readInt(), maxSegments + 1, "fingerprints");
      var fingerprints = new TreeMap<String, String>();
      for (int i = 0; i < fingerprintCount; i++)
        if (fingerprints.put(in.readUTF(), in.readUTF()) != null)
          throw new IOException("duplicate generated URI");
      int count = bounded(in.readInt(), maxSegments, "segments");
      var segments = new ArrayList<SourceMapSegment>(count);
      for (int i = 0; i < count; i++) segments.add(readSegment(in));
      if (in.read() != -1) throw new IOException("trailing source-map data");
      return new SourceMap(version, fingerprints, segments);
    }
  }

  private static int bounded(int value, int max, String name) throws IOException {
    if (value < 0 || value > max) throw new IOException(name + " exceeds budget");
    return value;
  }

  private static void writeSegment(DataOutputStream out, SourceMapSegment s) throws IOException {
    out.writeUTF(s.generated().generatedRelativeUri());
    writeRange(out, s.generated().range());
    out.writeInt(s.originals().size());
    for (var original : s.originals()) writeLocation(out, original);
    out.writeInt(s.kind().ordinal());
    writeNode(out, s.node());
    out.writeBoolean(s.memberOrigin().isPresent());
    if (s.memberOrigin().isPresent()) writeMember(out, s.memberOrigin().orElseThrow());
    out.writeInt(s.priority());
    out.writeUTF(s.reason());
  }

  private static SourceMapSegment readSegment(DataInputStream in) throws IOException {
    var generated = new GeneratedRange(in.readUTF(), readRange(in));
    int count = bounded(in.readInt(), 1_000_000, "origins");
    var originals = new ArrayList<SourceLocation>(count);
    for (int i = 0; i < count; i++) originals.add(readLocation(in));
    var kind = MappingKind.values()[bounded(in.readInt(), MappingKind.values().length - 1, "kind")];
    var node = readNode(in);
    Optional<GeneratedMemberOrigin> member =
        in.readBoolean() ? Optional.of(readMember(in)) : Optional.empty();
    return new SourceMapSegment(
        generated, originals, kind, node, member, in.readInt(), in.readUTF());
  }

  private static void writeLocation(DataOutputStream out, SourceLocation value) throws IOException {
    writeSource(out, value.source());
    writeRange(out, value.range());
  }

  private static SourceLocation readLocation(DataInputStream in) throws IOException {
    return new SourceLocation(readSource(in), readRange(in));
  }

  private static void writeSource(DataOutputStream out, SourceId value) throws IOException {
    out.writeUTF(value.workspaceRelativeUri());
    out.writeUTF(value.contentSha256());
  }

  private static SourceId readSource(DataInputStream in) throws IOException {
    return new SourceId(in.readUTF(), in.readUTF());
  }

  private static void writeRange(DataOutputStream out, TextRange value) throws IOException {
    out.writeInt(value.unit().ordinal());
    out.writeInt(value.startOffset());
    out.writeInt(value.endOffset());
  }

  private static TextRange readRange(DataInputStream in) throws IOException {
    return new TextRange(
        OffsetUnit.values()[bounded(in.readInt(), OffsetUnit.values().length - 1, "unit")],
        in.readInt(),
        in.readInt());
  }

  private static void writeNode(DataOutputStream out, NodeId value) throws IOException {
    writeSource(out, value.source());
    out.writeUTF(value.grammarKind());
    writeRange(out, value.rawRange());
    out.writeInt(value.ordinal());
  }

  private static NodeId readNode(DataInputStream in) throws IOException {
    return new NodeId(readSource(in), in.readUTF(), readRange(in), in.readInt());
  }

  private static void writeMember(DataOutputStream out, GeneratedMemberOrigin value)
      throws IOException {
    writeSource(out, value.source());
    writeNode(out, value.declaration());
    out.writeInt(value.kind().ordinal());
    out.writeUTF(value.featureId());
  }

  private static GeneratedMemberOrigin readMember(DataInputStream in) throws IOException {
    return new GeneratedMemberOrigin(
        readSource(in),
        readNode(in),
        GeneratedMemberOrigin.OriginKind.values()[
            bounded(
                in.readInt(), GeneratedMemberOrigin.OriginKind.values().length - 1, "origin kind")],
        in.readUTF());
  }
}
