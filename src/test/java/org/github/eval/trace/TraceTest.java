package org.github.eval.trace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.github.eval.data.EvaluationValue;
import org.junit.jupiter.api.Test;

class TraceTest {

  private static EvaluationValue num(String value) {
    return EvaluationValue.of(new BigDecimal(value));
  }

  @Test
  void stepHoldsTypeDescriptionAndNullableValue() {
    Step withValue = new Step(StepType.OPERATION, "1 + 2 = 3", num("3"));
    assertEquals(StepType.OPERATION, withValue.type());
    assertEquals("1 + 2 = 3", withValue.description());
    assertEquals(num("3"), withValue.value());

    Step branch = new Step(StepType.BRANCH, "Take branch x", null);
    assertNull(branch.value());
  }

  @Test
  void noOpTracerIsInactive() {
    assertFalse(NoOpTracer.INSTANCE.isActive());
    NoOpTracer.INSTANCE.record(new Step(StepType.RESULT, "ignored", num("1"))); // must not throw
  }

  @Test
  void collectingTracerKeepsInsertionOrder() {
    CollectingTracer tracer = new CollectingTracer();
    assertTrue(tracer.isActive());
    tracer.record(new Step(StepType.VARIABLE, "a", num("1")));
    tracer.record(new Step(StepType.OPERATION, "b", num("2")));
    assertEquals(2, tracer.getSteps().size());
    assertEquals("a", tracer.getSteps().get(0).description());
    assertEquals("b", tracer.getSteps().get(1).description());
  }

  @Test
  void formatsVariableWithGrouping() {
    assertEquals("Resolve variable amount = 150,000",
        TraceFormatter.variable("amount", num("150000")));
    assertEquals("Resolve variable x = 1,234,567.89",
        TraceFormatter.variable("x", num("1234567.89")));
    assertEquals("Resolve variable z = 0",
        TraceFormatter.variable("z", num("0.00")));
  }

  @Test
  void formatsOperationWithSymbolsAndGrouping() {
    assertEquals("150,000 × 0.13 = 19,500",
        TraceFormatter.operation(num("150000"), "*", num("0.13"), num("19500")));
    assertEquals("150,000 > 100,000 = true",
        TraceFormatter.operation(num("150000"), ">", num("100000"), EvaluationValue.of(true)));
    assertEquals("1 ÷ 3 = 0.3333333333333333333333333333333333",
        TraceFormatter.operation(num("1"), "/", num("3"),
            num("0.3333333333333333333333333333333333")));
    assertEquals("2 + 3 = 5",
        TraceFormatter.operation(num("2"), "+", num("3"), num("5")));
  }

  @Test
  void formatsUnaryOperation() {
    assertEquals("-5 = -5", TraceFormatter.unaryOperation("-", num("5"), num("-5")));
  }

  @Test
  void formatsConditionBranchFunctionAndResult() {
    assertEquals("Evaluate condition amount>100000 → true",
        TraceFormatter.condition("amount>100000", true));
    assertEquals("Take branch amount×0.13",
        TraceFormatter.branch("amount*0.13"));
    assertEquals("Call MAX(1,2) = 2",
        TraceFormatter.function("MAX(1,2)", num("2")));
    assertEquals("Call IF(amount>100000,amount×0.13,amount×0.03) = 19,500",
        TraceFormatter.function("IF(amount>100000,amount*0.13,amount*0.03)", num("19500")));
    assertEquals("Result: 1+2*3 = 7",
        TraceFormatter.result("1+2*3", num("7")));
  }
}
