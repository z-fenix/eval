package org.github.eval;

/** Thrown when evaluation fails: unknown variable/function, type mismatch, division by zero, … */
public class EvaluationException extends RuntimeException {

  public EvaluationException(String message) {
    super(message);
  }
}
