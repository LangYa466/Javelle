/* SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0 */
package org.javelle.testkit;

import java.lang.reflect.Modifier;

/** Reflection assertions shared by black-box compatibility suites. */
public final class ClassInspector {
  private ClassInspector() {}

  public static void requirePublicMethod(
      Class<?> type, String name, Class<?> returnType, Class<?>... parameters)
      throws NoSuchMethodException {
    var method = type.getDeclaredMethod(name, parameters);
    if (!Modifier.isPublic(method.getModifiers()) || !method.getReturnType().equals(returnType)) {
      throw new AssertionError("method contract mismatch: " + method);
    }
  }
}
