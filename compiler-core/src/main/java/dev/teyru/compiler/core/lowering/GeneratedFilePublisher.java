package dev.teyru.compiler.core.lowering;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/** Validates a complete generation batch before atomically publishing it. */
public final class GeneratedFilePublisher {
  private final AtomicReference<Map<String, GeneratedFile>> current =
      new AtomicReference<>(Map.of());

  public Map<String, GeneratedFile> publish(Collection<GeneratedFile> candidate) {
    Objects.requireNonNull(candidate);
    var checked = new TreeMap<String, GeneratedFile>();
    for (var file : candidate) {
      Objects.requireNonNull(file);
      if (checked.putIfAbsent(file.relativeUri(), file) != null)
        throw new IllegalArgumentException("generated path collision: " + file.relativeUri());
    }
    var immutable = Collections.unmodifiableMap(new LinkedHashMap<>(checked));
    current.set(immutable);
    return immutable;
  }

  public Map<String, GeneratedFile> current() {
    return current.get();
  }
}
