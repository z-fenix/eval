package org.github.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.math.MathContext;
import org.github.eval.data.EvaluationValue;
import org.junit.jupiter.api.Test;

/** Spec acceptance: combined formulas, precision, configurability. */
class IntegrationTest {

  private static EvaluationValue num(String value) {
    return EvaluationValue.of(new BigDecimal(value));
  }

  @Test
  void specExample() {
    EvaluationValue result =
        new Expression("IF(A1 > 0, ROUND(B1, 2), 0)")
            .with("A1", 5)
            .with("B1", new BigDecimal("1.005"))
            .evaluate();
    assertEquals(num("1.01"), result);
  }

  @Test
  void combinedFormula() {
    // IF(AND(x>0, x<10), MAX(x*2, 5), MIN(x, 0)) with x = 4 → MAX(8, 5) = 8
    EvaluationValue result =
        new Expression("IF(AND(x > 0, x < 10), MAX(x * 2, 5), MIN(x, 0))")
            .with("x", 4)
            .evaluate();
    assertEquals(num("8"), result);
  }

  @Test
  void mixedTypesInOneFormula() {
    EvaluationValue result =
        new Expression("IF(ROUND(amount, 2) = 10.5, \"ok\" & \"!\", \"bad\")")
            .with("amount", new BigDecimal("10.4999"))
            .evaluate();
    assertEquals(EvaluationValue.of("ok!"), result);
  }

  @Test
  void longPrecisionChain() {
    // DECIMAL128: 1/3 = 0.33…3 (34 threes); ×3 = 0.99…9 (34 nines), no error blow-up.
    // Plain double arithmetic gives the coincidental-looking 1.0 here and hides the real value.
    EvaluationValue result = new Expression("(1/3)*3").evaluate();
    assertEquals(num("0.9999999999999999999999999999999999"), result);
  }

  @Test
  void decimalAdditionIsExact() {
    // double gives 0.30000000000000004; BigDecimal gives exactly 0.3.
    assertEquals(num("0.3"), new Expression("0.1 + 0.2").evaluate());
  }

  @Test
  void configurableMathContext() {
    EvaluationValue result =
        new Expression("1/3", ExpressionConfiguration.of(MathContext.DECIMAL32))
            .evaluate();
    assertEquals(num("0.3333333"), result);
  }

  @Test
  void concatenationWithEverything() {
    EvaluationValue result =
        new Expression("\"Total: \" & ROUND(MAX(a, b), 1) & \" (\" & IF(a > b, \"a\", \"b\") & \")\"")
            .with("a", new BigDecimal("2.55"))
            .with("b", new BigDecimal("2.5"))
            .evaluate();
    assertEquals(EvaluationValue.of("Total: 2.6 (a)"), result);
  }
}
