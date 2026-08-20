package org.github.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.Map;
import org.github.eval.data.EvaluationValue;
import org.junit.jupiter.api.Test;

class VariablesTest {

  @Test
  void withBindsVariables() {
    EvaluationValue result =
        new Expression("A1 * 2 + B1").with("A1", 3).with("B1", new BigDecimal("0.5")).evaluate();
    assertEquals(EvaluationValue.of(new BigDecimal("6.5")), result);
  }

  @Test
  void evaluateMapBindsVariables() {
    EvaluationValue result = new Expression("x + y").evaluate(Map.of("x", 1, "y", "2.5"));
    assertEquals(EvaluationValue.of(new BigDecimal("3.5")), result);
  }

  @Test
  void variableNamesAreCaseInsensitive() {
    EvaluationValue result = new Expression("total * 2").with("TOTAL", 21).evaluate();
    assertEquals(EvaluationValue.of(new BigDecimal("42")), result);
  }

  @Test
  void evaluateMapOverridesWithBindings() {
    EvaluationValue result =
        new Expression("x").with("x", 1).evaluate(Map.of("x", 99));
    assertEquals(EvaluationValue.of(new BigDecimal("99")), result);
  }

  @Test
  void unknownVariableThrows() {
    EvaluationException exception =
        assertThrows(EvaluationException.class, () -> new Expression("nope + 1").evaluate());
    assertEquals("Unknown variable: nope", exception.getMessage());
  }

  @Test
  void parseOnceEvaluateManyTimesWithDifferentValues() {
    Expression expression = new Expression("price * qty");
    assertEquals(
        EvaluationValue.of(new BigDecimal("10")),
        expression.evaluate(Map.of("price", "2.5", "qty", 4)));
    assertEquals(
        EvaluationValue.of(new BigDecimal("6")),
        expression.evaluate(Map.of("price", 3, "qty", 2)));
  }
}
