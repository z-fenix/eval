package org.github.eval.functions;

import java.util.List;
import org.github.eval.data.EvaluationValue;
import org.github.eval.parser.EvaluationContext;

public class OrFunction extends AbstractFunction {

  @Override
  protected EvaluationValue evaluateValues(
      List<EvaluationValue> arguments, EvaluationContext context) {
    requireMinArgumentCount(arguments, 1, "OR");
    for (EvaluationValue argument : arguments) {
      if (argument.getBooleanValue()) {
        return EvaluationValue.of(true);
      }
    }
    return EvaluationValue.of(false);
  }
}
