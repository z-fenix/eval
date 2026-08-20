package org.github.eval.functions;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.github.eval.data.EvaluationValue;
import org.github.eval.parser.EvaluationContext;

/** Excel ROUND: half away from zero. ROUND(2.5,0)=3, ROUND(-2.5,0)=-3. */
public class RoundFunction extends AbstractFunction {

  @Override
  protected EvaluationValue evaluateValues(
      List<EvaluationValue> arguments, EvaluationContext context) {
    requireExactArgumentCount(arguments, 2, "ROUND");
    BigDecimal value = arguments.get(0).getNumberValue();
    int digits = arguments.get(1).getNumberValue().intValue();
    return EvaluationValue.of(value.setScale(digits, RoundingMode.HALF_UP));
  }
}
