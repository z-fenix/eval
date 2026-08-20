package org.github.eval.functions;

import java.math.BigDecimal;
import java.util.List;
import org.github.eval.data.EvaluationValue;
import org.github.eval.parser.EvaluationContext;

public class MaxFunction extends AbstractFunction {

  @Override
  protected EvaluationValue evaluateValues(
      List<EvaluationValue> arguments, EvaluationContext context) {
    requireMinArgumentCount(arguments, 1, "MAX");
    BigDecimal max = arguments.get(0).getNumberValue();
    for (int i = 1; i < arguments.size(); i++) {
      BigDecimal current = arguments.get(i).getNumberValue();
      if (current.compareTo(max) > 0) {
        max = current;
      }
    }
    return EvaluationValue.of(max);
  }
}
