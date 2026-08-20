package org.github.eval.trace;

/** Default tracer: ignores everything and reports inactive so no descriptions are built. */
public final class NoOpTracer implements EvaluationTracer {

  public static final NoOpTracer INSTANCE = new NoOpTracer();

  private NoOpTracer() {}

  @Override
  public void record(Step step) {}

  @Override
  public boolean isActive() {
    return false;
  }
}
