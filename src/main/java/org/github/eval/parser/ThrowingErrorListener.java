package org.github.eval.parser;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.github.eval.ParseException;

/** Converts ANTLR syntax errors into {@link ParseException} instead of printing to stderr. */
public class ThrowingErrorListener extends BaseErrorListener {

  public static final ThrowingErrorListener INSTANCE = new ThrowingErrorListener();

  private ThrowingErrorListener() {}

  @Override
  public void syntaxError(
      Recognizer<?, ?> recognizer,
      Object offendingSymbol,
      int line,
      int charPositionInLine,
      String message,
      RecognitionException e) {
    throw new ParseException("line " + line + ":" + charPositionInLine + " " + message);
  }
}
