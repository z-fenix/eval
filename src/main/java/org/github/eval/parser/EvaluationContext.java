package org.github.eval.parser;

import java.math.MathContext;
import java.util.Map;
import org.github.eval.EvaluationException;
import org.github.eval.ExpressionConfiguration;
import org.github.eval.data.EvaluationValue;
import org.github.eval.functions.FunctionRegistry;
import org.github.eval.trace.EvaluationTracer;
import org.github.eval.trace.NoOpTracer;

/** Per-evaluation state: variables, configuration, and a bridge back to the visitor. */
public class EvaluationContext {

  private final ExpressionConfiguration configuration;
  private final Map<String, EvaluationValue> variables;
  private final EvaluationVisitor visitor;
  private final EvaluationTracer tracer;
  private final FunctionRegistry functionRegistry = FunctionRegistry.defaultRegistry();

  public EvaluationContext(
      ExpressionConfiguration configuration,
      Map<String, EvaluationValue> variables,
      EvaluationVisitor visitor) {
    this(configuration, variables, visitor, NoOpTracer.INSTANCE);
  }

  public EvaluationContext(
      ExpressionConfiguration configuration,
      Map<String, EvaluationValue> variables,
      EvaluationVisitor visitor,
      EvaluationTracer tracer) {
    this.configuration = configuration;
    this.variables = variables;
    this.visitor = visitor;
    this.tracer = tracer;
  }

  /** Evaluates a sub-expression — used by functions, lazily in the case of IF. */
  public EvaluationValue evaluate(ExprParser.ComparisonContext context) {
    return visitor.visitComparison(context);
  }

  public EvaluationValue getVariable(String name) {
    EvaluationValue value = variables.get(name);
    if (value == null) {
      throw new EvaluationException("Unknown variable: " + name);
    }
    return value;
  }

  public MathContext getMathContext() {
    return configuration.getMathContext();
  }

  public FunctionRegistry getFunctionRegistry() {
    return functionRegistry;
  }

  public EvaluationTracer getTracer() {
    return tracer;
  }
}
