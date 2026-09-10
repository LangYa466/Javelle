package org.javelle.compiler.core.frontend;

import java.util.*;

public final class FrontendSnapshot {
  private FrontendSnapshot() {}

  public static String canonical(ParseResult result) {
    var out = new StringBuilder("AST\n");
    append(result.ast(), 0, out);
    out.append("DIAGNOSTICS\n");
    for (var d : result.diagnostics())
      out.append(d.code().value())
          .append('|')
          .append(d.range().startOffset())
          .append(':')
          .append(d.range().endOffset())
          .append('|')
          .append(escape(d.message()))
          .append('\n');
    return out.toString();
  }

  private static void append(FrontendNode node, int depth, StringBuilder out) {
    out.append(depth)
        .append('|')
        .append(node.kind())
        .append('|')
        .append(escape(node.name()))
        .append('|')
        .append(node.range().startOffset())
        .append(':')
        .append(node.range().endOffset())
        .append("|id=")
        .append(node.id().ordinal())
        .append("|parent=")
        .append(node.parentId().map(x -> Integer.toString(x.ordinal())).orElse("-"));
    if (node instanceof AccessorNode a) out.append("|visibility=").append(a.visibility());
    out.append('\n');
    for (var child : node.children()) append(child, depth + 1, out);
  }

  private static String escape(String value) {
    return value.replace("\\", "\\\\").replace("\n", "\\n").replace("|", "\\u007c");
  }
}
