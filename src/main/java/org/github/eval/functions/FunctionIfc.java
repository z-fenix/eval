package org.github.eval.functions;

import java.util.List;
import org.github.eval.data.EvaluationValue;
import org.github.eval.parser.EvaluationContext;
import org.github.eval.parser.ExprParser;

/** A callable function. Lazy functions receive unevaluated argument subtrees (see IF). */
public interface FunctionIfc {

  /** When true, the function receives argument parse contexts and decides what to evaluate. */
  boolean isLazyArguments();

  EvaluationValue evaluate(
      List<ExprParser.ComparisonContext> arguments, EvaluationContext context);
}
