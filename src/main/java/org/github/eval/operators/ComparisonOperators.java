package org.github.eval.operators;

import java.math.BigDecimal;
import org.github.eval.EvaluationException;
import org.github.eval.data.EvaluationValue;
import org.github.eval.data.EvaluationValue.DataType;

/**
 * Excel-style comparison: numeric when both sides are numbers or numeric strings,
 * boolean when both sides are booleans, otherwise case-insensitive text.
 */
public final class ComparisonOperators {

  private ComparisonOperators() {}

  public static EvaluationValue apply(String operator, EvaluationValue left, EvaluationValue right) {
    int comparison = compare(left, right);
    boolean result =
        switch (operator) {
          case "=" -> comparison == 0;
          case "<>" -> comparison != 0;
          case "<" -> comparison < 0;
          case ">" -> comparison > 0;
          case "<=" -> comparison <= 0;
          case ">=" -> comparison >= 0;
          default -> throw new EvaluationException("Unknown comparison operator: " + operator);
        };
    return EvaluationValue.of(result);
  }

  private static int compare(EvaluationValue left, EvaluationValue right) {
    BigDecimal leftNumber = toNumber(left);
    BigDecimal rightNumber = toNumber(right);
    if (leftNumber != null && rightNumber != null) {
      return leftNumber.compareTo(rightNumber);
    }
    if (left.getDataType() == DataType.BOOLEAN && right.getDataType() == DataType.BOOLEAN) {
      return Boolean.compare(left.getBooleanValue(), right.getBooleanValue());
    }
    return left.getStringValue().compareToIgnoreCase(right.getStringValue());
  }

  private static BigDecimal toNumber(EvaluationValue value) {
    if (value.getDataType() == DataType.NUMBER) {
      return value.getNumberValue();
    }
    if (value.getDataType() == DataType.STRING) {
      try {
        return new BigDecimal(value.getStringValue().trim());
      } catch (NumberFormatException e) {
        return null;
      }
    }
    return null;
  }
}
