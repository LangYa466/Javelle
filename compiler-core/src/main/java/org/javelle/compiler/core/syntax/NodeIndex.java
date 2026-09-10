package org.javelle.compiler.core.syntax;

import java.util.*;

public final class NodeIndex {
  private final Map<NodeId, CstNode> nodes;

  public NodeIndex(Collection<CstNode> roots) {
    var map = new LinkedHashMap<NodeId, CstNode>();
    var todo = new ArrayDeque<Map.Entry<CstNode, Optional<NodeId>>>();
    for (var root : roots) todo.add(Map.entry(root, Optional.empty()));
    while (!todo.isEmpty()) {
      var item = todo.removeFirst();
      var n = item.getKey();
      if (!n.parentId().equals(item.getValue()))
        throw new IllegalArgumentException("inconsistent parent ID");
      if (map.put(n.id(), n) != null) throw new IllegalArgumentException("duplicate node ID");
      for (var child : n.children()) todo.add(Map.entry(child, Optional.of(n.id())));
    }
    nodes = Collections.unmodifiableMap(new LinkedHashMap<>(map));
  }

  public Optional<CstNode> find(NodeId id) {
    return Optional.ofNullable(nodes.get(id));
  }

  public List<CstNode> ordered() {
    return List.copyOf(nodes.values());
  }
}
