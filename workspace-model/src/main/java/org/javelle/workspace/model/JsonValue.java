/* SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0 */
package org.javelle.workspace.model;

import java.math.BigDecimal;
import java.util.*;

public sealed interface JsonValue
    permits JsonValue.ObjectValue,
        JsonValue.ArrayValue,
        JsonValue.StringValue,
        JsonValue.NumberValue,
        JsonValue.BooleanValue,
        JsonValue.NullValue {
  record ObjectValue(Map<String, JsonValue> values) implements JsonValue {
    public ObjectValue {
      values = Collections.unmodifiableMap(new TreeMap<>(values));
    }
  }

  record ArrayValue(List<JsonValue> values) implements JsonValue {
    public ArrayValue {
      values = List.copyOf(values);
    }
  }

  record StringValue(String value) implements JsonValue {
    public StringValue {
      Objects.requireNonNull(value);
    }
  }

  record NumberValue(BigDecimal value) implements JsonValue {
    public NumberValue {
      Objects.requireNonNull(value);
    }
  }

  record BooleanValue(boolean value) implements JsonValue {}

  enum NullValue implements JsonValue {
    INSTANCE
  }
}
