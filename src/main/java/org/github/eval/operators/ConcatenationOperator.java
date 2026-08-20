package org.github.eval.operators;

import org.github.eval.data.EvaluationValue;

/** Excel's {@code &} text concatenation: both operands are stringified first. */
public final class ConcatenationOperator {

  private ConcatenationOperator() {}

  public static EvaluationValue concat(EvaluationValue left, EvaluationValue right) {
    return EvaluationValue.of(left.getStringValue() + right.getStringValue());
  }
}
