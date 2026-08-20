package org.github.eval.trace;

/** Receives evaluation steps. Implementations decide whether to record them. */
public interface EvaluationTracer {

  void record(Step step);

  /** When false, callers skip building step descriptions entirely (the zero-cost path). */
  boolean isActive();
}
