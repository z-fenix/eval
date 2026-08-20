package org.github.eval.functions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.github.eval.EvaluationException;
import org.github.eval.Expression;
import org.github.eval.data.EvaluationValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AndOrTest {

  private static EvaluationValue evaluate(String expression) {
    return new Expression(expression).evaluate();
  }

  @ParameterizedTest
  @CsvSource(delimiter = ';', value = {
    "AND(TRUE, TRUE); true",
    "AND(TRUE, FALSE); false",
    "AND(1, 2, 3); true",
    "AND(1, 0); false",
    "AND(\"true\", 1); true",
    "AND(2>1, 3>2); true"
  })
  void and(String expression, boolean expected) {
    assertEquals(EvaluationValue.of(expected), evaluate(expression));
  }

  @ParameterizedTest
  @CsvSource(delimiter = ';', value = {
    "OR(FALSE, FALSE); false",
    "OR(FALSE, TRUE); true",
    "OR(0, 0, 5); true",
    "OR(1=2, 2=2); true"
  })
  void or(String expression, boolean expected) {
    assertEquals(EvaluationValue.of(expected), evaluate(expression));
  }

  @Test
  void functionNamesAreCaseInsensitive() {
    assertEquals(EvaluationValue.of(true), evaluate("aNd(tRuE, True)"));
  }

  @Test
  void andRejectsNonBooleanOperand() {
    assertThrows(EvaluationException.class, () -> evaluate("AND(\"yes\")"));
  }

  @Test
  void andRequiresAtLeastOneArgument() {
    assertThrows(EvaluationException.class, () -> evaluate("AND()"));
  }

  @Test
  void unknownFunctionThrows() {
    EvaluationException exception =
        assertThrows(EvaluationException.class, () -> evaluate("NOSUCHFN(1)"));
    assertEquals("Unknown function: NOSUCHFN", exception.getMessage());
  }
}
