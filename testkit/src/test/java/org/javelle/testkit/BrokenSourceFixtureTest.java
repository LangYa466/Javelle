/* SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0 */
package org.javelle.testkit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.List;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;

class BrokenSourceFixtureTest {
  @Test
  void independentlyBrokenJavaFixtureFailsJavac() {
    var source =
        new SimpleJavaFileObject(URI.create("string:///Broken.java"), JavaFileObject.Kind.SOURCE) {
          @Override
          public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return "final class Broken { void broken( }";
          }
        };
    var diagnostics = new javax.tools.DiagnosticCollector<JavaFileObject>();
    boolean success =
        ToolProvider.getSystemJavaCompiler()
            .getTask(null, null, diagnostics, List.of("--release", "21"), null, List.of(source))
            .call();
    assertFalse(success);
    assertTrue(
        diagnostics.getDiagnostics().stream()
            .anyMatch(d -> d.getKind() == javax.tools.Diagnostic.Kind.ERROR));
  }
}
