package org.github.eval;

/** Thrown when an expression string cannot be parsed. Carries the ANTLR line:column. */
public class ParseException extends RuntimeException {

  public ParseException(String message) {
    super(message);
  }
}
