package org.github.eval.functions;

import java.util.ArrayList;
import java.util.List;
import org.github.eval.EvaluationException;
import org.github.eval.data.EvaluationValue;
import org.github.eval.parser.EvaluationContext;
import org.github.eval.parser.ExprParser;

/** Base for eager functions: evaluates every argument, then calls {@link #evaluateValues}. */
public abstract class AbstractFunction implements FunctionIfc {

  @Override
  public boolean isLazyArguments() {
    return false;
  }

  @Override
  public EvaluationValue evaluate(
      List<ExprParser.ComparisonContext> arguments, EvaluationContext context) {
    List<EvaluationValue> values = new ArrayList<>(arguments.size());
    for (ExprParser.ComparisonContext argument : arguments) {
      values.add(context.evaluate(argument));
    }
    return evaluateValues(values, context);
  }

  protected abstract EvaluationValue evaluateValues(
      List<EvaluationValue> arguments, EvaluationContext context);

  protected void requireExactArgumentCount(List<?> arguments, int expected, String name) {
    if (arguments.size() != expected) {
      throw new EvaluationException(
          name + " requires exactly " + expected + " argument(s), got " + arguments.size());
    }
  }

  protected void requireMinArgumentCount(List<?> arguments, int min, String name) {
    if (arguments.size() < min) {
      throw new EvaluationException(
          name + " requires at least " + min + " argument(s), got " + arguments.size());
    }
  }
}
