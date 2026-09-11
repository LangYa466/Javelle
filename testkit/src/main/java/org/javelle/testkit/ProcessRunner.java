/* SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0 */
package org.javelle.testkit;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Executes a subprocess with timeout and output-size limits. */
public final class ProcessRunner {
  private ProcessRunner() {}

  public static ProcessResult run(List<String> command, Duration timeout, int maxBytes)
      throws IOException, InterruptedException {
    if (command.isEmpty() || timeout.isNegative() || timeout.isZero() || maxBytes < 1) {
      throw new IllegalArgumentException("command, timeout, and maxBytes must be bounded");
    }
    Process process = new ProcessBuilder(command).start();
    try (ExecutorService readers = Executors.newFixedThreadPool(2)) {
      Future<String> stdout = readers.submit(() -> readBounded(process.getInputStream(), maxBytes));
      Future<String> stderr = readers.submit(() -> readBounded(process.getErrorStream(), maxBytes));
      boolean exited = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
      if (!exited) {
        process.destroyForcibly();
        if (!process.waitFor(5, TimeUnit.SECONDS)) {
          throw new IOException("process could not be killed after timeout");
        }
      }
      return new ProcessResult(
          exited ? process.exitValue() : -1, get(stdout), get(stderr), !exited);
    }
  }

  private static String readBounded(InputStream stream, int maxBytes) throws IOException {
    byte[] output = stream.readNBytes(maxBytes + 1);
    if (output.length > maxBytes)
      throw new IOException("process output exceeded " + maxBytes + " bytes");
    return StandardCharsets.UTF_8
        .newDecoder()
        .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
        .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
        .decode(java.nio.ByteBuffer.wrap(output))
        .toString();
  }

  private static String get(Future<String> output) throws IOException, InterruptedException {
    try {
      return output.get();
    } catch (ExecutionException failure) {
      if (failure.getCause() instanceof IOException io) throw io;
      throw new IOException("process stream reader failed", failure.getCause());
    }
  }
}
