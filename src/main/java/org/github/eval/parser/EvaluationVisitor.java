package org.github.eval.parser;

import java.math.BigDecimal;
import java.util.Map;
import org.github.eval.ExpressionConfiguration;
import org.github.eval.data.EvaluationValue;
import org.github.eval.operators.ArithmeticOperators;
import org.github.eval.operators.ComparisonOperators;
import org.github.eval.operators.ConcatenationOperator;
import org.github.eval.functions.FunctionIfc;

/** Walks the parse tree and computes the expression's value. */
public class EvaluationVisitor extends ExprBaseVisitor<EvaluationValue> {

  private final EvaluationContext context;

  public EvaluationVisitor(
      ExpressionConfiguration configuration, Map<String, EvaluationValue> variables) {
    this.context = new EvaluationContext(configuration, variables, this);
  }

  public EvaluationContext getContext() {
    return context;
  }

  @Override
  public EvaluationValue visitExpression(ExprParser.ExpressionContext ctx) {
    return visit(ctx.comparison());
  }

  @Override
  public EvaluationValue visitComparison(ExprParser.ComparisonContext ctx) {
    EvaluationValue result = visit(ctx.concatenation(0));
    for (int i = 0; i < ctx.comparisonOperator().size(); i++) {
      EvaluationValue right = visit(ctx.concatenation(i + 1));
      result = ComparisonOperators.apply(ctx.comparisonOperator(i).getText(), result, right);
    }
    return result;
  }

  @Override
  public EvaluationValue visitConcatenation(ExprParser.ConcatenationContext ctx) {
    EvaluationValue result = visit(ctx.additive(0));
    for (int i = 1; i < ctx.additive().size(); i++) {
      result = ConcatenationOperator.concat(result, visit(ctx.additive(i)));
    }
    return result;
  }

  @Override
  public EvaluationValue visitAdditive(ExprParser.AdditiveContext ctx) {
    EvaluationValue result = visit(ctx.multiplicative(0));
    for (int i = 0; i < ctx.additiveOperator().size(); i++) {
      EvaluationValue right = visit(ctx.multiplicative(i + 1));
      result =
          ctx.additiveOperator(i).getText().equals("+")
              ? ArithmeticOperators.add(result, right, context.getMathContext())
              : ArithmeticOperators.subtract(result, right, context.getMathContext());
    }
    return result;
  }

  @Override
  public EvaluationValue visitMultiplicative(ExprParser.MultiplicativeContext ctx) {
    EvaluationValue result = visit(ctx.unary(0));
    for (int i = 0; i < ctx.multiplicativeOperator().size(); i++) {
      EvaluationValue right = visit(ctx.unary(i + 1));
      result =
          ctx.multiplicativeOperator(i).getText().equals("*")
              ? ArithmeticOperators.multiply(result, right, context.getMathContext())
              : ArithmeticOperators.divide(result, right, context.getMathContext());
    }
    return result;
  }

  @Override
  public EvaluationValue visitUnary(ExprParser.UnaryContext ctx) {
    if (ctx.sign == null) {
      return visit(ctx.primary());
    }
    EvaluationValue value = visit(ctx.unary());
    return ctx.sign.getText().equals("-")
        ? ArithmeticOperators.negate(value)
        : ArithmeticOperators.unaryPlus(value);
  }

  @Override
  public EvaluationValue visitPrimary(ExprParser.PrimaryContext ctx) {
    if (ctx.NUMBER() != null) {
      return EvaluationValue.of(new BigDecimal(ctx.NUMBER().getText()));
    }
    if (ctx.STRING() != null) {
      String text = ctx.STRING().getText();
      return EvaluationValue.of(text.substring(1, text.length() - 1).replace("\"\"", "\""));
    }
    if (ctx.booleanLiteral() != null) {
      return EvaluationValue.of(ctx.booleanLiteral().getText().equalsIgnoreCase("true"));
    }
    if (ctx.variable() != null) {
      return context.getVariable(ctx.variable().getText());
    }
    if (ctx.functionCall() != null) {
      return visit(ctx.functionCall());
    }
    return visit(ctx.comparison()); // '(' comparison ')'
  }

  @Override
  public EvaluationValue visitFunctionCall(ExprParser.FunctionCallContext ctx) {
    FunctionIfc function =
        context.getFunctionRegistry().getFunction(ctx.IDENTIFIER().getText());
    return function.evaluate(ctx.comparison(), context);
  }
}
