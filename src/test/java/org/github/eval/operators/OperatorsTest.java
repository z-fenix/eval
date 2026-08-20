package org.github.eval.operators;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.math.MathContext;
import org.github.eval.EvaluationException;
import org.github.eval.data.EvaluationValue;
import org.junit.jupiter.api.Test;

class OperatorsTest {

  private static final MathContext MC = MathContext.DECIMAL128;

  private static EvaluationValue num(String value) {
    return EvaluationValue.of(new BigDecimal(value));
  }

  @Test
  void decimalAdditionIsExact() {
    assertEquals(num("0.3"), ArithmeticOperators.add(num("0.1"), num("0.2"), MC));
  }

  @Test
  void divisionCarriesDecimal128Precision() {
    EvaluationValue result = ArithmeticOperators.divide(num("1"), num("3"), MC);
    assertEquals(new BigDecimal("0.3333333333333333333333333333333333"), result.getNumberValue());
  }

  @Test
  void divisionByZeroThrows() {
    assertThrows(EvaluationException.class, () -> ArithmeticOperators.divide(num("1"), num("0"), MC));
  }

  @Test
  void nonTerminatingDivisionThrowsEvaluationExceptionNotRawArithmeticException() {
    assertThrows(
        EvaluationException.class,
        () -> ArithmeticOperators.divide(num("1"), num("3"), MathContext.UNLIMITED));
  }

  @Test
  void arithmeticCoercesNumericStringsAndBooleans() {
    assertEquals(num("3"), ArithmeticOperators.add(EvaluationValue.of("1"), num("2"), MC));
    assertEquals(num("2"), ArithmeticOperators.add(EvaluationValue.of(true), num("1"), MC));
  }

  @Test
  void negateAndUnaryPlus() {
    assertEquals(num("-2"), ArithmeticOperators.negate(num("2")));
    assertEquals(num("2"), ArithmeticOperators.unaryPlus(EvaluationValue.of("2")));
  }

  @Test
  void numericComparisonIsScaleInsensitive() {
    assertEquals(EvaluationValue.of(true), ComparisonOperators.apply("=", num("2.0"), num("2")));
    assertEquals(EvaluationValue.of(true), ComparisonOperators.apply("<>", num("2"), num("3")));
    assertEquals(EvaluationValue.of(true), ComparisonOperators.apply(">=", num("3"), num("3")));
    assertEquals(EvaluationValue.of(false), ComparisonOperators.apply("<", num("3"), num("2")));
  }

  @Test
  void stringComparisonIsCaseInsensitive() {
    assertEquals(
        EvaluationValue.of(true),
        ComparisonOperators.apply("=", EvaluationValue.of("abc"), EvaluationValue.of("ABC")));
    assertEquals(
        EvaluationValue.of(false),
        ComparisonOperators.apply("<>", EvaluationValue.of("abc"), EvaluationValue.of("ABC")));
  }

  @Test
  void mixedNumberAndNumericStringCompareNumerically() {
    assertEquals(
        EvaluationValue.of(true), ComparisonOperators.apply("=", num("10"), EvaluationValue.of("10")));
  }

  @Test
  void booleanComparison() {
    assertEquals(
        EvaluationValue.of(true),
        ComparisonOperators.apply(">", EvaluationValue.of(true), EvaluationValue.of(false)));
  }

  @Test
  void concatenationStringifiesOperands() {
    assertEquals(EvaluationValue.of("12"), ConcatenationOperator.concat(num("1"), num("2")));
    assertEquals(
        EvaluationValue.of("aTRUE"),
        ConcatenationOperator.concat(EvaluationValue.of("a"), EvaluationValue.of(true)));
  }
}
