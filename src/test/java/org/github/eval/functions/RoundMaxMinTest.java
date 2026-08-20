package org.github.eval.functions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import org.github.eval.EvaluationException;
import org.github.eval.Expression;
import org.github.eval.data.EvaluationValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RoundMaxMinTest {

  private static EvaluationValue evaluate(String expression) {
    return new Expression(expression).evaluate();
  }

  private static EvaluationValue num(String value) {
    return EvaluationValue.of(new BigDecimal(value));
  }

  @ParameterizedTest
  @CsvSource(delimiter = ';', value = {
    "ROUND(2.5, 0); 3",
    "ROUND(-2.5, 0); -3",
    "ROUND(1.005, 2); 1.01",
    "ROUND(2.4, 0); 2",
    "ROUND(123.456, -1); 120",
    "ROUND(1.2345, 3); 1.235"
  })
  void roundUsesHalfAwayFromZero(String expression, String expected) {
    assertEquals(num(expected), evaluate(expression));
  }

  @Test
  void roundRequiresExactlyTwoArguments() {
    assertThrows(EvaluationException.class, () -> evaluate("ROUND(1.5)"));
  }

  @ParameterizedTest
  @CsvSource(delimiter = ';', value = {
    "MAX(1, 2, 3); 3",
    "MAX(-1, -5); -1",
    "MAX(2.50, 2.5, 1); 2.50",
    "MIN(1, 2, 3); 1",
    "MIN(-1, -5); -5",
    "MIN(0.1, 0.10); 0.1"
  })
  void maxMin(String expression, String expected) {
    assertEquals(num(expected), evaluate(expression));
  }

  @Test
  void maxRequiresAtLeastOneArgument() {
    assertThrows(EvaluationException.class, () -> evaluate("MAX()"));
  }

  @Test
  void maxCoercesNumericStrings() {
    assertEquals(num("10"), evaluate("MAX(\"10\", 2)"));
  }

  @Test
  void nestedAndCombined() {
    assertEquals(num("2"), evaluate("MIN(MAX(1, 2), ROUND(2.5, 0))"));
  }
}
