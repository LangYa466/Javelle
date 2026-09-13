/* SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0 */
package dev.teyru.workspace.model;

import static dev.teyru.workspace.model.WorkspaceModel.*;

import java.io.IOException;
import java.net.*;
import java.nio.file.*;
import java.text.Normalizer;
import java.util.*;

public final class DefaultWorkspaceModelValidator implements WorkspaceModelValidator {
  public List<WorkspaceDiagnostic> validate(WorkspaceModel m, ValidationEnvironment env) {
    var d = new ArrayList<WorkspaceDiagnostic>();
    if (m.schemaVersion().major() != 1)
      add(d, "TY-WS-SCHEMA-MAJOR", "/schemaVersion/major", "unsupported major");
    if (m.modules().size() > m.limits().maxModules())
      add(d, "TY-WS-LIMIT-MODULES", "/modules", "module limit");
    if (m.overlays().size() > m.limits().maxOverlays())
      add(d, "TY-WS-LIMIT-OVERLAYS", "/overlays", "overlay limit");
    if (!(m.workspace().logicalRoot().equals(".")
        || isSafeLogicalPath(m.workspace().logicalRoot())))
      add(d, "TY-WS-PATH-TRAVERSAL", "/workspace/logicalRoot", "unsafe workspace logical root");
    if (!m.workspace().defaultEncoding().equals("UTF-8"))
      add(d, "TY-WS-SCHEMA-ENCODING", "/workspace/defaultEncoding", "UTF-8 required");
    validateTrust(m.workspace().trust(), "/workspace/trust", d);
    Set<String> ids = new HashSet<>();
    int sourceSets = 0, paths = 0, edges = 0;
    Map<String, Set<String>> graph = new TreeMap<>(), visibility = new TreeMap<>();
    for (int mi = 0; mi < m.modules().size(); mi++) {
      var mod = m.modules().get(mi);
      String mp = "/modules/" + mi;
      if (!mod.id().matches("^[A-Za-z0-9_.:-]{1,256}$"))
        add(d, "TY-WS-SCHEMA-MODULE-ID", mp + "/id", "invalid module id");
      if (!mod.logicalProjectPath().matches("^:(?:[A-Za-z0-9_.-]+(?::[A-Za-z0-9_.-]+)*)?$"))
        add(d, "TY-WS-PATH-PROJECT", mp + "/logicalProjectPath", "invalid project identity");
      if (!ids.add(mod.id()))
        add(d, "TY-WS-DUPLICATE-MODULE", mp + "/id", "duplicate module " + mod.id());
      graph.putIfAbsent(mod.id(), new TreeSet<>());
      visibility.putIfAbsent(mod.id(), new TreeSet<>());
      Set<String> ss = new HashSet<>(), props = new HashSet<>();
      int toolchainMajor = javaMajor(mod.toolchain().javaVersion());
      if (toolchainMajor != 21 && toolchainMajor != 25)
        add(d, "TY-WS-SCHEMA-TOOLCHAIN", mp + "/toolchain/javaVersion", "unsupported Java version");
      validatePath(mod.toolchain().executable(), m, env, mp + "/toolchain/executable", d);
      for (int si = 0; si < mod.sourceSets().size(); si++) {
        sourceSets++;
        var s = mod.sourceSets().get(si);
        String sp = mp + "/sourceSets/" + si;
        if (!ss.add(s.name()))
          add(d, "TY-WS-DUPLICATE-SOURCESET", sp + "/name", "duplicate source set");
        validateTrust(s.trust(), sp + "/trust", d);
        if (!s.encoding().equals("UTF-8"))
          add(d, "TY-WS-SCHEMA-ENCODING", sp + "/encoding", "UTF-8 required");
        if ((s.releases().release() != 21 && s.releases().release() != 25)
            || s.releases().source() > s.releases().release()
            || s.releases().target() > s.releases().release())
          add(d, "TY-WS-SCHEMA-RELEASE", sp + "/releases", "inconsistent release");
        if (s.releases().release() > toolchainMajor)
          add(
              d,
              "TY-WS-SCHEMA-TOOLCHAIN-RELEASE",
              sp + "/releases/release",
              "release exceeds toolchain");
        if (!s.paths().processorPath().isEmpty()
            && (!s.trust().allowProcessors()
                || s.trust().level() != TrustLevel.TRUSTED_PROCESSORS
                || s.trust().decisionSource() == DecisionSource.DEFAULT))
          add(
              d,
              "TY-WS-TRUST-PROCESSOR-PATH",
              sp + "/paths/processorPath",
              "processor path requires explicit processor trust");
        var seen = new HashSet<String>();
        var folded = new HashMap<String, String>();
        for (PathRef p : all(s)) {
          paths++;
          String key = caseKey(p.logicalPath(), m.workspace().caseSensitivity());
          if (!seen.add(key))
            add(d, "TY-WS-DUPLICATE-PATH", sp, "duplicate path " + p.logicalPath());
          if (m.workspace().caseSensitivity() == CaseSensitivity.UNKNOWN) {
            String f = caseKey(p.logicalPath(), CaseSensitivity.INSENSITIVE);
            String prior = folded.putIfAbsent(f, p.logicalPath());
            if (prior != null && !prior.equals(p.logicalPath()))
              add(
                  d,
                  "TY-WS-PATH-CASE-AMBIGUOUS",
                  sp,
                  "case sensitivity required for " + p.logicalPath());
          }
          validatePath(p, m, env, sp, d);
        }
        validateSourceOverlaps(s, m.workspace().caseSensitivity(), sp, d);
      }
      for (var p : mod.propertyMetadata()) {
        String k = p.ownerBinaryName() + "#" + p.propertyName();
        if (!props.add(k))
          add(d, "TY-WS-DUPLICATE-PROPERTY", mp + "/propertyMetadata", "duplicate property " + k);
        if (!sha(p.originFingerprint()))
          add(d, "TY-WS-FINGERPRINT-PROPERTY", mp + "/propertyMetadata", "bad fingerprint");
      }
      for (var dep : mod.dependencies()) {
        edges++;
        if (dep.kind() == DependencyKind.BUILD_ORDER) graph.get(mod.id()).add(dep.targetModuleId());
        if (dep.kind() == DependencyKind.SOURCE_VISIBILITY)
          visibility.get(mod.id()).add(dep.targetModuleId());
        if (dep.targetSourceSet().isPresent()) {
          var target =
              m.modules().stream().filter(x -> x.id().equals(dep.targetModuleId())).findFirst();
          if (target.isPresent()
              && target.get().sourceSets().stream()
                  .noneMatch(x -> x.name().equals(dep.targetSourceSet().orElseThrow())))
            add(d, "TY-WS-DEPENDENCY-SOURCESET", mp + "/dependencies", "missing target source set");
        }
      }
    }
    if (sourceSets > m.limits().maxSourceSets())
      add(d, "TY-WS-LIMIT-SOURCESETS", "/modules", "source-set limit");
    if (paths > m.limits().maxPaths()) add(d, "TY-WS-LIMIT-PATHS", "/modules", "path limit");
    if (edges > m.limits().maxDependencyEdges())
      add(d, "TY-WS-LIMIT-EDGES", "/modules", "edge limit");
    for (var e : graph.entrySet())
      for (String target : e.getValue())
        if (!graph.containsKey(target))
          add(d, "TY-WS-CYCLE-MISSING", "/modules", "missing dependency " + target);
    cycle(graph).ifPresent(c -> add(d, "TY-WS-CYCLE-BUILD", "/modules", String.join(" -> ", c)));
    for (var component : stronglyConnected(visibility))
      add(d, "TY-WS-SCC-SOURCE-VISIBILITY", "/modules", String.join(" -> ", component));
    Set<String> snapshots = new HashSet<>();
    for (var mod : m.modules())
      for (var s : mod.sourceSets()) s.onDiskSnapshotFingerprint().ifPresent(snapshots::add);
    Set<String> overlay = new HashSet<>();
    for (int i = 0; i < m.overlays().size(); i++) {
      var o = m.overlays().get(i);
      if (!overlay.add(o.documentUri()))
        add(d, "TY-WS-DUPLICATE-OVERLAY", "/overlays/" + i, "duplicate overlay");
      if (o.contentUtf8()
          .map(
              x ->
                  x.getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                      > m.limits().maxDocumentBytes())
          .orElse(false)) add(d, "TY-WS-LIMIT-OVERLAY", "/overlays/" + i, "overlay too large");
      if (!validDocumentUri(o.documentUri()))
        add(d, "TY-WS-PATH-OVERLAY-URI", "/overlays/" + i + "/documentUri", "invalid document URI");
      if (o.version() < 0)
        add(d, "TY-WS-OVERLAY-VERSION", "/overlays/" + i + "/version", "negative overlay version");
      if (!sha(o.contentSha256()) || !sha(o.baseOnDiskFingerprint()))
        add(d, "TY-WS-FINGERPRINT-OVERLAY", "/overlays/" + i, "invalid overlay fingerprint");
      if (o.contentUtf8().isPresent()
          && !digest(o.contentUtf8().orElseThrow()).equals(o.contentSha256()))
        add(
            d,
            "TY-WS-OVERLAY-CONTENT-HASH",
            "/overlays/" + i + "/contentSha256",
            "content hash mismatch");
      if (!snapshots.contains(o.baseOnDiskFingerprint()))
        add(
            d,
            "TY-WS-STALE-OVERLAY-BASE",
            "/overlays/" + i + "/baseOnDiskFingerprint",
            "overlay base requires refresh");
    }
    if (!m.fingerprints().algorithm().equals("SHA-256")
        || !sha(m.fingerprints().model())
        || !sha(m.fingerprints().configuration())
        || !sha(m.fingerprints().classpath())
        || !sha(m.fingerprints().toolchain())
        || !sha(m.fingerprints().options()))
      add(d, "TY-WS-FINGERPRINT-INVALID", "/fingerprints", "invalid SHA-256 fingerprints");
    else if (!m.fingerprints().equals(WorkspaceModelFingerprinter.compute(m)))
      add(d, "TY-WS-STALE-FINGERPRINT", "/fingerprints", "model refresh required");
    return List.copyOf(d);
  }

  private static void validateTrust(TrustPolicy t, String p, List<WorkspaceDiagnostic> d) {
    if (t.level() == TrustLevel.UNTRUSTED
        && (t.allowBuildEvaluation()
            || t.allowProcessors()
            || t.allowNetwork()
            || t.allowUserCode()))
      add(d, "TY-WS-TRUST-UNTRUSTED", p, "untrusted policy enables execution");
    if (t.allowProcessors() && t.level() != TrustLevel.TRUSTED_PROCESSORS)
      add(d, "TY-WS-TRUST-PROCESSOR", p, "processor permission requires TRUSTED_PROCESSORS");
  }

  public static boolean isSafeLogicalPath(String logicalPath) {
    String x;
    try {
      x = URLDecoder.decode(logicalPath, java.nio.charset.StandardCharsets.UTF_8);
    } catch (IllegalArgumentException e) {
      return false;
    }
    return !(x.isBlank()
        || x.startsWith("/")
        || x.startsWith("//")
        || x.contains("\\")
        || x.contains("\0")
        || x.contains("//")
        || x.contains("?")
        || x.contains("#")
        || x.contains("!/")
        || x.length() > 4096
        || Arrays.stream(x.split("/", -1))
            .anyMatch(q -> q.equals(".") || q.equals("..") || q.isEmpty())
        || x.matches("^[A-Za-z]:.*"));
  }

  private static void validatePath(
      PathRef p,
      WorkspaceModel m,
      ValidationEnvironment env,
      String ptr,
      List<WorkspaceDiagnostic> d) {
    String x;
    try {
      x = URLDecoder.decode(p.logicalPath(), java.nio.charset.StandardCharsets.UTF_8);
    } catch (IllegalArgumentException e) {
      add(d, "TY-WS-PATH-ENCODING", ptr, "bad percent encoding");
      return;
    }
    if (!isSafeLogicalPath(p.logicalPath()))
      add(d, "TY-WS-PATH-TRAVERSAL", ptr, "unsafe logical path");
    if (m.serializationKind() == SerializationKind.PORTABLE
        && (p.fileUri().isPresent() || p.resolvedAbsoluteUri().isPresent()))
      add(d, "TY-WS-PATH-PORTABLE", ptr, "absolute URI in portable model");
    for (String u : List.of(p.fileUri().orElse(""), p.resolvedAbsoluteUri().orElse("")))
      if (!u.isEmpty())
        try {
          URI uri = URI.create(u);
          if (!uri.isAbsolute() || !uri.getScheme().equals("file"))
            throw new IllegalArgumentException();
          if (uri.getQuery() != null
              || uri.getFragment() != null
              || uri.getUserInfo() != null
              || (uri.getAuthority() != null && !uri.getAuthority().isEmpty())
              || uri.getPath() == null
              || !uri.getPath().startsWith("/")
              || uri.getPath().contains("!/")) throw new IllegalArgumentException();
        } catch (IllegalArgumentException e) {
          add(d, "TY-WS-PATH-URI", ptr, "invalid file URI");
        }
    if (!p.relocatableKey().matches("sha256:[0-9a-f]{64}"))
      add(d, "TY-WS-FINGERPRINT-PATH", ptr, "invalid relocatable key");
    if (env.inspectFileSystem() && p.resolvedAbsoluteUri().isPresent())
      try {
        Path q = Path.of(URI.create(p.resolvedAbsoluteUri().orElseThrow()));
        if (hasSymlink(q) && p.symlinkPolicy() == SymlinkPolicy.REJECT)
          add(d, "TY-WS-PATH-SYMLINK", ptr, "symlink rejected");
        if (env.resolvedWorkspaceRoot().isPresent()
            && p.symlinkPolicy() != SymlinkPolicy.ALLOW_DECLARED_EXTERNAL
            && !q.toRealPath().startsWith(env.resolvedWorkspaceRoot().orElseThrow().toRealPath()))
          add(d, "TY-WS-PATH-OUTSIDE", ptr, "path escapes workspace");
      } catch (IOException | RuntimeException e) {
        if (p.exists()) add(d, "TY-WS-PATH-MISSING", ptr, "resolved path unavailable");
      }
  }

  private static boolean hasSymlink(Path p) {
    for (Path q = p.toAbsolutePath(); q != null; q = q.getParent())
      if (Files.isSymbolicLink(q)) return true;
    return false;
  }

  private static String caseKey(String x, CaseSensitivity c) {
    String n = Normalizer.normalize(x, Normalizer.Form.NFC);
    return c == CaseSensitivity.INSENSITIVE ? n.toLowerCase(Locale.ROOT) : n;
  }

  private static void validateSourceOverlaps(
      SourceSet s, CaseSensitivity c, String ptr, List<WorkspaceDiagnostic> d) {
    var roots = new ArrayList<PathRef>();
    roots.addAll(s.javaRoots());
    roots.addAll(s.teyruRoots());
    roots.addAll(s.generatedJavaRoots());
    for (int i = 0; i < roots.size(); i++)
      for (int j = i + 1; j < roots.size(); j++) {
        String a = caseKey(roots.get(i).logicalPath(), c),
            b = caseKey(roots.get(j).logicalPath(), c);
        if (a.startsWith(b + "/") || b.startsWith(a + "/"))
          add(d, "TY-WS-PATH-SOURCE-OVERLAP", ptr, "overlapping source roots");
      }
  }

  private static int javaMajor(String version) {
    try {
      return Integer.parseInt(version.split("[^0-9]", 2)[0]);
    } catch (RuntimeException e) {
      return -1;
    }
  }

  private static boolean validDocumentUri(String value) {
    try {
      URI u = URI.create(value);
      return u.isAbsolute()
          && u.getFragment() == null
          && u.getQuery() == null
          && u.getUserInfo() == null
          && !("file".equals(u.getScheme())
              && u.getAuthority() != null
              && !u.getAuthority().isEmpty())
          && u.normalize().toString().equals(value)
          && !value.contains("!/");
    } catch (RuntimeException e) {
      return false;
    }
  }

  private static String digest(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              java.security.MessageDigest.getInstance("SHA-256")
                  .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    }
  }

  private static List<List<String>> stronglyConnected(Map<String, Set<String>> graph) {
    var result = new ArrayList<List<String>>();
    var visited = new HashSet<String>();
    for (String start : graph.keySet()) {
      if (!visited.add(start)) continue;
      var forward = reachable(start, graph);
      var reverseGraph = new TreeMap<String, Set<String>>();
      graph.keySet().forEach(k -> reverseGraph.put(k, new TreeSet<>()));
      graph.forEach(
          (a, xs) -> xs.forEach(b -> reverseGraph.computeIfAbsent(b, k -> new TreeSet<>()).add(a)));
      forward.retainAll(reachable(start, reverseGraph));
      visited.addAll(forward);
      if (forward.size() > 1 || graph.getOrDefault(start, Set.of()).contains(start)) {
        var stable = new ArrayList<>(forward);
        Collections.sort(stable);
        stable.add(stable.getFirst());
        result.add(stable);
      }
    }
    return result;
  }

  private static Set<String> reachable(String start, Map<String, Set<String>> graph) {
    var seen = new TreeSet<String>();
    var todo = new ArrayDeque<String>();
    todo.add(start);
    while (!todo.isEmpty()) {
      String n = todo.remove();
      if (seen.add(n)) todo.addAll(graph.getOrDefault(n, Set.of()));
    }
    return seen;
  }

  private static Optional<List<String>> cycle(Map<String, Set<String>> g) {
    Set<String> done = new HashSet<>();
    List<String> stack = new ArrayList<>();
    for (String n : g.keySet()) {
      var c = dfs(n, g, done, stack);
      if (c.isPresent()) return c;
    }
    return Optional.empty();
  }

  private static Optional<List<String>> dfs(
      String n, Map<String, Set<String>> g, Set<String> d, List<String> s) {
    int at = s.indexOf(n);
    if (at >= 0) {
      var c = new ArrayList<>(s.subList(at, s.size()));
      c.add(n);
      return Optional.of(c);
    }
    if (!d.add(n)) return Optional.empty();
    s.add(n);
    for (String x : g.getOrDefault(n, Set.of())) {
      var c = dfs(x, g, d, s);
      if (c.isPresent()) return c;
    }
    s.remove(s.size() - 1);
    return Optional.empty();
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

  private static boolean sha(String x) {
    return x != null && x.matches("[0-9a-f]{64}");
  }

  private static void add(List<WorkspaceDiagnostic> d, String c, String p, String m) {
    d.add(new WorkspaceDiagnostic(c, p, m));
  }
}
