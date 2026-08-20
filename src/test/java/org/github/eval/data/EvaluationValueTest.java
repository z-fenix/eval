package org.github.eval.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.github.eval.EvaluationException;
import org.junit.jupiter.api.Test;

class EvaluationValueTest {

  @Test
  void numberEqualityIsScaleInsensitive() {
    assertEquals(EvaluationValue.of(new BigDecimal("3.00")), EvaluationValue.of(new BigDecimal("3")));
  }

  @Test
  void numericStringCoercesToNumber() {
    assertEquals(new BigDecimal("12.5"), EvaluationValue.of(" 12.5 ").getNumberValue());
  }

  @Test
  void nonNumericStringThrowsOnNumberAccess() {
    assertThrows(EvaluationException.class, () -> EvaluationValue.of("abc").getNumberValue());
  }

  @Test
  void booleanCoercesToOneOrZero() {
    assertEquals(BigDecimal.ONE, EvaluationValue.of(true).getNumberValue());
    assertEquals(BigDecimal.ZERO, EvaluationValue.of(false).getNumberValue());
  }

  @Test
  void numberToStringStripsTrailingZeros() {
    assertEquals("1.50", EvaluationValue.of("1.50").getStringValue());
    assertEquals("1.5", EvaluationValue.of(new BigDecimal("1.50")).getStringValue());
    assertEquals("0", EvaluationValue.of(new BigDecimal("0.00")).getStringValue());
  }

  @Test
  void booleanToStringIsExcelStyle() {
    assertEquals("TRUE", EvaluationValue.of(true).getStringValue());
    assertEquals("FALSE", EvaluationValue.of(false).getStringValue());
  }

  @Test
  void booleanCoercion() {
    assertTrue(EvaluationValue.of(new BigDecimal("2")).getBooleanValue());
    assertTrue(EvaluationValue.of("true").getBooleanValue());
    assertThrows(EvaluationException.class, () -> EvaluationValue.of("yes").getBooleanValue());
  }

  @Test
  void fromObjectMapsJavaTypes() {
    assertEquals(EvaluationValue.of(new BigDecimal("0.1")), EvaluationValue.fromObject(0.1d));
    assertEquals(EvaluationValue.of("x"), EvaluationValue.fromObject("x"));
    assertEquals(EvaluationValue.of(true), EvaluationValue.fromObject(Boolean.TRUE));
    assertThrows(EvaluationException.class, () -> EvaluationValue.fromObject(null));
  }
}
