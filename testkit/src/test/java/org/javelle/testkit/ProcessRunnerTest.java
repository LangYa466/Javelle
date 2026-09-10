/* SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0 */
package org.javelle.testkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProcessRunnerTest {
  @Test
  void capturesIndependentStreamsAndExitCode() throws Exception {
    ProcessResult result = ProcessRunner.run(javaCommand("emit"), Duration.ofSeconds(5), 4096);
    assertEquals(7, result.exitCode());
    assertEquals("stdout", result.stdout());
    assertEquals("stderr", result.stderr());
    assertFalse(result.timedOut());
  }

  @Test
  void forciblyStopsTimedOutProcess() throws Exception {
    ProcessResult result = ProcessRunner.run(javaCommand("hang"), Duration.ofMillis(150), 4096);
    assertTrue(result.timedOut());
    assertEquals(-1, result.exitCode());
  }

  @Test
  void rejectsUnboundedInputs() {
    assertThrows(
        IllegalArgumentException.class, () -> ProcessRunner.run(List.of(), Duration.ZERO, 0));
  }

  @Test
  void rejectsOutputPastCap() {
    assertThrows(
        IOException.class,
        () -> ProcessRunner.run(javaCommand("large"), Duration.ofSeconds(5), 32));
  }

  @Test
  void decodesUtf8Exactly() throws Exception {
    assertEquals("臺灣☕", ProcessRunner.run(javaCommand("utf8"), Duration.ofSeconds(5), 64).stdout());
  }

  private static List<String> javaCommand(String mode) {
    String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
    return List.of(
        java, "-cp", System.getProperty("java.class.path"), FixtureProcess.class.getName(), mode);
  }
}
