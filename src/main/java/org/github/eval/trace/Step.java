package org.github.eval.trace;

import java.util.Objects;
import org.github.eval.data.EvaluationValue;

/** One recorded evaluation event. Immutable. */
public final class Step {

  private final StepType type;
  private final String description;
  private final EvaluationValue value; // nullable, e.g. for BRANCH

  public Step(StepType type, String description, EvaluationValue value) {
    this.type = Objects.requireNonNull(type, "type");
    this.description = Objects.requireNonNull(description, "description");
    this.value = value;
  }

  public StepType getType() {
    return type;
  }

  public String getDescription() {
    return description;
  }

  /** The value this step produced, or null when not applicable (e.g. BRANCH). */
  public EvaluationValue getValue() {
    return value;
  }

  @Override
  public String toString() {
    return type + ": " + description;
  }
}
