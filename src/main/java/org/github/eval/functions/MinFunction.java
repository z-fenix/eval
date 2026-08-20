package org.github.eval.functions;

import java.math.BigDecimal;
import java.util.List;
import org.github.eval.data.EvaluationValue;
import org.github.eval.parser.EvaluationContext;

public class MinFunction extends AbstractFunction {

  @Override
  protected EvaluationValue evaluateValues(
      List<EvaluationValue> arguments, EvaluationContext context) {
    requireMinArgumentCount(arguments, 1, "MIN");
    BigDecimal min = arguments.get(0).getNumberValue();
    for (int i = 1; i < arguments.size(); i++) {
      BigDecimal current = arguments.get(i).getNumberValue();
      if (current.compareTo(min) < 0) {
        min = current;
      }
    }
    return EvaluationValue.of(min);
  }
}
