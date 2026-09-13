package dev.teyru.compiler.core.syntax;

import java.util.*;

public final class AstIndex {
  private final Map<NodeId, AstNode> nodes;

  public AstIndex(Collection<AstNode> roots) {
    var map = new LinkedHashMap<NodeId, AstNode>();
    var todo = new ArrayDeque<Map.Entry<AstNode, Optional<NodeId>>>();
    for (var root : roots) todo.add(Map.entry(root, Optional.empty()));
    while (!todo.isEmpty()) {
      var item = todo.removeFirst();
      var node = item.getKey();
      if (!node.parentId().equals(item.getValue()))
        throw new IllegalArgumentException("inconsistent parent ID");
      if (map.put(node.id(), node) != null) throw new IllegalArgumentException("duplicate node ID");
      for (var child : node.children()) todo.add(Map.entry(child, Optional.of(node.id())));
    }
    nodes = Map.copyOf(map);
  }

  public Optional<AstNode> find(NodeId id) {
    return Optional.ofNullable(nodes.get(id));
  }

  public List<AstNode> ordered() {
    return List.copyOf(nodes.values());
  }
}
