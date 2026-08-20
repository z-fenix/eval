package org.github.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.List;
import org.github.eval.data.EvaluationValue;
import org.github.eval.trace.Step;
import org.junit.jupiter.api.Test;

class ExplainFunctionTest {

  private static List<String> descriptions(Explanation explanation) {
    return explanation.steps().stream().map(Step::description).toList();
  }

  @Test
  void ifTracesConditionBranchAndCall() {
    Explanation explanation =
        new Expression("IF(amount>100000, amount*0.13, amount*0.03)")
            .with("amount", 150000)
            .explain();
    assertEquals(EvaluationValue.of(new BigDecimal("19500")), explanation.value());
    assertEquals(
        List.of(
            "Resolve variable amount = 150,000",
            "150,000 > 100,000 = true",
            "Evaluate condition amount>100000 → true",
            "Take branch amount×0.13",
            "Resolve variable amount = 150,000",
            "150,000 × 0.13 = 19,500",
            "Call IF(amount>100000,amount×0.13,amount×0.03) = 19,500",
            "Result: IF(amount>100000, amount*0.13, amount*0.03) = 19,500"),
        descriptions(explanation));
  }

  @Test
  void ifFalseBranchIsTracedAndTrueBranchIsNot() {
    Explanation explanation =
        new Expression("IF(1>2, 1/0, 5)").explain(); // untaken 1/0 must not be traced or evaluated
    assertEquals(EvaluationValue.of(new BigDecimal("5")), explanation.value());
    assertEquals(
        List.of(
            "1 > 2 = false",
            "Evaluate condition 1>2 → false",
            "Take branch 5",
            "Call IF(1>2,1÷0,5) = 5",
            "Result: IF(1>2, 1/0, 5) = 5"),
        descriptions(explanation));
  }

  @Test
  void nestedIfTracesInDepthFirstOrder() {
    Explanation explanation = new Expression("IF(TRUE, IF(FALSE, 1, 2), 3)").explain();
    assertEquals(EvaluationValue.of(new BigDecimal("2")), explanation.value());
    assertEquals(
        List.of(
            "Evaluate condition TRUE → true",
            "Take branch IF(FALSE,1,2)",
            "Evaluate condition FALSE → false",
            "Take branch 2",
            "Call IF(FALSE,1,2) = 2",
            "Call IF(TRUE,IF(FALSE,1,2),3) = 2",
            "Result: IF(TRUE, IF(FALSE, 1, 2), 3) = 2"),
        descriptions(explanation));
  }

  @Test
  void maxAndRoundAreTracedAsFunctionCalls() {
    Explanation explanation = new Expression("ROUND(MAX(1, 2.5), 0)").explain();
    assertEquals(EvaluationValue.of(new BigDecimal("3")), explanation.value());
    assertEquals(
        List.of(
            "Call MAX(1,2.5) = 2.5",
            "Call ROUND(MAX(1,2.5),0) = 3",
            "Result: ROUND(MAX(1, 2.5), 0) = 3"),
        descriptions(explanation));
  }
}
