# Evaluation Trace / Explanation Feature Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an `Expression.explain(...)` capability that returns the evaluated value plus an ordered, English, step-by-step trace of how the expression was evaluated.

**Architecture:** An observe-only `EvaluationTracer` hook is threaded through `EvaluationContext` into `EvaluationVisitor` and `IfFunction`. When a `CollectingTracer` is attached (only via `explain`), each visit method emits a `Step`; when the default `NoOpTracer` is attached (plain `evaluate`), an `isActive()` guard skips all description-building so behavior and cost are unchanged. All English wording and number/symbol formatting live in one `TraceFormatter`.

**Tech Stack:** Java 25, Gradle 9.7 (Kotlin DSL), ANTLR4 4.13.2, JUnit 6.

**Spec:** `docs/superpowers/specs/2026-08-20-evaluation-trace-design.md`

## Global Constraints

- Base package: `org.github.eval`. New trace classes go in `org.github.eval.trace`.
- Tracing is **observe-only** and must never change evaluation semantics or results.
- Plain `evaluate()` must be behaviorally identical and pay nothing: an `isActive()` guard wraps every step-building call; `NoOpTracer.isActive()` returns `false`.
- Step descriptions are **English**, rendered once at capture by `TraceFormatter` (no English strings hard-coded in the visitor or functions).
- Numbers render with thousands separators (`150,000`, `19,500`), trailing zeros stripped, zero → `0`; operators render as `×` and `÷` inside trace text; booleans render lowercase `true`/`false`.
- Only the **taken** IF branch is traced (matches lazy evaluation); the untaken branch emits nothing.
- If evaluation throws (`ParseException`/`EvaluationException`), the exception propagates exactly as in `evaluate()`; no `Explanation` is returned.
- Tests are JUnit 6 (`org.junit.jupiter`), run with `./gradlew test`.

---

### Task 1: `trace` package — step model, tracers, and formatter

**Files:**
- Create: `src/main/java/org/github/eval/trace/StepType.java`
- Create: `src/main/java/org/github/eval/trace/Step.java`
- Create: `src/main/java/org/github/eval/trace/EvaluationTracer.java`
- Create: `src/main/java/org/github/eval/trace/NoOpTracer.java`
- Create: `src/main/java/org/github/eval/trace/CollectingTracer.java`
- Create: `src/main/java/org/github/eval/trace/TraceFormatter.java`
- Test: `src/test/java/org/github/eval/trace/TraceTest.java`

**Interfaces:**
- Consumes: `org.github.eval.data.EvaluationValue` (existing: `getDataType()` → `DataType{NUMBER,STRING,BOOLEAN}`, `getStringValue()` → `String`, `getBooleanValue()` → `boolean`).
- Produces (used by Tasks 2–3):
  - `StepType` enum: `VARIABLE, OPERATION, CONDITION, BRANCH, FUNCTION, RESULT`.
  - `Step(StepType type, String description, EvaluationValue value)`; `getType()`, `getDescription()`, `getValue()` (value nullable).
  - `EvaluationTracer` interface: `void record(Step step)`; `boolean isActive()`.
  - `NoOpTracer.INSTANCE`; `isActive()` → `false`.
  - `CollectingTracer()`; `isActive()` → `true`; `getSteps()` → `List<Step>` (unmodifiable, insertion order).
  - `TraceFormatter` static methods (all return `String`): `variable(String name, EvaluationValue value)`, `operation(EvaluationValue left, String operator, EvaluationValue right, EvaluationValue result)`, `unaryOperation(String sign, EvaluationValue operand, EvaluationValue result)`, `condition(String conditionText, boolean result)`, `branch(String branchText)`, `function(String callText, EvaluationValue result)`, `result(String originalExpression, EvaluationValue result)`.

- [ ] **Step 1: Write the failing test**

`src/test/java/org/github/eval/trace/TraceTest.java`:

```java
package org.github.eval.trace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.github.eval.data.EvaluationValue;
import org.junit.jupiter.api.Test;

class TraceTest {

  private static EvaluationValue num(String value) {
    return EvaluationValue.of(new BigDecimal(value));
  }

  @Test
  void stepHoldsTypeDescriptionAndNullableValue() {
    Step withValue = new Step(StepType.OPERATION, "1 + 2 = 3", num("3"));
    assertEquals(StepType.OPERATION, withValue.getType());
    assertEquals("1 + 2 = 3", withValue.getDescription());
    assertEquals(num("3"), withValue.getValue());

    Step branch = new Step(StepType.BRANCH, "Take branch x", null);
    assertNull(branch.getValue());
  }

  @Test
  void noOpTracerIsInactive() {
    assertFalse(NoOpTracer.INSTANCE.isActive());
    NoOpTracer.INSTANCE.record(new Step(StepType.RESULT, "ignored", num("1"))); // must not throw
  }

  @Test
  void collectingTracerKeepsInsertionOrder() {
    CollectingTracer tracer = new CollectingTracer();
    assertTrue(tracer.isActive());
    tracer.record(new Step(StepType.VARIABLE, "a", num("1")));
    tracer.record(new Step(StepType.OPERATION, "b", num("2")));
    assertEquals(2, tracer.getSteps().size());
    assertEquals("a", tracer.getSteps().get(0).getDescription());
    assertEquals("b", tracer.getSteps().get(1).getDescription());
  }

  @Test
  void formatsVariableWithGrouping() {
    assertEquals("Resolve variable amount = 150,000",
        TraceFormatter.variable("amount", num("150000")));
    assertEquals("Resolve variable x = 1,234,567.89",
        TraceFormatter.variable("x", num("1234567.89")));
    assertEquals("Resolve variable z = 0",
        TraceFormatter.variable("z", num("0.00")));
  }

  @Test
  void formatsOperationWithSymbolsAndGrouping() {
    assertEquals("150,000 × 0.13 = 19,500",
        TraceFormatter.operation(num("150000"), "*", num("0.13"), num("19500")));
    assertEquals("150,000 > 100,000 = true",
        TraceFormatter.operation(num("150000"), ">", num("100000"), EvaluationValue.of(true)));
    assertEquals("1 ÷ 3 = 0.3333333333333333333333333333333333",
        TraceFormatter.operation(num("1"), "/", num("3"),
            num("0.3333333333333333333333333333333333")));
    assertEquals("2 + 3 = 5",
        TraceFormatter.operation(num("2"), "+", num("3"), num("5")));
  }

  @Test
  void formatsUnaryOperation() {
    assertEquals("-5 = -5", TraceFormatter.unaryOperation("-", num("5"), num("-5")));
  }

  @Test
  void formatsConditionBranchFunctionAndResult() {
    assertEquals("Evaluate condition amount>100000 → true",
        TraceFormatter.condition("amount>100000", true));
    assertEquals("Take branch amount×0.13",
        TraceFormatter.branch("amount*0.13"));
    assertEquals("Call MAX(1,2) = 2",
        TraceFormatter.function("MAX(1,2)", num("2")));
    assertEquals("Call IF(amount>100000,amount×0.13,amount×0.03) = 19,500",
        TraceFormatter.function("IF(amount>100000,amount*0.13,amount*0.03)", num("19500")));
    assertEquals("Result: 1+2*3 = 7",
        TraceFormatter.result("1+2*3", num("7")));
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "org.github.eval.trace.TraceTest"`
Expected: compilation failure — the `trace` package classes do not exist.

- [ ] **Step 3: Implement the six classes**

`src/main/java/org/github/eval/trace/StepType.java`:

```java
package org.github.eval.trace;

/** The kind of evaluation event a {@link Step} records. */
public enum StepType {
  VARIABLE,
  OPERATION,
  CONDITION,
  BRANCH,
  FUNCTION,
  RESULT
}
```

`src/main/java/org/github/eval/trace/Step.java`:

```java
package org.github.eval.trace;

import java.util.Objects;
import org.github.eval.data.EvaluationValue;

/** One recorded evaluation event. Immutable. */
public final class Step {

  private final StepType type;
  private final String description;
  private final EvaluationValue value; // nullable, e.g. for BRANCH

  public Step(StepType type, String description, EvaluationValue value) {
    this.type = Objects.requireNonNull(type, "type");
    this.description = Objects.requireNonNull(description, "description");
    this.value = value;
  }

  public StepType getType() {
    return type;
  }

  public String getDescription() {
    return description;
  }

  /** The value this step produced, or null when not applicable (e.g. BRANCH). */
  public EvaluationValue getValue() {
    return value;
  }

  @Override
  public String toString() {
    return type + ": " + description;
  }
}
```

`src/main/java/org/github/eval/trace/EvaluationTracer.java`:

```java
package org.github.eval.trace;

/** Receives evaluation steps. Implementations decide whether to record them. */
public interface EvaluationTracer {

  void record(Step step);

  /** When false, callers skip building step descriptions entirely (the zero-cost path). */
  boolean isActive();
}
```

`src/main/java/org/github/eval/trace/NoOpTracer.java`:

```java
package org.github.eval.trace;

/** Default tracer: ignores everything and reports inactive so no descriptions are built. */
public final class NoOpTracer implements EvaluationTracer {

  public static final NoOpTracer INSTANCE = new NoOpTracer();

  private NoOpTracer() {}

  @Override
  public void record(Step step) {}

  @Override
  public boolean isActive() {
    return false;
  }
}
```

`src/main/java/org/github/eval/trace/CollectingTracer.java`:

```java
package org.github.eval.trace;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Collects steps in evaluation order. */
public final class CollectingTracer implements EvaluationTracer {

  private final List<Step> steps = new ArrayList<>();

  @Override
  public void record(Step step) {
    steps.add(step);
  }

  @Override
  public boolean isActive() {
    return true;
  }

  public List<Step> getSteps() {
    return Collections.unmodifiableList(steps);
  }
}
```

`src/main/java/org/github/eval/trace/TraceFormatter.java`:

```java
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
    return text.replace("*", "×").replace("/", "÷");
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
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "org.github.eval.trace.TraceTest"`
Expected: PASS (7 tests). Watch `group` on `0.00` → `0` and on `1234567.89` → `1,234,567.89`.

- [ ] **Step 5: Commit**

```bash
git add src/
git commit -m "feat: trace package with step model, tracers, and formatter"
```

---

### Task 2: Tracer plumbing + arithmetic/variable tracing + `explain` API

**Files:**
- Modify: `src/main/java/org/github/eval/parser/EvaluationContext.java` (add tracer field, 4-arg constructor, `getTracer()`)
- Modify: `src/main/java/org/github/eval/parser/EvaluationVisitor.java` (add 3-arg constructor; emit VARIABLE/OPERATION steps)
- Modify: `src/main/java/org/github/eval/Expression.java` (store `expressionString`, extract `mergedVariables`, add `explain`)
- Create: `src/main/java/org/github/eval/Explanation.java`
- Test: `src/test/java/org/github/eval/ExplainArithmeticTest.java`

**Interfaces:**
- Consumes: Task 1 trace package (`Step`, `StepType`, `EvaluationTracer`, `NoOpTracer`, `CollectingTracer`, `TraceFormatter`); existing `EvaluationContext`/`EvaluationVisitor`/`Expression`.
- Produces:
  - `EvaluationContext(ExpressionConfiguration, Map<String,EvaluationValue>, EvaluationVisitor, EvaluationTracer)` (new 4-arg ctor; the existing 3-arg ctor delegates with `NoOpTracer.INSTANCE`); `getTracer()` → `EvaluationTracer`.
  - `EvaluationVisitor(ExpressionConfiguration, Map<String,EvaluationValue>, EvaluationTracer)` (new 3-arg ctor; existing 2-arg delegates with `NoOpTracer.INSTANCE`).
  - `Expression.explain()` and `Expression.explain(Map<String, ?>)` → `Explanation`.
  - `Explanation(EvaluationValue value, List<Step> steps)`; `getValue()`, `getSteps()`, `format()` → numbered newline-separated string.
- Note for Task 3: the visitor's `visitFunctionCall` and `IfFunction` do NOT emit yet; that lands in Task 3. This task emits only VARIABLE + OPERATION (binary + unary) and the terminal RESULT.

- [ ] **Step 1: Write the failing test**

`src/test/java/org/github/eval/ExplainArithmeticTest.java`:

```java
package org.github.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.Map;
import org.github.eval.data.EvaluationValue;
import org.junit.jupiter.api.Test;

class ExplainArithmeticTest {

  private static EvaluationValue num(String value) {
    return EvaluationValue.of(new BigDecimal(value));
  }

  @Test
  void explainReturnsValueAndSteps() {
    Explanation explanation = new Expression("1+2*3").explain();
    assertEquals(num("7"), explanation.getValue());
    assertEquals(
        java.util.List.of(
            "2 × 3 = 6",
            "1 + 6 = 7",
            "Result: 1+2*3 = 7"),
        explanation.getSteps().stream().map(s -> s.getDescription()).toList());
  }

  @Test
  void nestedOperationPrecedesOuterOperation() {
    // multiplication is nested inside the addition, so it is traced first
    Explanation explanation = new Expression("1+2*3").explain();
    assertEquals("2 × 3 = 6", explanation.getSteps().get(0).getDescription());
    assertEquals("1 + 6 = 7", explanation.getSteps().get(1).getDescription());
  }

  @Test
  void variableResolutionIsTraced() {
    Explanation explanation = new Expression("price * qty")
        .explain(Map.of("price", new BigDecimal("2.5"), "qty", 4));
    assertEquals(num("10"), explanation.getValue());
    assertEquals(
        java.util.List.of(
            "Resolve variable price = 2.5",
            "Resolve variable qty = 4",
            "2.5 × 4 = 10",
            "Result: price * qty = 10"),
        explanation.getSteps().stream().map(s -> s.getDescription()).toList());
  }

  @Test
  void withBindingsAreTraced() {
    Explanation explanation = new Expression("amount * 0.13").with("amount", 150000).explain();
    assertEquals(num("19500"), explanation.getValue());
    assertEquals(
        java.util.List.of(
            "Resolve variable amount = 150,000",
            "150,000 × 0.13 = 19,500",
            "Result: amount * 0.13 = 19,500"),
        explanation.getSteps().stream().map(s -> s.getDescription()).toList());
  }

  @Test
  void comparisonAndConcatenationAreTraced() {
    Explanation explanation = new Expression("1+1 = 2").explain();
    assertEquals(EvaluationValue.of(true), explanation.getValue());
    assertEquals(
        java.util.List.of(
            "1 + 1 = 2",
            "2 = 2 = true",
            "Result: 1+1 = 2 = true"),
        explanation.getSteps().stream().map(s -> s.getDescription()).toList());

    Explanation concat = new Expression("\"a\" & 1+1").explain();
    assertEquals(
        java.util.List.of(
            "1 + 1 = 2",
            "a & 2 = a2",
            "Result: \"a\" & 1+1 = a2"),
        concat.getSteps().stream().map(s -> s.getDescription()).toList());
  }

  @Test
  void unaryMinusIsTraced() {
    Explanation explanation = new Expression("-5 + 1").explain();
    assertEquals(
        java.util.List.of(
            "-5 = -5",
            "-5 + 1 = -4",
            "Result: -5 + 1 = -4"),
        explanation.getSteps().stream().map(s -> s.getDescription()).toList());
  }

  @Test
  void formatNumbersSteps() {
    Explanation explanation = new Expression("1+2").explain();
    assertEquals("1. 1 + 2 = 3\n2. Result: 1+2 = 3", explanation.format());
  }

  @Test
  void plainEvaluateIsUnchangedAndUntraced() {
    // evaluate() must behave exactly as before and must not require a tracer
    assertEquals(num("3"), new Expression("1+2").evaluate());
    assertEquals(num("6"), new Expression("x*2").with("x", 3).evaluate());
  }

  @Test
  void explainPropagatesEvaluationException() {
    assertThrows(EvaluationException.class, () -> new Expression("1/0").explain());
  }
}
```

Note on `comparisonAndConcatenationAreTraced`: the expression `1+1 = 2` yields step `2 = 2 = true` (left operand `2`, operator `=`, right `2`, result `true`) — this is correct, if terse, formatting of the comparison. The `format` test asserts `\n`-joined numbered lines.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "org.github.eval.ExplainArithmeticTest"`
Expected: compilation failure — `Explanation` and `Expression.explain` do not exist.

- [ ] **Step 3: Implement plumbing, emission, `Explanation`, and `explain`**

Modify `src/main/java/org/github/eval/parser/EvaluationContext.java` — add the import, field, 4-arg constructor, delegate, and getter:

```java
import org.github.eval.trace.EvaluationTracer;
import org.github.eval.trace.NoOpTracer;
```

```java
  private final EvaluationTracer tracer;

  public EvaluationContext(
      ExpressionConfiguration configuration,
      Map<String, EvaluationValue> variables,
      EvaluationVisitor visitor) {
    this(configuration, variables, visitor, NoOpTracer.INSTANCE);
  }

  public EvaluationContext(
      ExpressionConfiguration configuration,
      Map<String, EvaluationValue> variables,
      EvaluationVisitor visitor,
      EvaluationTracer tracer) {
    this.configuration = configuration;
    this.variables = variables;
    this.visitor = visitor;
    this.tracer = tracer;
  }

  public EvaluationTracer getTracer() {
    return tracer;
  }
```

Modify `src/main/java/org/github/eval/parser/EvaluationVisitor.java` — add imports, the 3-arg constructor, and the emission calls. Add imports:

```java
import org.github.eval.trace.EvaluationTracer;
import org.github.eval.trace.NoOpTracer;
import org.github.eval.trace.Step;
import org.github.eval.trace.StepType;
import org.github.eval.trace.TraceFormatter;
```

Replace the constructor:

```java
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
```

Add a private helper and call it from each visit method:

```java
  private void trace(StepType type, String description, EvaluationValue value) {
    if (context.getTracer().isActive()) {
      context.getTracer().record(new Step(type, description, value));
    }
  }
```

`visitComparison` — capture left before applying, then trace:

```java
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
```

`visitConcatenation`:

```java
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
```

`visitAdditive`:

```java
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
```

`visitMultiplicative`:

```java
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
```

`visitUnary`:

```java
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
```

`visitPrimary` — trace the variable branch only:

```java
    if (ctx.variable() != null) {
      EvaluationValue value = context.getVariable(ctx.variable().getText());
      trace(StepType.VARIABLE, TraceFormatter.variable(ctx.variable().getText(), value), value);
      return value;
    }
```

Create `src/main/java/org/github/eval/Explanation.java`:

```java
package org.github.eval;

import java.util.List;
import org.github.eval.data.EvaluationValue;
import org.github.eval.trace.Step;

/** The result of {@link Expression#explain}: the value plus the ordered evaluation steps. */
public final class Explanation {

  private final EvaluationValue value;
  private final List<Step> steps;

  public Explanation(EvaluationValue value, List<Step> steps) {
    this.value = value;
    this.steps = List.copyOf(steps);
  }

  public EvaluationValue getValue() {
    return value;
  }

  public List<Step> getSteps() {
    return steps;
  }

  /** Numbered, newline-separated English rendering of the steps. */
  public String format() {
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < steps.size(); i++) {
      if (i > 0) {
        builder.append('\n');
      }
      builder.append(i + 1).append(". ").append(steps.get(i).getDescription());
    }
    return builder.toString();
  }
}
```

Modify `src/main/java/org/github/eval/Expression.java` — add imports, the `expressionString` field, set it in the constructor, extract `mergedVariables`, and add `explain`. New imports:

```java
import java.util.ArrayList;
import java.util.List;
import org.github.eval.trace.CollectingTracer;
import org.github.eval.trace.Step;
import org.github.eval.trace.StepType;
import org.github.eval.trace.TraceFormatter;
```

Add field and set it (in both constructor paths — the 2-arg delegates to the 3-arg):

```java
  private final String expressionString;
```

```java
  public Expression(String expressionString, ExpressionConfiguration configuration) {
    this.expressionString = expressionString;
    this.configuration = configuration;
    // ... existing lexer/parser setup unchanged ...
    this.parseTree = parser.expression();
  }
```

Refactor `evaluate` to use a shared merge, then add `explain`:

```java
  public EvaluationValue evaluate() {
    return evaluate(Map.of());
  }

  public EvaluationValue evaluate(Map<String, ?> variables) {
    EvaluationVisitor visitor = new EvaluationVisitor(configuration, mergedVariables(variables));
    return visitor.visit(parseTree);
  }

  public Explanation explain() {
    return explain(Map.of());
  }

  public Explanation explain(Map<String, ?> variables) {
    CollectingTracer tracer = new CollectingTracer();
    EvaluationVisitor visitor =
        new EvaluationVisitor(configuration, mergedVariables(variables), tracer);
    EvaluationValue value = visitor.visit(parseTree);
    List<Step> steps = new ArrayList<>(tracer.getSteps());
    steps.add(
        new Step(StepType.RESULT, TraceFormatter.result(expressionString, value), value));
    return new Explanation(value, steps);
  }

  private Map<String, EvaluationValue> mergedVariables(Map<String, ?> variables) {
    Map<String, EvaluationValue> allVariables = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    allVariables.putAll(this.variables);
    variables.forEach((name, value) -> allVariables.put(name, EvaluationValue.fromObject(value)));
    return allVariables;
  }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "org.github.eval.ExplainArithmeticTest"`
Expected: PASS (9 tests). Then run the full suite to confirm nothing regressed: `./gradlew build`.

- [ ] **Step 5: Commit**

```bash
git add src/
git commit -m "feat: tracer plumbing, arithmetic/variable tracing, and explain API"
```

---

### Task 3: Function-call and IF branch tracing

**Files:**
- Modify: `src/main/java/org/github/eval/parser/EvaluationVisitor.java` (`visitFunctionCall` emits FUNCTION)
- Modify: `src/main/java/org/github/eval/functions/IfFunction.java` (emit CONDITION + BRANCH)
- Test: `src/test/java/org/github/eval/ExplainFunctionTest.java`

**Interfaces:**
- Consumes: Task 2 plumbing — `EvaluationContext.getTracer()`, the visitor's private `trace(StepType, String, EvaluationValue)` helper, `TraceFormatter.{function,condition,branch}`.
- Produces: nothing new for later tasks; completes the feature.

- [ ] **Step 1: Write the failing test**

`src/test/java/org/github/eval/ExplainFunctionTest.java`:

```java
package org.github.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.List;
import org.github.eval.data.EvaluationValue;
import org.junit.jupiter.api.Test;

class ExplainFunctionTest {

  private static List<String> descriptions(Explanation explanation) {
    return explanation.getSteps().stream().map(s -> s.getDescription()).toList();
  }

  @Test
  void ifTracesConditionBranchAndCall() {
    Explanation explanation =
        new Expression("IF(amount>100000, amount*0.13, amount*0.03)")
            .with("amount", 150000)
            .explain();
    assertEquals(EvaluationValue.of(new BigDecimal("19500")), explanation.getValue());
    assertEquals(
        List.of(
            "Resolve variable amount = 150,000",
            "150,000 > 100,000 = true",
            "Evaluate condition amount>100000 → true",
            "Take branch amount×0.13",
            "Resolve variable amount = 150,000",
            "150,000 × 0.13 = 19,500",
            "Call IF(amount>100000,amount×0.13,amount×0.03) = 19,500",
            "Result: IF(amount>100000, amount*0.13, amount*0.03) = 19,500"),
        descriptions(explanation));
  }

  @Test
  void ifFalseBranchIsTracedAndTrueBranchIsNot() {
    Explanation explanation =
        new Expression("IF(1>2, 1/0, 5)").explain(); // untaken 1/0 must not be traced or evaluated
    assertEquals(EvaluationValue.of(new BigDecimal("5")), explanation.getValue());
    assertEquals(
        List.of(
            "1 > 2 = false",
            "Evaluate condition 1>2 → false",
            "Take branch 5",
            "Call IF(1>2,1÷0,5) = 5",
            "Result: IF(1>2, 1/0, 5) = 5"),
        descriptions(explanation));
  }

  @Test
  void nestedIfTracesInDepthFirstOrder() {
    Explanation explanation = new Expression("IF(TRUE, IF(FALSE, 1, 2), 3)").explain();
    assertEquals(EvaluationValue.of(new BigDecimal("2")), explanation.getValue());
    assertEquals(
        List.of(
            "Evaluate condition TRUE → true",
            "Take branch IF(FALSE,1,2)",
            "Evaluate condition FALSE → false",
            "Take branch 2",
            "Call IF(FALSE,1,2) = 2",
            "Call IF(TRUE,IF(FALSE,1,2),3) = 2",
            "Result: IF(TRUE, IF(FALSE, 1, 2), 3) = 2"),
        descriptions(explanation));
  }

  @Test
  void maxAndRoundAreTracedAsFunctionCalls() {
    Explanation explanation = new Expression("ROUND(MAX(1, 2.5), 0)").explain();
    assertEquals(EvaluationValue.of(new BigDecimal("3")), explanation.getValue());
    assertEquals(
        List.of(
            "Call MAX(1,2.5) = 2.5",
            "Call ROUND(MAX(1,2.5),0) = 3",
            "Result: ROUND(MAX(1, 2.5), 0) = 3"),
        descriptions(explanation));
  }
}
```

Note on `nestedIfTracesInDepthFirstOrder`: a literal `TRUE`/`FALSE` condition emits no VARIABLE/OPERATION step (literals aren't traced), so the first step is the outer CONDITION. Note on `ifFalseBranch...`: the untaken `1/0` branch produces no steps and is never evaluated — proving tracing stays lazy. In `Take branch 5`, the branch is a literal so no further steps appear before the FUNCTION step.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "org.github.eval.ExplainFunctionTest"`
Expected: FAIL — steps missing FUNCTION/CONDITION/BRANCH entries (the assertions on full step lists fail).

- [ ] **Step 3: Implement FUNCTION and IF emission**

Modify `src/main/java/org/github/eval/parser/EvaluationVisitor.java` `visitFunctionCall` — capture and trace the result:

```java
  @Override
  public EvaluationValue visitFunctionCall(ExprParser.FunctionCallContext ctx) {
    FunctionIfc function =
        context.getFunctionRegistry().getFunction(ctx.IDENTIFIER().getText());
    EvaluationValue result = function.evaluate(ctx.comparison(), context);
    trace(StepType.FUNCTION, TraceFormatter.function(ctx.getText(), result), result);
    return result;
  }
```

Modify `src/main/java/org/github/eval/functions/IfFunction.java` — add imports and emit CONDITION and BRANCH. New imports:

```java
import org.github.eval.trace.Step;
import org.github.eval.trace.StepType;
import org.github.eval.trace.TraceFormatter;
```

Replace the `evaluate` body (after the arity check):

```java
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
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "org.github.eval.ExplainFunctionTest"`
Expected: PASS (4 tests). Then run the full suite: `./gradlew build` — all prior tests plus the new trace tests green.

- [ ] **Step 5: Commit**

```bash
git add src/
git commit -m "feat: function-call and IF branch tracing"
```

---

## Self-Review Notes

- **Spec coverage:** trace package (§3)→Task 1; emission points (§4)→Tasks 2 (variable/operation/result) & 3 (function/condition/branch); public API `explain`/`Explanation` (§5)→Task 2; observe-only + no-op cost + error propagation (§2,§6)→Task 2 (`isActive` guard, `plainEvaluateIsUnchangedAndUntraced`, `explainPropagatesEvaluationException`); the 8-step worked example (§1)→Task 3 `ifTracesConditionBranchAndCall`; nested-IF ordering (§7)→Task 3; number/operator formatting (§3)→Task 1.
- **Type consistency:** `trace(StepType, String, EvaluationValue)` helper signature identical across Tasks 2 and 3; `EvaluationContext.getTracer()` returns `EvaluationTracer` (which has `isActive()`/`record`) used identically in visitor and IfFunction; `TraceFormatter` method names used in Tasks 2–3 match the Task 1 definitions; `ctx.getText()` for function calls yields no spaces and `*`, which `TraceFormatter.function` normalizes to `×` — matching the asserted strings.
- **Placeholder scan:** no TBD/TODO; every code step has full code; every test step has full test code.
