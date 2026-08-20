package org.github.eval.functions;

import java.util.List;
import org.github.eval.EvaluationException;
import org.github.eval.data.EvaluationValue;
import org.github.eval.parser.EvaluationContext;
import org.github.eval.parser.ExprParser;
import org.github.eval.trace.Step;
import org.github.eval.trace.StepType;
import org.github.eval.trace.TraceFormatter;

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
    if (context.getTracer().isActive()) {
      context.getTracer()
          .record(
              new Step(
                  StepType.CONDITION,
                  TraceFormatter.condition(arguments.get(0).getText(), condition),
                  EvaluationValue.of(condition)));
    }
    if (condition) {
      traceBranch(context, arguments.get(1));
      return context.evaluate(arguments.get(1));
    }
    if (arguments.size() == 3) {
      traceBranch(context, arguments.get(2));
      return context.evaluate(arguments.get(2));
    }
    return EvaluationValue.of(false);
  }

  private static void traceBranch(EvaluationContext context, ExprParser.ComparisonContext branch) {
    if (context.getTracer().isActive()) {
      context.getTracer()
          .record(new Step(StepType.BRANCH, TraceFormatter.branch(branch.getText()), null));
    }
  }
}
