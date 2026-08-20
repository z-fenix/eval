package org.github.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import org.github.eval.data.EvaluationValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ExpressionTest {

  private static EvaluationValue evaluate(String expression) {
    return new Expression(expression).evaluate();
  }

  private static EvaluationValue num(String value) {
    return EvaluationValue.of(new BigDecimal(value));
  }

  @ParameterizedTest
  @CsvSource({
    "1+2, 3",
    "2*3+4, 10",
    "2*(3+4), 14",
    "10-2-3, 5",
    "2*3/4, 1.5",
    "-2+5, 3",
    "+2*3, 6",
    "--2, 2",
    "0.1+0.2, 0.3",
    "(1+2)*3, 9"
  })
  void arithmeticRespectsPrecedenceAndPrecision(String expression, String expected) {
    assertEquals(num(expected), evaluate(expression));
  }

  @Test
  void divisionUsesDecimal128() {
    assertEquals(
        num("0.3333333333333333333333333333333333"), evaluate("1/3"));
  }

  @Test
  void divisionByZeroThrowsEvaluationException() {
    assertThrows(EvaluationException.class, () -> evaluate("1/0"));
  }

  @ParameterizedTest
  @CsvSource({
    "1=1, true",
    "1=2, false",
    "2>1, true",
    "2>=2, true",
    "1<2, true",
    "2<=1, false",
    "1<>2, true",
    "\"abc\"=\"ABC\", true",
    "\"b\">\"a\", true"
  })
  void comparisons(String expression, boolean expected) {
    assertEquals(EvaluationValue.of(expected), evaluate(expression));
  }

  @Test
  void concatenation() {
    assertEquals(EvaluationValue.of("Result: 3"), evaluate("\"Result: \" & 1+2"));
    assertEquals(EvaluationValue.of("12"), evaluate("1 & 2"));
  }

  @Test
  void stringEscapingAndBooleans() {
    assertEquals(EvaluationValue.of("say \"hi\""), evaluate("\"say \"\"hi\"\"\""));
    assertEquals(EvaluationValue.of(true), evaluate("TRUE"));
    assertEquals(EvaluationValue.of(false), evaluate("false"));
  }

  @Test
  void comparisonBindsLooserThanConcatenation() {
    // "a" & "b" = "ab"  →  TRUE
    assertEquals(EvaluationValue.of(true), evaluate("\"a\" & \"b\" = \"ab\""));
  }

  @Test
  void syntaxErrorThrowsParseException() {
    assertThrows(ParseException.class, () -> new Expression("1 +"));
  }
}
