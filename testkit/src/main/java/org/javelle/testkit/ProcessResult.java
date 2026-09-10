/* SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0 */
package org.javelle.testkit;

/** Bounded external-process result used by black-box test suites. */
public record ProcessResult(int exitCode, String stdout, String stderr, boolean timedOut) {}
