package org.github.eval.parser;

import java.math.BigDecimal;
import java.util.Map;
import org.github.eval.ExpressionConfiguration;
import org.github.eval.data.EvaluationValue;
import org.github.eval.operators.ArithmeticOperators;
import org.github.eval.operators.ComparisonOperators;
import org.github.eval.operators.ConcatenationOperator;
import org.github.eval.functions.FunctionIfc;
import org.github.eval.trace.EvaluationTracer;
import org.github.eval.trace.NoOpTracer;
import org.github.eval.trace.Step;
import org.github.eval.trace.StepType;
import org.github.eval.trace.TraceFormatter;

/** Walks the parse tree and computes the expression's value. */
public class EvaluationVisitor extends ExprBaseVisitor<EvaluationValue> {

  private final EvaluationContext context;

  public EvaluationVisitor(
      ExpressionConfiguration configuration, Map<String, EvaluationValue> variables) {
    this(configuration, variables, NoOpTracer.INSTANCE);
  }

  public EvaluationVisitor(
      ExpressionConfiguration configuration,
      Map<String, EvaluationValue> variables,
      EvaluationTracer tracer) {
    this.context = new EvaluationContext(configuration, variables, this, tracer);
  }

  public EvaluationContext getContext() {
    return context;
  }

  private void trace(StepType type, String description, EvaluationValue value) {
    if (context.getTracer().isActive()) {
      context.getTracer().record(new Step(type, description, value));
    }
  }

  @Override
  public EvaluationValue visitExpression(ExprParser.ExpressionContext ctx) {
    return visit(ctx.comparison());
  }

  @Override
  public EvaluationValue visitComparison(ExprParser.ComparisonContext ctx) {
    EvaluationValue result = visit(ctx.concatenation(0));
    for (int i = 0; i < ctx.comparisonOperator().size(); i++) {
      String operator = ctx.comparisonOperator(i).getText();
      EvaluationValue left = result;
      EvaluationValue right = visit(ctx.concatenation(i + 1));
      result = ComparisonOperators.apply(operator, left, right);
      trace(StepType.OPERATION, TraceFormatter.operation(left, operator, right, result), result);
    }
    return result;
  }

  @Override
  public EvaluationValue visitConcatenation(ExprParser.ConcatenationContext ctx) {
    EvaluationValue result = visit(ctx.additive(0));
    for (int i = 1; i < ctx.additive().size(); i++) {
      EvaluationValue left = result;
      EvaluationValue right = visit(ctx.additive(i));
      result = ConcatenationOperator.concat(left, right);
      trace(StepType.OPERATION, TraceFormatter.operation(left, "&", right, result), result);
    }
    return result;
  }

  @Override
  public EvaluationValue visitAdditive(ExprParser.AdditiveContext ctx) {
    EvaluationValue result = visit(ctx.multiplicative(0));
    for (int i = 0; i < ctx.additiveOperator().size(); i++) {
      String operator = ctx.additiveOperator(i).getText();
      EvaluationValue left = result;
      EvaluationValue right = visit(ctx.multiplicative(i + 1));
      result =
          operator.equals("+")
              ? ArithmeticOperators.add(left, right, context.getMathContext())
              : ArithmeticOperators.subtract(left, right, context.getMathContext());
      trace(StepType.OPERATION, TraceFormatter.operation(left, operator, right, result), result);
    }
    return result;
  }

  @Override
  public EvaluationValue visitMultiplicative(ExprParser.MultiplicativeContext ctx) {
    EvaluationValue result = visit(ctx.unary(0));
    for (int i = 0; i < ctx.multiplicativeOperator().size(); i++) {
      String operator = ctx.multiplicativeOperator(i).getText();
      EvaluationValue left = result;
      EvaluationValue right = visit(ctx.unary(i + 1));
      result =
          operator.equals("*")
              ? ArithmeticOperators.multiply(left, right, context.getMathContext())
              : ArithmeticOperators.divide(left, right, context.getMathContext());
      trace(StepType.OPERATION, TraceFormatter.operation(left, operator, right, result), result);
    }
    return result;
  }

  @Override
  public EvaluationValue visitUnary(ExprParser.UnaryContext ctx) {
    if (ctx.sign == null) {
      return visit(ctx.primary());
    }
    EvaluationValue operand = visit(ctx.unary());
    EvaluationValue result =
        ctx.sign.getText().equals("-")
            ? ArithmeticOperators.negate(operand)
            : ArithmeticOperators.unaryPlus(operand);
    trace(
        StepType.OPERATION,
        TraceFormatter.unaryOperation(ctx.sign.getText(), operand, result),
        result);
    return result;
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
      EvaluationValue value = context.getVariable(ctx.variable().getText());
      trace(StepType.VARIABLE, TraceFormatter.variable(ctx.variable().getText(), value), value);
      return value;
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
