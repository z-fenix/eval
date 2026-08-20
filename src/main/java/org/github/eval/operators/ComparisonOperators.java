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
    if (isNumeric(left) && isNumeric(right)) {
      return left.getNumberValue().compareTo(right.getNumberValue());
    }
    if (left.getDataType() == DataType.BOOLEAN && right.getDataType() == DataType.BOOLEAN) {
      return Boolean.compare(left.getBooleanValue(), right.getBooleanValue());
    }
    return left.getStringValue().compareToIgnoreCase(right.getStringValue());
  }

  private static boolean isNumeric(EvaluationValue value) {
    if (value.getDataType() == DataType.NUMBER) {
      return true;
    }
    if (value.getDataType() == DataType.STRING) {
      try {
        new BigDecimal(value.getStringValue().trim());
        return true;
      } catch (NumberFormatException e) {
        return false;
      }
    }
    return false;
  }
}
