package org.github.eval.functions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import org.github.eval.EvaluationException;
import org.github.eval.Expression;
import org.github.eval.data.EvaluationValue;
import org.junit.jupiter.api.Test;

class IfTest {

  private static EvaluationValue evaluate(String expression) {
    return new Expression(expression).evaluate();
  }

  private static EvaluationValue num(String value) {
    return EvaluationValue.of(new BigDecimal(value));
  }

  @Test
  void selectsThenBranchWhenTrue() {
    assertEquals(num("1"), evaluate("IF(TRUE, 1, 2)"));
  }

  @Test
  void selectsElseBranchWhenFalse() {
    assertEquals(num("2"), evaluate("IF(FALSE, 1, 2)"));
  }

  @Test
  void evaluatesConditionExpression() {
    assertEquals(num("10"), evaluate("IF(2 > 1, 10, 20)"));
  }

  @Test
  void untakenBranchIsNotEvaluated() {
    // Would throw division-by-zero if evaluated eagerly.
    assertEquals(num("1"), evaluate("IF(TRUE, 1, 1/0)"));
    assertEquals(num("2"), evaluate("IF(FALSE, 1/0, 2)"));
  }

  @Test
  void missingElseYieldsFalse() {
    assertEquals(EvaluationValue.of(false), evaluate("IF(FALSE, 1)"));
  }

  @Test
  void twoArgumentTrueCaseReturnsBranch() {
    assertEquals(num("7"), evaluate("IF(TRUE, 7)"));
  }

  @Test
  void nestedIf() {
    assertEquals(
        EvaluationValue.of("big"), evaluate("IF(10 > 100, \"huge\", IF(10 > 5, \"big\", \"small\"))"));
  }

  @Test
  void wrongArgumentCountThrows() {
    assertThrows(EvaluationException.class, () -> evaluate("IF(TRUE)"));
    assertThrows(EvaluationException.class, () -> evaluate("IF(TRUE, 1, 2, 3)"));
  }

  @Test
  void ifWithVariables() {
    EvaluationValue result =
        new Expression("IF(score >= 60, \"pass\", \"fail\")").with("score", 75).evaluate();
    assertEquals(EvaluationValue.of("pass"), result);
  }
}
