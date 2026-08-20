package org.github.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.Map;
import org.github.eval.data.EvaluationValue;
import org.junit.jupiter.api.Test;

class ExplainArithmeticTest {

  private static EvaluationValue num(String value) {
    return EvaluationValue.of(new BigDecimal(value));
  }

  @Test
  void explainReturnsValueAndSteps() {
    Explanation explanation = new Expression("1+2*3").explain();
    assertEquals(num("7"), explanation.getValue());
    assertEquals(
        java.util.List.of(
            "2 × 3 = 6",
            "1 + 6 = 7",
            "Result: 1+2*3 = 7"),
        explanation.getSteps().stream().map(s -> s.getDescription()).toList());
  }

  @Test
  void nestedOperationPrecedesOuterOperation() {
    // multiplication is nested inside the addition, so it is traced first
    Explanation explanation = new Expression("1+2*3").explain();
    assertEquals("2 × 3 = 6", explanation.getSteps().get(0).getDescription());
    assertEquals("1 + 6 = 7", explanation.getSteps().get(1).getDescription());
  }

  @Test
  void variableResolutionIsTraced() {
    Explanation explanation = new Expression("price * qty")
        .explain(Map.of("price", new BigDecimal("2.5"), "qty", 4));
    assertEquals(num("10"), explanation.getValue());
    assertEquals(
        java.util.List.of(
            "Resolve variable price = 2.5",
            "Resolve variable qty = 4",
            "2.5 × 4 = 10",
            "Result: price * qty = 10"),
        explanation.getSteps().stream().map(s -> s.getDescription()).toList());
  }

  @Test
  void withBindingsAreTraced() {
    Explanation explanation = new Expression("amount * 0.13").with("amount", 150000).explain();
    assertEquals(num("19500"), explanation.getValue());
    assertEquals(
        java.util.List.of(
            "Resolve variable amount = 150,000",
            "150,000 × 0.13 = 19,500",
            "Result: amount * 0.13 = 19,500"),
        explanation.getSteps().stream().map(s -> s.getDescription()).toList());
  }

  @Test
  void comparisonAndConcatenationAreTraced() {
    Explanation explanation = new Expression("1+1 = 2").explain();
    assertEquals(EvaluationValue.of(true), explanation.getValue());
    assertEquals(
        java.util.List.of(
            "1 + 1 = 2",
            "2 = 2 = true",
            "Result: 1+1 = 2 = true"),
        explanation.getSteps().stream().map(s -> s.getDescription()).toList());

    Explanation concat = new Expression("\"a\" & 1+1").explain();
    assertEquals(
        java.util.List.of(
            "1 + 1 = 2",
            "a & 2 = a2",
            "Result: \"a\" & 1+1 = a2"),
        concat.getSteps().stream().map(s -> s.getDescription()).toList());
  }

  @Test
  void unaryMinusIsTraced() {
    Explanation explanation = new Expression("-5 + 1").explain();
    assertEquals(
        java.util.List.of(
            "-5 = -5",
            "-5 + 1 = -4",
            "Result: -5 + 1 = -4"),
        explanation.getSteps().stream().map(s -> s.getDescription()).toList());
  }

  @Test
  void formatNumbersSteps() {
    Explanation explanation = new Expression("1+2").explain();
    assertEquals("1. 1 + 2 = 3\n2. Result: 1+2 = 3", explanation.format());
  }

  @Test
  void plainEvaluateIsUnchangedAndUntraced() {
    // evaluate() must behave exactly as before and must not require a tracer
    assertEquals(num("3"), new Expression("1+2").evaluate());
    assertEquals(num("6"), new Expression("x*2").with("x", 3).evaluate());
  }

  @Test
  void explainPropagatesEvaluationException() {
    assertThrows(EvaluationException.class, () -> new Expression("1/0").explain());
  }
}
