package org.github.eval;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.github.eval.data.EvaluationValue;
import org.github.eval.parser.EvaluationVisitor;
import org.github.eval.parser.ExprLexer;
import org.github.eval.parser.ExprParser;
import org.github.eval.parser.ThrowingErrorListener;
import org.github.eval.trace.CollectingTracer;
import org.github.eval.trace.Step;
import org.github.eval.trace.StepType;
import org.github.eval.trace.TraceFormatter;

/** Public facade: parse once, then evaluate (optionally many times with different variables). */
public class Expression {

  private final String expressionString;
  private final ExprParser.ExpressionContext parseTree;
  private final ExpressionConfiguration configuration;
  private final Map<String, EvaluationValue> variables =
      new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

  public Expression(String expressionString) {
    this(expressionString, ExpressionConfiguration.defaultConfiguration());
  }

  public Expression(String expressionString, ExpressionConfiguration configuration) {
    this.expressionString = expressionString;
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
    EvaluationVisitor visitor = new EvaluationVisitor(configuration, mergedVariables(variables));
    return visitor.visit(parseTree);
  }

  public Explanation explain() {
    return explain(Map.of());
  }

  public Explanation explain(Map<String, ?> variables) {
    CollectingTracer tracer = new CollectingTracer();
    EvaluationVisitor visitor =
        new EvaluationVisitor(configuration, mergedVariables(variables), tracer);
    EvaluationValue value = visitor.visit(parseTree);
    List<Step> steps = new ArrayList<>(tracer.getSteps());
    steps.add(
        new Step(StepType.RESULT, TraceFormatter.result(expressionString, value), value));
    return new Explanation(value, steps);
  }

  private Map<String, EvaluationValue> mergedVariables(Map<String, ?> variables) {
    Map<String, EvaluationValue> allVariables = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    allVariables.putAll(this.variables);
    variables.forEach((name, value) -> allVariables.put(name, EvaluationValue.fromObject(value)));
    return allVariables;
  }
}
