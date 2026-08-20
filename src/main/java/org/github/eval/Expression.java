package org.github.eval;

import java.util.Map;
import java.util.TreeMap;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.github.eval.data.EvaluationValue;
import org.github.eval.parser.EvaluationVisitor;
import org.github.eval.parser.ExprLexer;
import org.github.eval.parser.ExprParser;
import org.github.eval.parser.ThrowingErrorListener;

/** Public facade: parse once, then evaluate (optionally many times with different variables). */
public class Expression {

  private final ExprParser.ExpressionContext parseTree;
  private final ExpressionConfiguration configuration;
  private final Map<String, EvaluationValue> variables =
      new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

  public Expression(String expressionString) {
    this(expressionString, ExpressionConfiguration.defaultConfiguration());
  }

  public Expression(String expressionString, ExpressionConfiguration configuration) {
    this.configuration = configuration;
    ExprLexer lexer = new ExprLexer(CharStreams.fromString(expressionString));
    lexer.removeErrorListeners();
    lexer.addErrorListener(ThrowingErrorListener.INSTANCE);
    ExprParser parser = new ExprParser(new CommonTokenStream(lexer));
    parser.removeErrorListeners();
    parser.addErrorListener(ThrowingErrorListener.INSTANCE);
    this.parseTree = parser.expression();
  }

  /** Binds a variable (fluent). Lookup is case-insensitive. */
  public Expression with(String variableName, Object value) {
    variables.put(variableName, EvaluationValue.fromObject(value));
    return this;
  }

  public EvaluationValue evaluate() {
    return evaluate(Map.of());
  }

  public EvaluationValue evaluate(Map<String, ?> variables) {
    Map<String, EvaluationValue> allVariables = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    allVariables.putAll(this.variables);
    variables.forEach((name, value) -> allVariables.put(name, EvaluationValue.fromObject(value)));
    EvaluationVisitor visitor = new EvaluationVisitor(configuration, allVariables);
    return visitor.visit(parseTree);
  }
}
