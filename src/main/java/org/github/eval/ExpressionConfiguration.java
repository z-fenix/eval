package org.github.eval;

import java.math.MathContext;

/** Evaluation-wide settings. Currently just the math context used for division and arithmetic. */
public class ExpressionConfiguration {

  public static final MathContext DEFAULT_MATH_CONTEXT = MathContext.DECIMAL128;

  private final MathContext mathContext;

  private ExpressionConfiguration(MathContext mathContext) {
    this.mathContext = mathContext;
  }

  public static ExpressionConfiguration defaultConfiguration() {
    return new ExpressionConfiguration(DEFAULT_MATH_CONTEXT);
  }

  public static ExpressionConfiguration of(MathContext mathContext) {
    return new ExpressionConfiguration(mathContext);
  }

  public MathContext getMathContext() {
    return mathContext;
  }
}
