/* SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0 */
package dev.teyru.workspace.model;

import static dev.teyru.workspace.model.WorkspaceModel.*;

import java.math.BigDecimal;
import java.util.*;

public final class DefaultWorkspaceModelCodec implements WorkspaceModelCodec {
  private static final String UNKNOWN_TOP_LEVEL = "dev.teyru.workspace/unknown-top-level";
  private static final Set<String> ROOT_FIELDS =
      Set.of(
          "schemaVersion",
          "serializationKind",
          "workspace",
          "modules",
          "overlays",
          "fingerprints",
          "limits",
          "extensions");

  public byte[] writePortable(WorkspaceModel model) {
    if (model.serializationKind() != SerializationKind.PORTABLE)
      throw new IllegalArgumentException("TY-WS-PATH-PORTABLE");
    assertPortable(model);
    return WorkspaceJson.write(toJson(model));
  }

  public byte[] writeResolved(WorkspaceModel model) {
    if (model.serializationKind() != SerializationKind.RESOLVED)
      throw new IllegalArgumentException("TY-WS-PATH-RESOLVED");
    return WorkspaceJson.write(toJson(model));
  }

  public WorkspaceReadResult read(byte[] utf8, WorkspaceReadOptions options) {
    try {
      var raw = WorkspaceJson.parse(utf8, options);
      WorkspaceModel m = fromJson(raw);
      var d = new DefaultWorkspaceModelValidator().validate(m, ValidationEnvironment.noIo());
      return new WorkspaceReadResult(d.isEmpty() ? Optional.of(m) : Optional.empty(), d, raw);
    } catch (RuntimeException e) {
      var empty = new JsonValue.ObjectValue(Map.of());
      return new WorkspaceReadResult(
          Optional.empty(), List.of(new WorkspaceDiagnostic(code(e), "", e.getMessage())), empty);
    }
  }

  private static String code(Exception e) {
    String m = e.getMessage();
    return m != null && m.startsWith("TY-WS-") ? m.split("[ :]", 2)[0] : "TY-WS-SCHEMA-INVALID";
  }

  private static void assertPortable(WorkspaceModel m) {
    if (!(m.workspace().logicalRoot().equals(".")
        || DefaultWorkspaceModelValidator.isSafeLogicalPath(m.workspace().logicalRoot())))
      throw new IllegalArgumentException("TY-WS-PATH-TRAVERSAL");
    for (var mod : m.modules()) {
      if (!mod.logicalProjectPath().matches("^:(?:[A-Za-z0-9_.-]+(?::[A-Za-z0-9_.-]+)*)?$"))
        throw new IllegalArgumentException("TY-WS-PATH-PROJECT");
      check(mod.toolchain().executable());
      for (var s : mod.sourceSets()) for (var p : all(s)) check(p);
    }
    m.workspace().resolvedRoot().ifPresent(DefaultWorkspaceModelCodec::check);
  }

  private static void check(PathRef p) {
    if (p.fileUri().isPresent() || p.resolvedAbsoluteUri().isPresent())
      throw new IllegalArgumentException("TY-WS-PATH-PORTABLE");
    if (!DefaultWorkspaceModelValidator.isSafeLogicalPath(p.logicalPath()))
      throw new IllegalArgumentException("TY-WS-PATH-TRAVERSAL");
  }

  private static List<PathRef> all(SourceSet s) {
    var x = new ArrayList<PathRef>();
    x.addAll(s.javaRoots());
    x.addAll(s.teyruRoots());
    x.addAll(s.generatedJavaRoots());
    x.addAll(s.generatedClassRoots());
    x.addAll(s.paths().compileClasspath());
    x.addAll(s.paths().runtimeClasspath());
    x.addAll(s.paths().processorPath());
    x.addAll(s.paths().modulePath());
    x.addAll(s.paths().sourceJars());
    return x;
  }

  private static JsonValue toJson(WorkspaceModel m) {
    Map<String, JsonValue> o = new TreeMap<>();
    JsonValue unknown = m.extensions().get(UNKNOWN_TOP_LEVEL);
    if (unknown instanceof JsonValue.ObjectValue extra) o.putAll(extra.values());
    o.put(
        "schemaVersion",
        obj("major", n(m.schemaVersion().major()), "minor", n(m.schemaVersion().minor())));
    o.put("serializationKind", s(m.serializationKind()));
    o.put("workspace", workspace(m.workspace()));
    o.put("modules", arr(m.modules().stream().map(DefaultWorkspaceModelCodec::module).toList()));
    o.put("overlays", arr(m.overlays().stream().map(DefaultWorkspaceModelCodec::overlay).toList()));
    o.put("fingerprints", fingerprints(m.fingerprints()));
    o.put("limits", limits(m.limits()));
    var extensions = new TreeMap<>(m.extensions());
    extensions.remove(UNKNOWN_TOP_LEVEL);
    o.put("extensions", new JsonValue.ObjectValue(extensions));
    return new JsonValue.ObjectValue(o);
  }

  private static JsonValue workspace(WorkspaceDescriptor w) {
    return obj(
        "logicalName",
        s(w.logicalName()),
        "logicalRoot",
        s(w.logicalRoot()),
        "resolvedRoot",
        opt(w.resolvedRoot(), DefaultWorkspaceModelCodec::path),
        "caseSensitivity",
        s(w.caseSensitivity()),
        "defaultEncoding",
        s(w.defaultEncoding()),
        "trust",
        trust(w.trust()));
  }

  private static JsonValue module(WorkspaceModule m) {
    return obj(
        "id",
        s(m.id()),
        "logicalProjectPath",
        s(m.logicalProjectPath()),
        "sourceSets",
        arr(m.sourceSets().stream().map(DefaultWorkspaceModelCodec::sourceSet).toList()),
        "dependencies",
        arr(m.dependencies().stream().map(DefaultWorkspaceModelCodec::dep).toList()),
        "toolchain",
        toolchain(m.toolchain()),
        "propertyMetadata",
        arr(m.propertyMetadata().stream().map(DefaultWorkspaceModelCodec::property).toList()),
        "extensions",
        new JsonValue.ObjectValue(m.extensions()));
  }

  private static JsonValue sourceSet(SourceSet x) {
    return obj(
        "name",
        s(x.name()),
        "javaRoots",
        paths(x.javaRoots()),
        "teyruRoots",
        paths(x.teyruRoots()),
        "generatedJavaRoots",
        paths(x.generatedJavaRoots()),
        "generatedClassRoots",
        paths(x.generatedClassRoots()),
        "paths",
        obj(
            "compileClasspath",
            paths(x.paths().compileClasspath()),
            "runtimeClasspath",
            paths(x.paths().runtimeClasspath()),
            "processorPath",
            paths(x.paths().processorPath()),
            "modulePath",
            paths(x.paths().modulePath()),
            "sourceJars",
            paths(x.paths().sourceJars())),
        "releases",
        obj(
            "source",
            n(x.releases().source()),
            "target",
            n(x.releases().target()),
            "release",
            n(x.releases().release())),
        "compilerOptions",
        arr(x.compilerOptions().stream().map(DefaultWorkspaceModelCodec::s).toList()),
        "encoding",
        s(x.encoding()),
        "trust",
        trust(x.trust()),
        "onDiskSnapshotFingerprint",
        opt(x.onDiskSnapshotFingerprint(), DefaultWorkspaceModelCodec::s));
  }

  private static JsonValue path(PathRef p) {
    return obj(
        "logicalPath",
        s(p.logicalPath()),
        "relocatableKey",
        s(p.relocatableKey()),
        "fileUri",
        opt(p.fileUri(), DefaultWorkspaceModelCodec::s),
        "resolvedAbsoluteUri",
        opt(p.resolvedAbsoluteUri(), DefaultWorkspaceModelCodec::s),
        "kind",
        s(p.kind()),
        "exists",
        b(p.exists()),
        "symlinkPolicy",
        s(p.symlinkPolicy()),
        "contentSha256",
        opt(p.contentSha256(), DefaultWorkspaceModelCodec::s));
  }

  private static JsonValue trust(TrustPolicy t) {
    return obj(
        "level",
        s(t.level()),
        "allowBuildEvaluation",
        b(t.allowBuildEvaluation()),
        "allowProcessors",
        b(t.allowProcessors()),
        "allowNetwork",
        b(t.allowNetwork()),
        "allowUserCode",
        b(t.allowUserCode()),
        "decisionSource",
        s(t.decisionSource()));
  }

  private static JsonValue toolchain(Toolchain t) {
    return obj(
        "executable",
        path(t.executable()),
        "javaVersion",
        s(t.javaVersion()),
        "vendor",
        s(t.vendor()),
        "runtimeFingerprint",
        s(t.runtimeFingerprint()));
  }

  private static JsonValue dep(ModuleDependency d) {
    return obj(
        "targetModuleId",
        s(d.targetModuleId()),
        "scope",
        s(d.scope()),
        "kind",
        s(d.kind()),
        "targetSourceSet",
        opt(d.targetSourceSet(), DefaultWorkspaceModelCodec::s));
  }

  private static JsonValue property(PropertyMetadata p) {
    return obj(
        "ownerBinaryName",
        s(p.ownerBinaryName()),
        "propertyName",
        s(p.propertyName()),
        "typeDescriptor",
        s(p.typeDescriptor()),
        "getterName",
        opt(p.getterName(), DefaultWorkspaceModelCodec::s),
        "setterName",
        opt(p.setterName(), DefaultWorkspaceModelCodec::s),
        "readable",
        b(p.readable()),
        "writable",
        b(p.writable()),
        "metadataVersion",
        n(p.metadataVersion()),
        "originFingerprint",
        s(p.originFingerprint()));
  }

  private static JsonValue overlay(DocumentOverlay d) {
    return obj(
        "documentUri",
        s(d.documentUri()),
        "version",
        n(d.version()),
        "contentSha256",
        s(d.contentSha256()),
        "baseOnDiskFingerprint",
        s(d.baseOnDiskFingerprint()),
        "state",
        s(d.state()),
        "contentUtf8",
        opt(d.contentUtf8(), DefaultWorkspaceModelCodec::s));
  }

  private static JsonValue fingerprints(ModelFingerprints f) {
    return obj(
        "algorithm",
        s(f.algorithm()),
        "model",
        s(f.model()),
        "configuration",
        s(f.configuration()),
        "classpath",
        s(f.classpath()),
        "toolchain",
        s(f.toolchain()),
        "options",
        s(f.options()));
  }

  private static JsonValue limits(ModelLimits l) {
    return obj(
        "maxDocumentBytes",
        n(l.maxDocumentBytes()),
        "maxModules",
        n(l.maxModules()),
        "maxSourceSets",
        n(l.maxSourceSets()),
        "maxPaths",
        n(l.maxPaths()),
        "maxOverlays",
        n(l.maxOverlays()),
        "maxDependencyEdges",
        n(l.maxDependencyEdges()));
  }

  private static JsonValue paths(List<PathRef> x) {
    return arr(x.stream().map(DefaultWorkspaceModelCodec::path).toList());
  }

  private static <T> JsonValue opt(Optional<T> x, java.util.function.Function<T, JsonValue> f) {
    return x.<JsonValue>map(f).orElse(JsonValue.NullValue.INSTANCE);
  }

  private static JsonValue s(Object x) {
    return new JsonValue.StringValue(x.toString());
  }

  private static JsonValue n(long x) {
    return new JsonValue.NumberValue(BigDecimal.valueOf(x));
  }

  private static JsonValue b(boolean x) {
    return new JsonValue.BooleanValue(x);
  }

  private static JsonValue arr(List<JsonValue> x) {
    return new JsonValue.ArrayValue(x);
  }

  private static JsonValue obj(Object... x) {
    Map<String, JsonValue> m = new TreeMap<>();
    for (int i = 0; i < x.length; i += 2)
      if (!(x[i + 1] instanceof JsonValue.NullValue)) m.put((String) x[i], (JsonValue) x[i + 1]);
    return new JsonValue.ObjectValue(m);
  }

  private static WorkspaceModel fromJson(JsonValue.ObjectValue root) {
    var o = root.values();
    var v = o(o, "schemaVersion");
    int major = i(v.values(), "major"), minor = i(v.values(), "minor");
    if (major != 1) throw new IllegalArgumentException("TY-WS-SCHEMA-MAJOR");
    var w = o(o, "workspace");
    var extensions = new TreeMap<>(objectValues(o, "extensions"));
    var unknown = new TreeMap<String, JsonValue>();
    o.forEach(
        (key, value) -> {
          if (!ROOT_FIELDS.contains(key)) unknown.put(key, value);
        });
    if (!unknown.isEmpty()) extensions.put(UNKNOWN_TOP_LEVEL, new JsonValue.ObjectValue(unknown));
    var model =
        new WorkspaceModel(
            new SchemaVersion(major, minor),
            en(SerializationKind.class, str(o, "serializationKind")),
            new WorkspaceDescriptor(
                str(w.values(), "logicalName"),
                str(w.values(), "logicalRoot"),
                optionalObject(w.values(), "resolvedRoot", DefaultWorkspaceModelCodec::readPath),
                en(CaseSensitivity.class, str(w.values(), "caseSensitivity")),
                str(w.values(), "defaultEncoding"),
                readTrust(o(w.values(), "trust"))),
            objects(o, "modules", DefaultWorkspaceModelCodec::readModule),
            objects(o, "overlays", DefaultWorkspaceModelCodec::readOverlay),
            readFingerprints(o(o, "fingerprints")),
            readLimits(o(o, "limits")),
            extensions);
    return model;
  }

  private static WorkspaceModule readModule(JsonValue.ObjectValue v) {
    var o = v.values();
    return new WorkspaceModule(
        str(o, "id"),
        str(o, "logicalProjectPath"),
        objects(o, "sourceSets", DefaultWorkspaceModelCodec::readSourceSet),
        objects(o, "dependencies", DefaultWorkspaceModelCodec::readDep),
        readToolchain(o(o, "toolchain")),
        objects(o, "propertyMetadata", DefaultWorkspaceModelCodec::readProperty),
        objectValues(o, "extensions"));
  }

  private static SourceSet readSourceSet(JsonValue.ObjectValue v) {
    var o = v.values();
    var p = o(o, "paths");
    var r = o(o, "releases");
    return new SourceSet(
        str(o, "name"),
        paths(o, "javaRoots"),
        paths(o, "teyruRoots"),
        paths(o, "generatedJavaRoots"),
        paths(o, "generatedClassRoots"),
        new CompilationPaths(
            paths(p.values(), "compileClasspath"),
            paths(p.values(), "runtimeClasspath"),
            paths(p.values(), "processorPath"),
            paths(p.values(), "modulePath"),
            paths(p.values(), "sourceJars")),
        new Releases(i(r.values(), "source"), i(r.values(), "target"), i(r.values(), "release")),
        strings(o, "compilerOptions"),
        str(o, "encoding"),
        readTrust(o(o, "trust")),
        optionalString(o, "onDiskSnapshotFingerprint"));
  }

  private static PathRef readPath(JsonValue.ObjectValue v) {
    var o = v.values();
    return new PathRef(
        str(o, "logicalPath"),
        str(o, "relocatableKey"),
        optionalString(o, "fileUri"),
        optionalString(o, "resolvedAbsoluteUri"),
        en(PathKind.class, str(o, "kind")),
        bool(o, "exists"),
        en(SymlinkPolicy.class, str(o, "symlinkPolicy")),
        optionalString(o, "contentSha256"));
  }

  private static TrustPolicy readTrust(JsonValue.ObjectValue v) {
    var o = v.values();
    return new TrustPolicy(
        en(TrustLevel.class, str(o, "level")),
        bool(o, "allowBuildEvaluation"),
        bool(o, "allowProcessors"),
        bool(o, "allowNetwork"),
        bool(o, "allowUserCode"),
        en(DecisionSource.class, str(o, "decisionSource")));
  }

  private static Toolchain readToolchain(JsonValue.ObjectValue v) {
    var o = v.values();
    return new Toolchain(
        readPath(o(o, "executable")),
        str(o, "javaVersion"),
        str(o, "vendor"),
        str(o, "runtimeFingerprint"));
  }

  private static ModuleDependency readDep(JsonValue.ObjectValue v) {
    var o = v.values();
    return new ModuleDependency(
        str(o, "targetModuleId"),
        en(DependencyScope.class, str(o, "scope")),
        en(DependencyKind.class, str(o, "kind")),
        optionalString(o, "targetSourceSet"));
  }

  private static PropertyMetadata readProperty(JsonValue.ObjectValue v) {
    var o = v.values();
    return new PropertyMetadata(
        str(o, "ownerBinaryName"),
        str(o, "propertyName"),
        str(o, "typeDescriptor"),
        optionalString(o, "getterName"),
        optionalString(o, "setterName"),
        bool(o, "readable"),
        bool(o, "writable"),
        i(o, "metadataVersion"),
        str(o, "originFingerprint"));
  }

  private static DocumentOverlay readOverlay(JsonValue.ObjectValue v) {
    var o = v.values();
    return new DocumentOverlay(
        str(o, "documentUri"),
        lng(o, "version"),
        str(o, "contentSha256"),
        str(o, "baseOnDiskFingerprint"),
        en(OverlayState.class, str(o, "state")),
        optionalString(o, "contentUtf8"));
  }

  private static ModelFingerprints readFingerprints(JsonValue.ObjectValue v) {
    var o = v.values();
    return new ModelFingerprints(
        str(o, "algorithm"),
        str(o, "model"),
        str(o, "configuration"),
        str(o, "classpath"),
        str(o, "toolchain"),
        str(o, "options"));
  }

  private static ModelLimits readLimits(JsonValue.ObjectValue v) {
    var o = v.values();
    return new ModelLimits(
        lng(o, "maxDocumentBytes"),
        i(o, "maxModules"),
        i(o, "maxSourceSets"),
        i(o, "maxPaths"),
        i(o, "maxOverlays"),
        i(o, "maxDependencyEdges"));
  }

  private static <T extends Enum<T>> T en(Class<T> t, String x) {
    try {
      return Enum.valueOf(t, x);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("TY-WS-SCHEMA-ENUM " + x);
    }
  }

  private static JsonValue.ObjectValue o(Map<String, JsonValue> m, String k) {
    JsonValue v = m.get(k);
    if (!(v instanceof JsonValue.ObjectValue x))
      throw new IllegalArgumentException("TY-WS-SCHEMA-REQUIRED " + k);
    return x;
  }

  private static String str(Map<String, JsonValue> m, String k) {
    JsonValue v = m.get(k);
    if (!(v instanceof JsonValue.StringValue x))
      throw new IllegalArgumentException("TY-WS-SCHEMA-REQUIRED " + k);
    return x.value();
  }

  private static boolean bool(Map<String, JsonValue> m, String k) {
    JsonValue v = m.get(k);
    if (!(v instanceof JsonValue.BooleanValue x))
      throw new IllegalArgumentException("TY-WS-SCHEMA-REQUIRED " + k);
    return x.value();
  }

  private static int i(Map<String, JsonValue> m, String k) {
    return Math.toIntExact(lng(m, k));
  }

  private static long lng(Map<String, JsonValue> m, String k) {
    JsonValue v = m.get(k);
    if (!(v instanceof JsonValue.NumberValue x))
      throw new IllegalArgumentException("TY-WS-SCHEMA-REQUIRED " + k);
    try {
      return x.value().longValueExact();
    } catch (ArithmeticException e) {
      throw new IllegalArgumentException("TY-WS-SCHEMA-INTEGER " + k);
    }
  }

  private static Optional<String> optionalString(Map<String, JsonValue> m, String k) {
    return m.containsKey(k) ? Optional.of(str(m, k)) : Optional.empty();
  }

  private static <T> Optional<T> optionalObject(
      Map<String, JsonValue> m, String k, java.util.function.Function<JsonValue.ObjectValue, T> f) {
    return m.containsKey(k) ? Optional.of(f.apply(o(m, k))) : Optional.empty();
  }

  private static List<String> strings(Map<String, JsonValue> m, String k) {
    return array(m, k).stream()
        .map(
            v -> {
              if (!(v instanceof JsonValue.StringValue s))
                throw new IllegalArgumentException("TY-WS-SCHEMA-ARRAY " + k);
              return s.value();
            })
        .toList();
  }

  private static List<PathRef> paths(Map<String, JsonValue> m, String k) {
    return objects(m, k, DefaultWorkspaceModelCodec::readPath);
  }

  private static List<JsonValue> array(Map<String, JsonValue> m, String k) {
    JsonValue v = m.get(k);
    if (!(v instanceof JsonValue.ArrayValue a))
      throw new IllegalArgumentException("TY-WS-SCHEMA-REQUIRED " + k);
    return a.values();
  }

  private static <T> List<T> objects(
      Map<String, JsonValue> m, String k, java.util.function.Function<JsonValue.ObjectValue, T> f) {
    return array(m, k).stream()
        .map(
            v -> {
              if (!(v instanceof JsonValue.ObjectValue o))
                throw new IllegalArgumentException("TY-WS-SCHEMA-ARRAY " + k);
              return f.apply(o);
            })
        .toList();
  }

  private static Map<String, JsonValue> objectValues(Map<String, JsonValue> m, String k) {
    return m.containsKey(k) ? o(m, k).values() : Map.of();
  }
}
