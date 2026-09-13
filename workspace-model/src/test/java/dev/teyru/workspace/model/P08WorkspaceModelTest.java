/* SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0 */
package dev.teyru.workspace.model;

import static dev.teyru.workspace.model.WorkspaceModel.*;
import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.junit.jupiter.api.Test;

class P08WorkspaceModelTest {
  private static final String H = "a".repeat(64);

  private static String sha(String text) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new AssertionError(e);
    }
  }

  private static TrustPolicy untrusted() {
    return new TrustPolicy(
        TrustLevel.UNTRUSTED, false, false, false, false, DecisionSource.DEFAULT);
  }

  private static PathRef path(String name) {
    return new PathRef(
        name,
        "sha256:" + H,
        Optional.empty(),
        Optional.empty(),
        PathKind.DIRECTORY,
        false,
        SymlinkPolicy.REJECT,
        Optional.of(H));
  }

  private static WorkspaceModel model() {
    var paths =
        new CompilationPaths(
            List.of(path("lib/a.jar")),
            List.of(),
            List.of(),
            List.of(),
            List.of(path("lib/a-sources.jar")));
    var source =
        new SourceSet(
            "main",
            List.of(path("src/main/java")),
            List.of(path("src/main/teyru")),
            List.of(path("build/generated")),
            List.of(path("build/classes")),
            paths,
            new Releases(21, 21, 21),
            List.of("-Xlint:all"),
            "UTF-8",
            untrusted(),
            Optional.of(H));
    var tool =
        new Toolchain(
            new PathRef(
                "toolchains/jdk/bin/java",
                "sha256:" + H,
                Optional.empty(),
                Optional.empty(),
                PathKind.JDK_EXECUTABLE,
                false,
                SymlinkPolicy.REJECT,
                Optional.of(H)),
            "21.0.11",
            "Eclipse Adoptium",
            H);
    var prop =
        new PropertyMetadata(
            "demo.User",
            "name",
            "Ljava/lang/String;",
            Optional.of("getName"),
            Optional.of("setName"),
            true,
            true,
            1,
            H);
    var module =
        new WorkspaceModule(
            "app",
            ":app",
            List.of(source),
            List.of(),
            tool,
            List.of(prop),
            Map.of("example.dev/value", new JsonValue.StringValue("kept")));
    var blank = new ModelFingerprints("SHA-256", H, H, H, H, H);
    var m =
        new WorkspaceModel(
            new SchemaVersion(1, 0),
            SerializationKind.PORTABLE,
            new WorkspaceDescriptor(
                "demo", ".", Optional.empty(), CaseSensitivity.SENSITIVE, "UTF-8", untrusted()),
            List.of(module),
            List.of(
                new DocumentOverlay(
                    "file:///workspace/src/main/teyru/User.teyru",
                    1,
                    sha("class User {}"),
                    H,
                    OverlayState.DIRTY,
                    Optional.of("class User {}"))),
            blank,
            new ModelLimits(1_000_000, 100, 100, 1000, 100, 1000),
            Map.of("example.dev/root", new JsonValue.BooleanValue(true)));
    return WorkspaceModelFingerprinter.refresh(m);
  }

  @Test
  void canonicalRoundTripIsImmutableAndPreservesAdditiveFields() {
    var m = model();
    var codec = new DefaultWorkspaceModelCodec();
    byte[] one = codec.writePortable(m), two = codec.writePortable(m);
    assertArrayEquals(one, two);
    var read = codec.read(one, WorkspaceReadOptions.defaults());
    assertTrue(read.success(), read.diagnostics().toString());
    assertEquals(m, read.model().orElseThrow());
    assertEquals(
        new JsonValue.BooleanValue(true),
        read.model().orElseThrow().extensions().get("example.dev/root"));
    assertThrows(
        UnsupportedOperationException.class, () -> m.modules().add(m.modules().getFirst()));
    String home = System.getProperty("user.home");
    if (home.length() > 1)
      assertFalse(new String(one, StandardCharsets.UTF_8).contains("file://" + home));
  }

  @Test
  void resolvedAbsoluteUrisDoNotAffectRelocatableFingerprints() {
    var portable = model();
    var original = portable.fingerprints();
    var p = portable.modules().getFirst().toolchain().executable();
    var relocated =
        new PathRef(
            p.logicalPath(),
            p.relocatableKey(),
            Optional.of("file:///different/jdk/bin/java"),
            Optional.of("file:///different/jdk/bin/java"),
            p.kind(),
            p.exists(),
            p.symlinkPolicy(),
            p.contentSha256());
    var mod = portable.modules().getFirst();
    var changed =
        new WorkspaceModule(
            mod.id(),
            mod.logicalProjectPath(),
            mod.sourceSets(),
            mod.dependencies(),
            new Toolchain(
                relocated,
                mod.toolchain().javaVersion(),
                mod.toolchain().vendor(),
                mod.toolchain().runtimeFingerprint()),
            mod.propertyMetadata(),
            mod.extensions());
    var resolved =
        new WorkspaceModel(
            portable.schemaVersion(),
            SerializationKind.RESOLVED,
            portable.workspace(),
            List.of(changed),
            portable.overlays(),
            portable.fingerprints(),
            portable.limits(),
            portable.extensions());
    assertEquals(original, WorkspaceModelFingerprinter.compute(resolved));
    assertThrows(
        IllegalArgumentException.class,
        () -> new DefaultWorkspaceModelCodec().writePortable(resolved));
  }

  @Test
  void validatorRejectsTrustDuplicatesTraversalCyclesAndStaleFingerprint() {
    var base = model();
    var badTrust =
        new TrustPolicy(TrustLevel.UNTRUSTED, true, true, true, true, DecisionSource.USER);
    var ss = base.modules().getFirst().sourceSets().getFirst();
    var badSet =
        new SourceSet(
            ss.name(),
            List.of(path("../escape"), path("../escape")),
            ss.teyruRoots(),
            ss.generatedJavaRoots(),
            ss.generatedClassRoots(),
            ss.paths(),
            ss.releases(),
            ss.compilerOptions(),
            ss.encoding(),
            badTrust,
            ss.onDiskSnapshotFingerprint());
    var tool = base.modules().getFirst().toolchain();
    var a =
        new WorkspaceModule(
            "a",
            ":a",
            List.of(badSet),
            List.of(
                new ModuleDependency(
                    "b", DependencyScope.COMPILE, DependencyKind.BUILD_ORDER, Optional.of("main"))),
            tool,
            List.of(),
            Map.of());
    var b =
        new WorkspaceModule(
            "b",
            ":b",
            List.of(),
            List.of(
                new ModuleDependency(
                    "a", DependencyScope.COMPILE, DependencyKind.BUILD_ORDER, Optional.empty())),
            tool,
            List.of(),
            Map.of());
    var invalid =
        new WorkspaceModel(
            base.schemaVersion(),
            base.serializationKind(),
            base.workspace(),
            List.of(a, b),
            base.overlays(),
            base.fingerprints(),
            base.limits(),
            Map.of());
    var codes =
        new DefaultWorkspaceModelValidator()
            .validate(invalid, ValidationEnvironment.noIo()).stream()
                .map(WorkspaceDiagnostic::code)
                .toList();
    assertTrue(codes.contains("TY-WS-PATH-TRAVERSAL"));
    assertTrue(codes.contains("TY-WS-DUPLICATE-PATH"));
    assertTrue(codes.contains("TY-WS-TRUST-UNTRUSTED"));
    assertTrue(codes.contains("TY-WS-CYCLE-BUILD"));
    assertTrue(codes.contains("TY-WS-STALE-FINGERPRINT"));
  }

  @Test
  void strictReaderRejectsDuplicateKeysUnknownMajorMalformedUtf8AndLimits() {
    var codec = new DefaultWorkspaceModelCodec();
    assertFalse(
        codec
            .read(
                "{\"schemaVersion\":{},\"schemaVersion\":{}}".getBytes(StandardCharsets.UTF_8),
                WorkspaceReadOptions.defaults())
            .success());
    assertFalse(
        codec
            .read(
                "{\"schemaVersion\":{\"major\":2,\"minor\":0}}".getBytes(StandardCharsets.UTF_8),
                WorkspaceReadOptions.defaults())
            .success());
    assertFalse(
        codec.read(new byte[] {(byte) 0xc3, 0x28}, WorkspaceReadOptions.defaults()).success());
    assertFalse(
        codec
            .read("{}".getBytes(StandardCharsets.UTF_8), new WorkspaceReadOptions(1, 10, 10))
            .success());
  }

  @Test
  void newerMinorUnknownAdditiveFieldIsAcceptedAndRetainedInRawDocument() {
    var codec = new DefaultWorkspaceModelCodec();
    String json = new String(codec.writePortable(model()), StandardCharsets.UTF_8);
    json =
        json.replace("\"minor\":0", "\"minor\":7")
            .replaceFirst("\\{", "{\"futureFeature\":{\"enabled\":true},");
    WorkspaceReadResult result =
        codec.read(json.getBytes(StandardCharsets.UTF_8), WorkspaceReadOptions.defaults());
    assertTrue(result.success(), result.diagnostics().toString());
    assertEquals(7, result.model().orElseThrow().schemaVersion().minor());
    assertTrue(result.rawDocument().values().containsKey("futureFeature"));
    String rewritten =
        new String(codec.writePortable(result.model().orElseThrow()), StandardCharsets.UTF_8);
    assertTrue(rewritten.contains("\"futureFeature\":{\"enabled\":true}"));
  }

  @Test
  void portableWriterRejectsAbsoluteAndUriLikeLogicalPaths() {
    var base = model();
    for (String unsafe : List.of("/home/user/x", "C:/jdk/bin/java", "src?q=x", "x#y", "a.jar!/X")) {
      var p = path(unsafe);
      var mod = base.modules().getFirst();
      var changed =
          new WorkspaceModule(
              mod.id(),
              mod.logicalProjectPath(),
              mod.sourceSets(),
              mod.dependencies(),
              new Toolchain(p, "21.0.11", "vendor", H),
              mod.propertyMetadata(),
              mod.extensions());
      var candidate =
          new WorkspaceModel(
              base.schemaVersion(),
              base.serializationKind(),
              base.workspace(),
              List.of(changed),
              base.overlays(),
              base.fingerprints(),
              base.limits(),
              base.extensions());
      assertThrows(
          IllegalArgumentException.class,
          () -> new DefaultWorkspaceModelCodec().writePortable(candidate),
          unsafe);
    }
  }

  @Test
  void validatorRejectsProcessorToolchainCaseOverlapUriAndOverlayFailures() {
    var base = model();
    var old = base.modules().getFirst();
    var s = old.sourceSets().getFirst();
    var paths =
        new CompilationPaths(
            s.paths().compileClasspath(),
            s.paths().runtimeClasspath(),
            List.of(path("processors/p.jar")),
            s.paths().modulePath(),
            s.paths().sourceJars());
    var badSet =
        new SourceSet(
            s.name(),
            List.of(path("src/Main"), path("src/main"), path("src/main/nested")),
            s.teyruRoots(),
            s.generatedJavaRoots(),
            s.generatedClassRoots(),
            paths,
            new Releases(25, 25, 25),
            s.compilerOptions(),
            s.encoding(),
            untrusted(),
            s.onDiskSnapshotFingerprint());
    var badModule =
        new WorkspaceModule(
            old.id(),
            old.logicalProjectPath(),
            List.of(badSet),
            old.dependencies(),
            old.toolchain(),
            old.propertyMetadata(),
            old.extensions());
    var overlay =
        new DocumentOverlay(
            "file://host/a.teyru?x",
            -1,
            H,
            "b".repeat(64),
            OverlayState.DIRTY,
            Optional.of("different"));
    var candidate =
        new WorkspaceModel(
            base.schemaVersion(),
            base.serializationKind(),
            new WorkspaceDescriptor(
                "demo", ".", Optional.empty(), CaseSensitivity.UNKNOWN, "UTF-8", untrusted()),
            List.of(badModule),
            List.of(overlay),
            base.fingerprints(),
            base.limits(),
            base.extensions());
    var codes =
        new DefaultWorkspaceModelValidator()
            .validate(candidate, ValidationEnvironment.noIo()).stream()
                .map(WorkspaceDiagnostic::code)
                .collect(java.util.stream.Collectors.toSet());
    assertTrue(codes.contains("TY-WS-TRUST-PROCESSOR-PATH"));
    assertTrue(codes.contains("TY-WS-SCHEMA-TOOLCHAIN-RELEASE"));
    assertTrue(codes.contains("TY-WS-PATH-CASE-AMBIGUOUS"));
    assertTrue(codes.contains("TY-WS-PATH-SOURCE-OVERLAP"));
    assertTrue(codes.contains("TY-WS-PATH-OVERLAY-URI"));
    assertTrue(codes.contains("TY-WS-OVERLAY-VERSION"));
    assertTrue(codes.contains("TY-WS-OVERLAY-CONTENT-HASH"));
    assertTrue(codes.contains("TY-WS-STALE-OVERLAY-BASE"));
  }

  @Test
  void rootsAreSetLikeSnapshotAndOverlayAffectExpectedFingerprints() {
    var base = model();
    var mod = base.modules().getFirst();
    var s = mod.sourceSets().getFirst();
    var reversed =
        new SourceSet(
            s.name(),
            s.javaRoots(),
            List.of(path("z"), path("a")),
            s.generatedJavaRoots(),
            s.generatedClassRoots(),
            s.paths(),
            s.releases(),
            s.compilerOptions(),
            s.encoding(),
            s.trust(),
            s.onDiskSnapshotFingerprint());
    var forward =
        new SourceSet(
            s.name(),
            s.javaRoots(),
            List.of(path("a"), path("z")),
            s.generatedJavaRoots(),
            s.generatedClassRoots(),
            s.paths(),
            s.releases(),
            s.compilerOptions(),
            s.encoding(),
            s.trust(),
            s.onDiskSnapshotFingerprint());
    assertEquals(fingerprintWith(base, mod, reversed), fingerprintWith(base, mod, forward));
    var snapshot =
        new SourceSet(
            s.name(),
            s.javaRoots(),
            s.teyruRoots(),
            s.generatedJavaRoots(),
            s.generatedClassRoots(),
            s.paths(),
            s.releases(),
            s.compilerOptions(),
            s.encoding(),
            s.trust(),
            Optional.of("b".repeat(64)));
    assertNotEquals(base.fingerprints(), fingerprintWith(base, mod, snapshot));
    var noOverlay =
        new WorkspaceModel(
            base.schemaVersion(),
            base.serializationKind(),
            base.workspace(),
            base.modules(),
            List.of(),
            base.fingerprints(),
            base.limits(),
            base.extensions());
    assertEquals(
        WorkspaceModelFingerprinter.compute(base), WorkspaceModelFingerprinter.compute(noOverlay));
    assertNotEquals(
        WorkspaceModelFingerprinter.analysisFingerprint(base),
        WorkspaceModelFingerprinter.analysisFingerprint(noOverlay));
  }

  @Test
  void everySemanticFamilyInvalidatesItsFingerprint() {
    var base = model();
    var mod = base.modules().getFirst();
    var s = mod.sourceSets().getFirst();
    var baseline = base.fingerprints();
    var changedOptions =
        new SourceSet(
            s.name(),
            s.javaRoots(),
            s.teyruRoots(),
            s.generatedJavaRoots(),
            s.generatedClassRoots(),
            s.paths(),
            s.releases(),
            List.of("-parameters"),
            s.encoding(),
            s.trust(),
            s.onDiskSnapshotFingerprint());
    assertNotEquals(baseline, fingerprintWith(base, mod, changedOptions));
    var changedCp =
        new CompilationPaths(
            List.of(path("lib/other.jar")),
            s.paths().runtimeClasspath(),
            s.paths().processorPath(),
            s.paths().modulePath(),
            s.paths().sourceJars());
    var cpSet =
        new SourceSet(
            s.name(),
            s.javaRoots(),
            s.teyruRoots(),
            s.generatedJavaRoots(),
            s.generatedClassRoots(),
            changedCp,
            s.releases(),
            s.compilerOptions(),
            s.encoding(),
            s.trust(),
            s.onDiskSnapshotFingerprint());
    assertNotEquals(baseline, fingerprintWith(base, mod, cpSet));
    var trustSet =
        new SourceSet(
            s.name(),
            s.javaRoots(),
            s.teyruRoots(),
            s.generatedJavaRoots(),
            s.generatedClassRoots(),
            s.paths(),
            s.releases(),
            s.compilerOptions(),
            s.encoding(),
            new TrustPolicy(
                TrustLevel.TRUSTED_BUILD, true, false, false, true, DecisionSource.USER),
            s.onDiskSnapshotFingerprint());
    assertNotEquals(baseline, fingerprintWith(base, mod, trustSet));
    var changedTool =
        new WorkspaceModule(
            mod.id(),
            mod.logicalProjectPath(),
            mod.sourceSets(),
            mod.dependencies(),
            new Toolchain(
                mod.toolchain().executable(), "25.0.1", mod.toolchain().vendor(), "b".repeat(64)),
            mod.propertyMetadata(),
            mod.extensions());
    assertNotEquals(baseline, WorkspaceModelFingerprinter.compute(withModule(base, changedTool)));
    var changedProperty =
        new PropertyMetadata(
            "demo.User", "other", "I", Optional.empty(), Optional.empty(), true, false, 1, H);
    var propertyModule =
        new WorkspaceModule(
            mod.id(),
            mod.logicalProjectPath(),
            mod.sourceSets(),
            mod.dependencies(),
            mod.toolchain(),
            List.of(changedProperty),
            mod.extensions());
    assertNotEquals(
        baseline, WorkspaceModelFingerprinter.compute(withModule(base, propertyModule)));
  }

  private static WorkspaceModel withModule(WorkspaceModel base, WorkspaceModule module) {
    return new WorkspaceModel(
        base.schemaVersion(),
        base.serializationKind(),
        base.workspace(),
        List.of(module),
        base.overlays(),
        base.fingerprints(),
        base.limits(),
        base.extensions());
  }

  private static ModelFingerprints fingerprintWith(
      WorkspaceModel base, WorkspaceModule mod, SourceSet set) {
    var changed =
        new WorkspaceModule(
            mod.id(),
            mod.logicalProjectPath(),
            List.of(set),
            mod.dependencies(),
            mod.toolchain(),
            mod.propertyMetadata(),
            mod.extensions());
    return WorkspaceModelFingerprinter.compute(
        new WorkspaceModel(
            base.schemaVersion(),
            base.serializationKind(),
            base.workspace(),
            List.of(changed),
            base.overlays(),
            base.fingerprints(),
            base.limits(),
            base.extensions()));
  }

  @Test
  void sourceVisibilitySccAndMissingTargetSourceSetAreDistinct() {
    var base = model();
    var tool = base.modules().getFirst().toolchain();
    var ss = base.modules().getFirst().sourceSets();
    var a =
        new WorkspaceModule(
            "a",
            ":a",
            ss,
            List.of(
                new ModuleDependency(
                    "b",
                    DependencyScope.COMPILE,
                    DependencyKind.SOURCE_VISIBILITY,
                    Optional.of("missing"))),
            tool,
            List.of(),
            Map.of());
    var b =
        new WorkspaceModule(
            "b",
            ":b",
            ss,
            List.of(
                new ModuleDependency(
                    "a",
                    DependencyScope.COMPILE,
                    DependencyKind.SOURCE_VISIBILITY,
                    Optional.of("main"))),
            tool,
            List.of(),
            Map.of());
    var m =
        new WorkspaceModel(
            base.schemaVersion(),
            base.serializationKind(),
            base.workspace(),
            List.of(a, b),
            List.of(),
            base.fingerprints(),
            base.limits(),
            Map.of());
    var codes =
        new DefaultWorkspaceModelValidator()
            .validate(m, ValidationEnvironment.noIo()).stream()
                .map(WorkspaceDiagnostic::code)
                .toList();
    assertTrue(codes.contains("TY-WS-SCC-SOURCE-VISIBILITY"));
    assertTrue(codes.contains("TY-WS-DEPENDENCY-SOURCESET"));
    assertFalse(codes.contains("TY-WS-CYCLE-BUILD"));
  }

  @Test
  void publicApiGoldenAndEveryProductionClassfileStayPlatformNeutral() throws Exception {
    String actual = publicApiSnapshot();
    String expected;
    try (var in = getClass().getResourceAsStream("/workspace-public-api.txt")) {
      assertNotNull(in);
      expected = new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
    assertEquals(expected, actual, "intentional public DTO changes require reviewed golden update");

    var root =
        Path.of(WorkspaceModel.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    try (var files = Files.walk(root.resolve("dev/teyru/workspace/model"))) {
      for (Path file : files.filter(x -> x.toString().endsWith(".class")).toList()) {
        String bytes = new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1);
        for (String forbidden : List.of("org/gradle", "com/intellij", "org/eclipse/lsp4j"))
          assertFalse(bytes.contains(forbidden), file + " references " + forbidden);
      }
    }
  }

  @Test
  void apiDiffGateDetectsAdditionDeletionAndSignatureChange() {
    var baseline = Set.of("TYPE A", "METHOD A#m():void", "CTOR A()");
    assertEquals(
        Set.of("METHOD A#n():void"),
        apiDifference(baseline, union(baseline, "METHOD A#n():void")).added());
    assertEquals(
        Set.of("CTOR A()"),
        apiDifference(baseline, Set.of("TYPE A", "METHOD A#m():void")).removed());
    var changed = apiDifference(baseline, Set.of("TYPE A", "METHOD A#m():int", "CTOR A()"));
    assertEquals(Set.of("METHOD A#m():int"), changed.added());
    assertEquals(Set.of("METHOD A#m():void"), changed.removed());
  }

  @Test
  void portableGoldenHasNoAbsolutePathLeaks() throws Exception {
    String actual =
        new String(new DefaultWorkspaceModelCodec().writePortable(model()), StandardCharsets.UTF_8);
    String expected;
    try (var in = getClass().getResourceAsStream("/workspace-portable-v1.json")) {
      assertNotNull(in);
      expected = new String(in.readAllBytes(), StandardCharsets.UTF_8).stripTrailing();
    }
    assertEquals(expected, actual.stripTrailing());
    for (String forbidden :
        List.of(
            "/home/",
            "\\\\",
            "C:/",
            "C:\\\\",
            "/opt/jdk",
            "file://" + System.getProperty("user.home"),
            "\"logicalPath\":\"" + System.getProperty("user.home")))
      if (!forbidden.isEmpty()) assertFalse(actual.contains(forbidden), forbidden);
  }

  private static String publicApiSnapshot() throws Exception {
    Path root =
        Path.of(WorkspaceModel.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    var lines = new TreeSet<String>();
    try (var files = Files.walk(root.resolve("dev/teyru/workspace/model"))) {
      for (Path file : files.filter(x -> x.toString().endsWith(".class")).toList()) {
        String relative =
            root.relativize(file).toString().replace(FileSystems.getDefault().getSeparator(), ".");
        Class<?> type =
            Class.forName(
                relative.substring(0, relative.length() - 6),
                false,
                P08WorkspaceModelTest.class.getClassLoader());
        if (apiVisible(type.getModifiers())) describe(type, lines);
      }
    }
    String body = String.join("\n", lines) + "\n";
    return "# Reviewed P08-R02 baseline: frozen workspace-model v1 DTO/codec/validation API\nsha256 "
        + sha(body)
        + "\n"
        + body;
  }

  private static void describe(Class<?> type, Set<String> lines) {
    String name = type.getName();
    String kind =
        type.isAnnotation()
            ? "annotation"
            : type.isEnum()
                ? "enum"
                : type.isRecord() ? "record" : type.isInterface() ? "interface" : "class";
    lines.add(
        "TYPE "
            + visibility(type.getModifiers())
            + " "
            + kind
            + " "
            + name
            + " extends="
            + (type.getGenericSuperclass() == null
                ? "-"
                : type.getGenericSuperclass().getTypeName())
            + " implements="
            + Arrays.stream(type.getGenericInterfaces())
                .map(java.lang.reflect.Type::getTypeName)
                .sorted()
                .toList()
            + " permits="
            + (type.getPermittedSubclasses() == null
                ? List.of()
                : Arrays.stream(type.getPermittedSubclasses())
                    .map(Class::getName)
                    .sorted()
                    .toList()));
    if (type.isRecord())
      for (RecordComponent c : type.getRecordComponents())
        lines.add("COMPONENT " + name + "#" + c.getName() + ":" + c.getGenericType().getTypeName());
    for (Field f : type.getDeclaredFields())
      if (apiVisible(f.getModifiers()) && !f.isSynthetic())
        lines.add(
            "FIELD "
                + visibility(f.getModifiers())
                + " "
                + name
                + "#"
                + f.getName()
                + ":"
                + f.getGenericType().getTypeName());
    for (Constructor<?> c : type.getDeclaredConstructors())
      if (apiVisible(c.getModifiers()) && !c.isSynthetic())
        lines.add(
            "CTOR "
                + visibility(c.getModifiers())
                + " "
                + name
                + "("
                + types(c.getGenericParameterTypes())
                + ") throws="
                + types(c.getGenericExceptionTypes()));
    for (Method m : type.getDeclaredMethods())
      if (apiVisible(m.getModifiers()) && !m.isSynthetic() && !m.isBridge())
        lines.add(
            "METHOD "
                + visibility(m.getModifiers())
                + " "
                + name
                + "#"
                + m.getName()
                + "("
                + types(m.getGenericParameterTypes())
                + "):"
                + m.getGenericReturnType().getTypeName()
                + " throws="
                + types(m.getGenericExceptionTypes()));
  }

  private static boolean apiVisible(int modifiers) {
    return Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers);
  }

  private static String visibility(int modifiers) {
    var words = new ArrayList<String>();
    for (int bit :
        new int[] {
          Modifier.PUBLIC, Modifier.PROTECTED, Modifier.STATIC, Modifier.FINAL, Modifier.ABSTRACT
        }) if ((modifiers & bit) != 0) words.add(Modifier.toString(bit));
    return String.join(" ", words);
  }

  private static String types(java.lang.reflect.Type[] types) {
    return Arrays.stream(types)
        .map(java.lang.reflect.Type::getTypeName)
        .collect(java.util.stream.Collectors.joining(","));
  }

  private record ApiDifference(Set<String> added, Set<String> removed) {}

  private static ApiDifference apiDifference(Set<String> expected, Set<String> actual) {
    var added = new TreeSet<>(actual);
    added.removeAll(expected);
    var removed = new TreeSet<>(expected);
    removed.removeAll(actual);
    return new ApiDifference(Set.copyOf(added), Set.copyOf(removed));
  }

  private static Set<String> union(Set<String> values, String addition) {
    var result = new HashSet<>(values);
    result.add(addition);
    return Set.copyOf(result);
  }
}
