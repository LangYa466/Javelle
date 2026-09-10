package org.javelle.compiler.core.lowering;

public record JavaFragment(String text) {
  public JavaFragment {
    if (text == null) throw new NullPointerException();
  }
}
