package org.github.eval.operators;

import java.math.BigDecimal;
import java.math.MathContext;
import org.github.eval.EvaluationException;
import org.github.eval.data.EvaluationValue;

/** BigDecimal arithmetic with Excel-style operand coercion. */
public final class ArithmeticOperators {

  private ArithmeticOperators() {}

  public static EvaluationValue add(EvaluationValue left, EvaluationValue right, MathContext mc) {
    return EvaluationValue.of(left.getNumberValue().add(right.getNumberValue(), mc));
  }

  public static EvaluationValue subtract(
      EvaluationValue left, EvaluationValue right, MathContext mc) {
    return EvaluationValue.of(left.getNumberValue().subtract(right.getNumberValue(), mc));
  }

  public static EvaluationValue multiply(
      EvaluationValue left, EvaluationValue right, MathContext mc) {
    return EvaluationValue.of(left.getNumberValue().multiply(right.getNumberValue(), mc));
  }

  public static EvaluationValue divide(EvaluationValue left, EvaluationValue right, MathContext mc) {
    if (right.getNumberValue().compareTo(BigDecimal.ZERO) == 0) {
      throw new EvaluationException("Division by zero");
    }
    try {
      return EvaluationValue.of(left.getNumberValue().divide(right.getNumberValue(), mc));
    } catch (ArithmeticException e) {
      throw new EvaluationException("Division failed: " + e.getMessage());
    }
  }

  public static EvaluationValue negate(EvaluationValue value) {
    return EvaluationValue.of(value.getNumberValue().negate());
  }

  /** Unary plus still coerces its operand to a number. */
  public static EvaluationValue unaryPlus(EvaluationValue value) {
    return EvaluationValue.of(value.getNumberValue());
  }
}
