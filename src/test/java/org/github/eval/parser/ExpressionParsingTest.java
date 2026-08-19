package org.github.eval.parser;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.github.eval.ParseException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ExpressionParsingTest {

  private ExprParser.ExpressionContext parse(String expression) {
    ExprLexer lexer = new ExprLexer(CharStreams.fromString(expression));
    lexer.removeErrorListeners();
    lexer.addErrorListener(ThrowingErrorListener.INSTANCE);
    ExprParser parser = new ExprParser(new CommonTokenStream(lexer));
    parser.removeErrorListeners();
    parser.addErrorListener(ThrowingErrorListener.INSTANCE);
    return parser.expression();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "1 + 2 * (3 - 4)",
        "-1.5E3 & \"a\"",
        "IF(A1 > 0, ROUND(B1, 2), 0)",
        "AND(a1 = B_2, TRUE)",
        ".5 / 2 <> 3"
      })
  void parsesValidExpressions(String expression) {
    assertDoesNotThrow(() -> parse(expression));
  }

  @Test
  void throwsParseExceptionWithPositionOnSyntaxError() {
    ParseException exception = assertThrows(ParseException.class, () -> parse("1 +"));
    assertTrue(exception.getMessage().startsWith("line 1:"), exception.getMessage());
  }

  @Test
  void rejectsTrailingGarbage() {
    assertThrows(ParseException.class, () -> parse("1 2"));
  }
}
