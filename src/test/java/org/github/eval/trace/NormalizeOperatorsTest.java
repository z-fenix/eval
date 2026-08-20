package org.github.eval.trace;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class NormalizeOperatorsTest {

  @Test
  void conditionOutsideStringStillNormalizesOperators() {
    assertEquals(
        "Evaluate condition amount>100000 → true",
        TraceFormatter.condition("amount>100000", true));
  }

  @Test
  void slashInsideStringLiteralIsNotConverted() {
    assertEquals(
        "Take branch \"a/b\"",
        TraceFormatter.branch("\"a/b\""));
  }

  @Test
  void asteriskInsideStringLiteralIsUntouchedButOutsideIsNormalized() {
    assertEquals(
        "Take branch \"a*b\"×2",
        TraceFormatter.branch("\"a*b\"*2"));
  }

  @Test
  void escapedQuoteInsideStringLiteralKeepsItsAsterisk() {
    assertEquals(
        "Evaluate condition \"x\"\"*\"=y → true",
        TraceFormatter.condition("\"x\"\"*\"=y", true));
  }
}
