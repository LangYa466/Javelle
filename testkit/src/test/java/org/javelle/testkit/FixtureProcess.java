/* SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0 */
package org.javelle.testkit;

public final class FixtureProcess {
  private FixtureProcess() {}

  public static void main(String[] args) throws Exception {
    if (args.length == 1 && args[0].equals("emit")) {
      System.out.print("stdout");
      System.err.print("stderr");
      System.exit(7);
    }
    if (args.length == 1 && args[0].equals("large")) {
      System.out.print("x".repeat(8192));
      return;
    }
    if (args.length == 1 && args[0].equals("utf8")) {
      System.out.print("臺灣☕");
      return;
    }
    Thread.sleep(30_000L);
  }
}
