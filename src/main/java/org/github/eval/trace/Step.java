package org.github.eval.trace;

import java.util.Objects;

import jakarta.annotation.Nonnull;
import org.github.eval.data.EvaluationValue;

/**
 * One recorded evaluation event. Immutable.
 *
 * @param value nullable, e.g. for BRANCH
 */
public record Step(StepType type, String description, EvaluationValue value) {

  public Step(StepType type, String description, EvaluationValue value) {
    this.type = Objects.requireNonNull(type, "type");
    this.description = Objects.requireNonNull(description, "description");
    this.value = value;
  }

  /**
   * The value this step produced, or null when not applicable (e.g. BRANCH).
   */
  @Override
  public EvaluationValue value() {
    return value;
  }

  @Override
  public @Nonnull String toString() {
    return type + ": " + description;
  }
}
