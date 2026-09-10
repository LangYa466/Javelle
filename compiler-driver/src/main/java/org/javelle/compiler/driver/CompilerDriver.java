/* SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0 */
package org.javelle.compiler.driver;

import org.javelle.compiler.core.budget.CancellationToken;

public interface CompilerDriver {
  CompileResult compile(CompileRequest request, CancellationToken cancellation);
}
