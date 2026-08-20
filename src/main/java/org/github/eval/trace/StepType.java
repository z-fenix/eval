package org.github.eval.trace;

/** The kind of evaluation event a {@link Step} records. */
public enum StepType {
  VARIABLE,
  OPERATION,
  CONDITION,
  BRANCH,
  FUNCTION,
  RESULT
}
