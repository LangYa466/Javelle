/* SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0 */
package dev.teyru.compiler.driver;

import dev.teyru.compiler.core.budget.CancellationToken;

public interface CompilerDriver {
  CompileResult compile(CompileRequest request, CancellationToken cancellation);
}
