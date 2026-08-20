package org.github.eval.trace;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Collects steps in evaluation order. */
public final class CollectingTracer implements EvaluationTracer {

  private final List<Step> steps = new ArrayList<>();

  @Override
  public void record(Step step) {
    steps.add(step);
  }

  @Override
  public boolean isActive() {
    return true;
  }

  public List<Step> getSteps() {
    return Collections.unmodifiableList(steps);
  }
}
