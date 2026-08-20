package org.github.eval.data;

import java.math.BigDecimal;
import java.util.Objects;
import org.github.eval.EvaluationException;

/** Immutable tagged union of the runtime value types (NUMBER, STRING, BOOLEAN). */
public final class EvaluationValue {

  public enum DataType {
    NUMBER,
    STRING,
    BOOLEAN
  }

  private final DataType dataType;
  private final Object value;

  private EvaluationValue(DataType dataType, Object value) {
    this.dataType = dataType;
    this.value = value;
  }

  public static EvaluationValue of(BigDecimal value) {
    return new EvaluationValue(DataType.NUMBER, value);
  }

  public static EvaluationValue of(String value) {
    return new EvaluationValue(DataType.STRING, value);
  }

  public static EvaluationValue of(boolean value) {
    return new EvaluationValue(DataType.BOOLEAN, value);
  }

  /** Converts a Java value supplied as a variable binding. */
  public static EvaluationValue fromObject(Object value) {
    if (value instanceof EvaluationValue evaluationValue) {
      return evaluationValue;
    }
    if (value instanceof BigDecimal bigDecimal) {
      return of(bigDecimal);
    }
    if (value instanceof Number number) {
      // via toString to avoid importing binary floating-point error from double/float
      return of(new BigDecimal(number.toString()));
    }
    if (value instanceof Boolean bool) {
      return of(bool);
    }
    if (value instanceof String string) {
      return of(string);
    }
    throw new EvaluationException(
        "Unsupported value type: " + (value == null ? "null" : value.getClass().getName()));
  }

  public DataType getDataType() {
    return dataType;
  }

  public BigDecimal getNumberValue() {
    return switch (dataType) {
      case NUMBER -> (BigDecimal) value;
      case BOOLEAN -> (Boolean) value ? BigDecimal.ONE : BigDecimal.ZERO;
      case STRING -> {
        try {
          yield new BigDecimal(((String) value).trim());
        } catch (NumberFormatException e) {
          throw new EvaluationException("Cannot convert string '" + value + "' to a number");
        }
      }
    };
  }

  public String getStringValue() {
    return switch (dataType) {
      case STRING -> (String) value;
      case BOOLEAN -> (Boolean) value ? "TRUE" : "FALSE";
      case NUMBER -> {
        BigDecimal number = ((BigDecimal) value).stripTrailingZeros();
        yield number.compareTo(BigDecimal.ZERO) == 0 ? "0" : number.toPlainString();
      }
    };
  }

  public boolean getBooleanValue() {
    return switch (dataType) {
      case BOOLEAN -> (Boolean) value;
      case NUMBER -> ((BigDecimal) value).compareTo(BigDecimal.ZERO) != 0;
      case STRING -> {
        String string = ((String) value).trim();
        if (string.equalsIgnoreCase("true")) {
          yield true;
        }
        if (string.equalsIgnoreCase("false")) {
          yield false;
        }
        throw new EvaluationException("Cannot convert string '" + value + "' to a boolean");
      }
    };
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof EvaluationValue other) || dataType != other.dataType) {
      return false;
    }
    if (value instanceof BigDecimal a && other.value instanceof BigDecimal b) {
      return a.compareTo(b) == 0;
    }
    return value.equals(other.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(dataType, value instanceof BigDecimal n ? n.stripTrailingZeros() : value);
  }

  @Override
  public String toString() {
    return dataType + "(" + value + ")";
  }
}
