package org.github.eval;

import java.util.List;
import org.github.eval.data.EvaluationValue;
import org.github.eval.trace.Step;

/** The result of {@link Expression#explain}: the value plus the ordered evaluation steps. */
public final class Explanation {

  private final EvaluationValue value;
  private final List<Step> steps;

  public Explanation(EvaluationValue value, List<Step> steps) {
    this.value = value;
    this.steps = List.copyOf(steps);
  }

  public EvaluationValue getValue() {
    return value;
  }

  public List<Step> getSteps() {
    return steps;
  }

  /** Numbered, newline-separated English rendering of the steps. */
  public String format() {
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < steps.size(); i++) {
      if (i > 0) {
        builder.append('\n');
      }
      builder.append(i + 1).append(". ").append(steps.get(i).getDescription());
    }
    return builder.toString();
  }
}
