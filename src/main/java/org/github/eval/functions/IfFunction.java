package org.github.eval.functions;

import java.util.List;
import org.github.eval.EvaluationException;
import org.github.eval.data.EvaluationValue;
import org.github.eval.parser.EvaluationContext;
import org.github.eval.parser.ExprParser;

/** Excel IF with lazy branch evaluation: only the selected branch is evaluated. */
public class IfFunction implements FunctionIfc {

  @Override
  public boolean isLazyArguments() {
    return true;
  }

  @Override
  public EvaluationValue evaluate(
      List<ExprParser.ComparisonContext> arguments, EvaluationContext context) {
    if (arguments.size() < 2 || arguments.size() > 3) {
      throw new EvaluationException("IF requires 2 or 3 arguments, got " + arguments.size());
    }
    boolean condition = context.evaluate(arguments.get(0)).getBooleanValue();
    if (condition) {
      return context.evaluate(arguments.get(1));
    }
    return arguments.size() == 3 ? context.evaluate(arguments.get(2)) : EvaluationValue.of(false);
  }
}
