package org.github.eval.trace;

import org.github.eval.data.EvaluationValue;

/** Single place for English step wording and number/operator formatting. */
public final class TraceFormatter {

  private TraceFormatter() {}

  public static String variable(String name, EvaluationValue value) {
    return "Resolve variable " + name + " = " + formatValue(value);
  }

  public static String operation(
      EvaluationValue left, String operator, EvaluationValue right, EvaluationValue result) {
    return formatValue(left)
        + " " + symbol(operator) + " "
        + formatValue(right)
        + " = " + formatValue(result);
  }

  public static String unaryOperation(String sign, EvaluationValue operand, EvaluationValue result) {
    return sign + formatValue(operand) + " = " + formatValue(result);
  }

  public static String condition(String conditionText, boolean result) {
    return "Evaluate condition " + normalizeOperators(conditionText) + " → " + result;
  }

  public static String branch(String branchText) {
    return "Take branch " + normalizeOperators(branchText);
  }

  public static String function(String callText, EvaluationValue result) {
    return "Call " + normalizeOperators(callText) + " = " + formatValue(result);
  }

  public static String result(String originalExpression, EvaluationValue result) {
    return "Result: " + originalExpression + " = " + formatValue(result);
  }

  private static String symbol(String operator) {
    return switch (operator) {
      case "*" -> "×";
      case "/" -> "÷";
      default -> operator; // + - & = <> < > <= >=
    };
  }

  private static String normalizeOperators(String text) {
    StringBuilder result = new StringBuilder(text.length());
    boolean inString = false;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (inString) {
        if (c == '"') {
          result.append('"');
          if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
            result.append('"');
            i++; // skip the escaped quote
          } else {
            inString = false;
          }
        } else {
          result.append(c);
        }
      } else {
        if (c == '"') {
          result.append('"');
          inString = true;
        } else if (c == '*') {
          result.append('×');
        } else if (c == '/') {
          result.append('÷');
        } else {
          result.append(c);
        }
      }
    }
    return result.toString();
  }

  private static String formatValue(EvaluationValue value) {
    return switch (value.getDataType()) {
      // getStringValue for a NUMBER is plain, trailing zeros stripped, zero → "0"
      case NUMBER -> group(value.getStringValue());
      case STRING -> value.getStringValue();
      case BOOLEAN -> Boolean.toString(value.getBooleanValue()); // lowercase true/false
    };
  }

  /** Inserts thousands separators into the integer part of a plain (un-grouped) numeric string. */
  private static String group(String plain) {
    String sign = "";
    if (plain.startsWith("-")) {
      sign = "-";
      plain = plain.substring(1);
    }
    int dot = plain.indexOf('.');
    String intPart = dot < 0 ? plain : plain.substring(0, dot);
    String fracPart = dot < 0 ? "" : plain.substring(dot);
    StringBuilder grouped = new StringBuilder();
    int length = intPart.length();
    for (int i = 0; i < length; i++) {
      if (i > 0 && (length - i) % 3 == 0) {
        grouped.append(',');
      }
      grouped.append(intPart.charAt(i));
    }
    return sign + grouped + fracPart;
  }
}
