# Excel-Style Expression Evaluator (ANTLR4) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a Java library that parses and evaluates Excel-style expressions (`IF/AND/OR/ROUND/MAX/MIN`, operators `+ - * / = > < >= <= <> &`, named variables) using an ANTLR4-generated parser and BigDecimal-precision arithmetic.

**Architecture:** `Expression` facade parses the string once into an ANTLR parse tree and retains it; each `evaluate(variables)` walks the tree with a fresh `EvaluationVisitor` producing `EvaluationValue`s. Lazy `IF` never visits the untaken branch. Semantics live in `data` (value/coercion), `operators` (BigDecimal logic), and `functions` (behind `FunctionIfc`); the visitor only dispatches.

**Tech Stack:** Java 25, Gradle 9.7 (Kotlin DSL), ANTLR4 4.13.2, JUnit 6 (Jupiter).

**Spec:** `docs/superpowers/specs/2026-08-20-excel-expression-evaluator-design.md`

## Global Constraints

- Base package: `org.github.eval`.
- All numbers are `java.math.BigDecimal`; never `double`/`float`. Literals parse straight to `BigDecimal`.
- Default `MathContext` is `MathContext.DECIMAL128` (34 digits); division always uses the configured context. `ROUND` uses `RoundingMode.HALF_UP`.
- Function names and variable names are case-insensitive (Excel convention).
- Grammar file lives at `src/main/antlr/org/github/eval/parser/Expr.g4` (generates into package `org.github.eval.parser`); keep semantics out of the `.g4` (no embedded actions).
- Parser syntax errors never print to stderr — they throw `ParseException` with line:column.
- Tests are JUnit 6 (`org.junit.jupiter`), run with `./gradlew test` (`gradlew.bat` on Windows).
- `IF` evaluates lazily: only the selected branch subtree is visited.

---

### Task 1: ANTLR build wiring, grammar, and parse-error plumbing

**Files:**
- Modify: `build.gradle.kts`
- Create: `src/main/antlr/org/github/eval/parser/Expr.g4`
- Create: `src/main/java/org/github/eval/ParseException.java`
- Create: `src/main/java/org/github/eval/parser/ThrowingErrorListener.java`
- Test: `src/test/java/org/github/eval/parser/ExpressionParsingTest.java`

**Interfaces:**
- Consumes: nothing (first task).
- Produces: generated `org.github.eval.parser.ExprLexer`, `ExprParser`, `ExprBaseVisitor`; `ParseException(String)`; `ThrowingErrorListener.INSTANCE`. Grammar rule contexts used later: `ExpressionContext.comparison()`, `ComparisonContext.{concatenation(int), comparisonOperator(int)}`, `ConcatenationContext.additive(int)`, `AdditiveContext.{multiplicative(int), additiveOperator(int)}`, `MultiplicativeContext.{unary(int), multiplicativeOperator(int)}`, `UnaryContext.{sign, unary(), primary()}`, `PrimaryContext.{NUMBER(), STRING(), booleanLiteral(), functionCall(), variable(), comparison()}`, `FunctionCallContext.{IDENTIFIER(), comparison()}`.

The repo is not yet a git repository — the last step initializes git so later tasks can commit.

- [ ] **Step 1: Wire ANTLR into `build.gradle.kts`**

Replace the whole file with:

```kotlin
plugins {
    java
    antlr
}

group = "org.github.eval"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    antlr("org.antlr:antlr4:4.13.2")
    implementation("org.antlr:antlr4-runtime:4.13.2")

    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.generateGrammarSource {
    arguments.add("-visitor")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
    }
}
```

- [ ] **Step 2: Write the grammar**

Create `src/main/antlr/org/github/eval/parser/Expr.g4`:

```antlr
grammar Expr;

expression         : comparison EOF ;
comparison         : concatenation (comparisonOperator concatenation)* ;
comparisonOperator : '=' | '<>' | '<=' | '>=' | '<' | '>' ;
concatenation      : additive ('&' additive)* ;
additive           : multiplicative (additiveOperator multiplicative)* ;
additiveOperator   : '+' | '-' ;
multiplicative     : unary (multiplicativeOperator unary)* ;
multiplicativeOperator : '*' | '/' ;
unary              : sign=('+'|'-') unary | primary ;
primary            : NUMBER
                   | STRING
                   | booleanLiteral
                   | functionCall
                   | variable
                   | '(' comparison ')'
                   ;
booleanLiteral     : TRUE | FALSE ;
functionCall       : IDENTIFIER '(' (comparison (',' comparison)*)? ')' ;
variable           : IDENTIFIER ;

TRUE       : [Tt][Rr][Uu][Ee] ;
FALSE      : [Ff][Aa][Ll][Ss][Ee] ;
NUMBER     : [0-9]+ ('.' [0-9]*)? ([Ee] [+-]? [0-9]+)?
           | '.' [0-9]+ ([Ee] [+-]? [0-9]+)? ;
STRING     : '"' ('""' | ~["])* '"' ;
IDENTIFIER : [A-Za-z_][A-Za-z0-9_.]* ;
WS         : [ \t\r\n]+ -> skip ;
```

- [ ] **Step 3: Verify the parser generates**

Run: `./gradlew generateGrammarSource`
Expected: BUILD SUCCESSFUL; generated files exist under `build/generated-src/antlr/main/org/github/eval/parser/` (`ExprLexer.java`, `ExprParser.java`, `ExprBaseVisitor.java`).

- [ ] **Step 4: Add `ParseException` and `ThrowingErrorListener`**

`src/main/java/org/github/eval/ParseException.java`:

```java
package org.github.eval;

/** Thrown when an expression string cannot be parsed. Carries the ANTLR line:column. */
public class ParseException extends RuntimeException {

  public ParseException(String message) {
    super(message);
  }
}
```

`src/main/java/org/github/eval/parser/ThrowingErrorListener.java`:

```java
package org.github.eval.parser;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.github.eval.ParseException;

/** Converts ANTLR syntax errors into {@link ParseException} instead of printing to stderr. */
public class ThrowingErrorListener extends BaseErrorListener {

  public static final ThrowingErrorListener INSTANCE = new ThrowingErrorListener();

  private ThrowingErrorListener() {}

  @Override
  public void syntaxError(
      Recognizer<?, ?> recognizer,
      Object offendingSymbol,
      int line,
      int charPositionInLine,
      String message,
      RecognitionException e) {
    throw new ParseException("line " + line + ":" + charPositionInLine + " " + message);
  }
}
```

- [ ] **Step 5: Write the parsing test**

`src/test/java/org/github/eval/parser/ExpressionParsingTest.java`:

```java
package org.github.eval.parser;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.github.eval.ParseException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ExpressionParsingTest {

  private ExprParser.ExpressionContext parse(String expression) {
    ExprLexer lexer = new ExprLexer(CharStreams.fromString(expression));
    lexer.removeErrorListeners();
    lexer.addErrorListener(ThrowingErrorListener.INSTANCE);
    ExprParser parser = new ExprParser(new CommonTokenStream(lexer));
    parser.removeErrorListeners();
    parser.addErrorListener(ThrowingErrorListener.INSTANCE);
    return parser.expression();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "1 + 2 * (3 - 4)",
        "-1.5E3 & \"a\"",
        "IF(A1 > 0, ROUND(B1, 2), 0)",
        "AND(a1 = B_2, TRUE)",
        ".5 / 2 <> 3"
      })
  void parsesValidExpressions(String expression) {
    assertDoesNotThrow(() -> parse(expression));
  }

  @Test
  void throwsParseExceptionWithPositionOnSyntaxError() {
    ParseException exception = assertThrows(ParseException.class, () -> parse("1 +"));
    assertTrue(exception.getMessage().startsWith("line 1:"), exception.getMessage());
  }

  @Test
  void rejectsTrailingGarbage() {
    assertThrows(ParseException.class, () -> parse("1 2"));
  }
}
```

- [ ] **Step 6: Run the test**

Run: `./gradlew test --tests "org.github.eval.parser.ExpressionParsingTest"`
Expected: PASS (4+ tests). `1 2` fails because of the `EOF` in the entry rule.

- [ ] **Step 7: Init git and commit**

```bash
git init
git add build.gradle.kts src/ .gitignore gradlew gradlew.bat gradle/ settings.gradle.kts CLAUDE.md docs/
git commit -m "feat: ANTLR4 grammar, build wiring, and parse-error plumbing"
```

---

### Task 2: `EvaluationValue` tagged union with Excel-style coercions

**Files:**
- Create: `src/main/java/org/github/eval/EvaluationException.java`
- Create: `src/main/java/org/github/eval/data/EvaluationValue.java`
- Test: `src/test/java/org/github/eval/data/EvaluationValueTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces: `EvaluationException(String)`; `EvaluationValue` with `static of(BigDecimal)`, `of(String)`, `of(boolean)`, `fromObject(Object)`; `getDataType()` → `DataType{NUMBER,STRING,BOOLEAN}`; `getNumberValue()` → `BigDecimal`; `getStringValue()` → `String`; `getBooleanValue()` → `boolean`. `equals` compares numbers with `compareTo` (so `of(new BigDecimal("3.00"))` equals `of(new BigDecimal("3"))`).

- [ ] **Step 1: Write the failing test**

`src/test/java/org/github/eval/data/EvaluationValueTest.java`:

```java
package org.github.eval.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.github.eval.EvaluationException;
import org.junit.jupiter.api.Test;

class EvaluationValueTest {

  @Test
  void numberEqualityIsScaleInsensitive() {
    assertEquals(EvaluationValue.of(new BigDecimal("3.00")), EvaluationValue.of(new BigDecimal("3")));
  }

  @Test
  void numericStringCoercesToNumber() {
    assertEquals(new BigDecimal("12.5"), EvaluationValue.of(" 12.5 ").getNumberValue());
  }

  @Test
  void nonNumericStringThrowsOnNumberAccess() {
    assertThrows(EvaluationException.class, () -> EvaluationValue.of("abc").getNumberValue());
  }

  @Test
  void booleanCoercesToOneOrZero() {
    assertEquals(BigDecimal.ONE, EvaluationValue.of(true).getNumberValue());
    assertEquals(BigDecimal.ZERO, EvaluationValue.of(false).getNumberValue());
  }

  @Test
  void numberToStringStripsTrailingZeros() {
    assertEquals("1.50", EvaluationValue.of("1.50").getStringValue());
    assertEquals("1.5", EvaluationValue.of(new BigDecimal("1.50")).getStringValue());
    assertEquals("0", EvaluationValue.of(new BigDecimal("0.00")).getStringValue());
  }

  @Test
  void booleanToStringIsExcelStyle() {
    assertEquals("TRUE", EvaluationValue.of(true).getStringValue());
    assertEquals("FALSE", EvaluationValue.of(false).getStringValue());
  }

  @Test
  void booleanCoercion() {
    assertTrue(EvaluationValue.of(new BigDecimal("2")).getBooleanValue());
    assertTrue(EvaluationValue.of("true").getBooleanValue());
    assertThrows(EvaluationException.class, () -> EvaluationValue.of("yes").getBooleanValue());
  }

  @Test
  void fromObjectMapsJavaTypes() {
    assertEquals(EvaluationValue.of(new BigDecimal("0.1")), EvaluationValue.fromObject(0.1d));
    assertEquals(EvaluationValue.of("x"), EvaluationValue.fromObject("x"));
    assertEquals(EvaluationValue.of(true), EvaluationValue.fromObject(Boolean.TRUE));
    assertThrows(EvaluationException.class, () -> EvaluationValue.fromObject(null));
  }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests "org.github.eval.data.EvaluationValueTest"`
Expected: compilation failure — `EvaluationValue` and `EvaluationException` do not exist.

- [ ] **Step 3: Implement `EvaluationException` and `EvaluationValue`**

`src/main/java/org/github/eval/EvaluationException.java`:

```java
package org.github.eval;

/** Thrown when evaluation fails: unknown variable/function, type mismatch, division by zero, … */
public class EvaluationException extends RuntimeException {

  public EvaluationException(String message) {
    super(message);
  }
}
```

`src/main/java/org/github/eval/data/EvaluationValue.java`:

```java
package org.github.eval.data;

import java.math.BigDecimal;
import java.util.Objects;
import org.github.eval.EvaluationException;

/** Immutable tagged union of the runtime value types (NUMBER, STRING, BOOLEAN). */
public final class EvaluationValue {

  public enum DataType {
    NUMBER,
    STRING,
    BOOLEAN
  }

  private final DataType dataType;
  private final Object value;

  private EvaluationValue(DataType dataType, Object value) {
    this.dataType = dataType;
    this.value = value;
  }

  public static EvaluationValue of(BigDecimal value) {
    return new EvaluationValue(DataType.NUMBER, value);
  }

  public static EvaluationValue of(String value) {
    return new EvaluationValue(DataType.STRING, value);
  }

  public static EvaluationValue of(boolean value) {
    return new EvaluationValue(DataType.BOOLEAN, value);
  }

  /** Converts a Java value supplied as a variable binding. */
  public static EvaluationValue fromObject(Object value) {
    if (value instanceof EvaluationValue evaluationValue) {
      return evaluationValue;
    }
    if (value instanceof BigDecimal bigDecimal) {
      return of(bigDecimal);
    }
    if (value instanceof Number number) {
      // via toString to avoid importing binary floating-point error from double/float
      return of(new BigDecimal(number.toString()));
    }
    if (value instanceof Boolean bool) {
      return of(bool);
    }
    if (value instanceof String string) {
      return of(string);
    }
    throw new EvaluationException(
        "Unsupported value type: " + (value == null ? "null" : value.getClass().getName()));
  }

  public DataType getDataType() {
    return dataType;
  }

  public BigDecimal getNumberValue() {
    return switch (dataType) {
      case NUMBER -> (BigDecimal) value;
      case BOOLEAN -> (Boolean) value ? BigDecimal.ONE : BigDecimal.ZERO;
      case STRING -> {
        try {
          yield new BigDecimal(((String) value).trim());
        } catch (NumberFormatException e) {
          throw new EvaluationException("Cannot convert string '" + value + "' to a number");
        }
      }
    };
  }

  public String getStringValue() {
    return switch (dataType) {
      case STRING -> (String) value;
      case BOOLEAN -> (Boolean) value ? "TRUE" : "FALSE";
      case NUMBER -> {
        BigDecimal number = ((BigDecimal) value).stripTrailingZeros();
        yield number.compareTo(BigDecimal.ZERO) == 0 ? "0" : number.toPlainString();
      }
    };
  }

  public boolean getBooleanValue() {
    return switch (dataType) {
      case BOOLEAN -> (Boolean) value;
      case NUMBER -> ((BigDecimal) value).compareTo(BigDecimal.ZERO) != 0;
      case STRING -> {
        String string = ((String) value).trim();
        if (string.equalsIgnoreCase("true")) {
          yield true;
        }
        if (string.equalsIgnoreCase("false")) {
          yield false;
        }
        throw new EvaluationException("Cannot convert string '" + value + "' to a boolean");
      }
    };
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof EvaluationValue other) || dataType != other.dataType) {
      return false;
    }
    if (value instanceof BigDecimal a && other.value instanceof BigDecimal b) {
      return a.compareTo(b) == 0;
    }
    return value.equals(other.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(dataType, value instanceof BigDecimal n ? n.stripTrailingZeros() : value);
  }

  @Override
  public String toString() {
    return dataType + "(" + value + ")";
  }
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew test --tests "org.github.eval.data.EvaluationValueTest"`
Expected: PASS (8 tests).

- [ ] **Step 5: Commit**

```bash
git add src/
git commit -m "feat: EvaluationValue tagged union with Excel-style coercions"
```

---

### Task 3: `ExpressionConfiguration` and operator semantics

**Files:**
- Create: `src/main/java/org/github/eval/ExpressionConfiguration.java`
- Create: `src/main/java/org/github/eval/operators/ArithmeticOperators.java`
- Create: `src/main/java/org/github/eval/operators/ComparisonOperators.java`
- Create: `src/main/java/org/github/eval/operators/ConcatenationOperator.java`
- Test: `src/test/java/org/github/eval/operators/OperatorsTest.java`

**Interfaces:**
- Consumes: `EvaluationValue` (Task 2), `EvaluationException` (Task 2).
- Produces:
  - `ExpressionConfiguration.defaultConfiguration()`, `ExpressionConfiguration.of(MathContext)`, `getMathContext()`; `DEFAULT_MATH_CONTEXT = MathContext.DECIMAL128`.
  - `ArithmeticOperators.add/subtract/multiply(EvaluationValue, EvaluationValue, MathContext)` → `EvaluationValue`; `divide(...)` (throws `EvaluationException` on zero divisor); `negate(EvaluationValue)`, `unaryPlus(EvaluationValue)`.
  - `ComparisonOperators.apply(String operator, EvaluationValue left, EvaluationValue right)` → `EvaluationValue` (operator one of `= <> < > <= >=`).
  - `ConcatenationOperator.concat(EvaluationValue, EvaluationValue)` → `EvaluationValue`.

- [ ] **Step 1: Write the failing test**

`src/test/java/org/github/eval/operators/OperatorsTest.java`:

```java
package org.github.eval.operators;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.math.MathContext;
import org.github.eval.EvaluationException;
import org.github.eval.data.EvaluationValue;
import org.junit.jupiter.api.Test;

class OperatorsTest {

  private static final MathContext MC = MathContext.DECIMAL128;

  private static EvaluationValue num(String value) {
    return EvaluationValue.of(new BigDecimal(value));
  }

  @Test
  void decimalAdditionIsExact() {
    assertEquals(num("0.3"), ArithmeticOperators.add(num("0.1"), num("0.2"), MC));
  }

  @Test
  void divisionCarriesDecimal128Precision() {
    EvaluationValue result = ArithmeticOperators.divide(num("1"), num("3"), MC);
    assertEquals(new BigDecimal("0.3333333333333333333333333333333333"), result.getNumberValue());
  }

  @Test
  void divisionByZeroThrows() {
    assertThrows(EvaluationException.class, () -> ArithmeticOperators.divide(num("1"), num("0"), MC));
  }

  @Test
  void arithmeticCoercesNumericStringsAndBooleans() {
    assertEquals(num("3"), ArithmeticOperators.add(EvaluationValue.of("1"), num("2"), MC));
    assertEquals(num("2"), ArithmeticOperators.add(EvaluationValue.of(true), num("1"), MC));
  }

  @Test
  void negateAndUnaryPlus() {
    assertEquals(num("-2"), ArithmeticOperators.negate(num("2")));
    assertEquals(num("2"), ArithmeticOperators.unaryPlus(EvaluationValue.of("2")));
  }

  @Test
  void numericComparisonIsScaleInsensitive() {
    assertEquals(EvaluationValue.of(true), ComparisonOperators.apply("=", num("2.0"), num("2")));
    assertEquals(EvaluationValue.of(true), ComparisonOperators.apply("<>", num("2"), num("3")));
    assertEquals(EvaluationValue.of(true), ComparisonOperators.apply(">=", num("3"), num("3")));
    assertEquals(EvaluationValue.of(false), ComparisonOperators.apply("<", num("3"), num("2")));
  }

  @Test
  void stringComparisonIsCaseInsensitive() {
    assertEquals(
        EvaluationValue.of(true),
        ComparisonOperators.apply("=", EvaluationValue.of("abc"), EvaluationValue.of("ABC")));
    assertEquals(
        EvaluationValue.of(false),
        ComparisonOperators.apply("<>", EvaluationValue.of("abc"), EvaluationValue.of("ABC")));
  }

  @Test
  void mixedNumberAndNumericStringCompareNumerically() {
    assertEquals(
        EvaluationValue.of(true), ComparisonOperators.apply("=", num("10"), EvaluationValue.of("10")));
  }

  @Test
  void booleanComparison() {
    assertEquals(
        EvaluationValue.of(true),
        ComparisonOperators.apply(">", EvaluationValue.of(true), EvaluationValue.of(false)));
  }

  @Test
  void concatenationStringifiesOperands() {
    assertEquals(EvaluationValue.of("12"), ConcatenationOperator.concat(num("1"), num("2")));
    assertEquals(
        EvaluationValue.of("aTRUE"),
        ConcatenationOperator.concat(EvaluationValue.of("a"), EvaluationValue.of(true)));
  }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests "org.github.eval.operators.OperatorsTest"`
Expected: compilation failure — the three operator classes and `ExpressionConfiguration` do not exist.

- [ ] **Step 3: Implement the four classes**

`src/main/java/org/github/eval/ExpressionConfiguration.java`:

```java
package org.github.eval;

import java.math.MathContext;

/** Evaluation-wide settings. Currently just the math context used for division and arithmetic. */
public class ExpressionConfiguration {

  public static final MathContext DEFAULT_MATH_CONTEXT = MathContext.DECIMAL128;

  private final MathContext mathContext;

  private ExpressionConfiguration(MathContext mathContext) {
    this.mathContext = mathContext;
  }

  public static ExpressionConfiguration defaultConfiguration() {
    return new ExpressionConfiguration(DEFAULT_MATH_CONTEXT);
  }

  public static ExpressionConfiguration of(MathContext mathContext) {
    return new ExpressionConfiguration(mathContext);
  }

  public MathContext getMathContext() {
    return mathContext;
  }
}
```

`src/main/java/org/github/eval/operators/ArithmeticOperators.java`:

```java
package org.github.eval.operators;

import java.math.MathContext;
import org.github.eval.EvaluationException;
import org.github.eval.data.EvaluationValue;

/** BigDecimal arithmetic with Excel-style operand coercion. */
public final class ArithmeticOperators {

  private ArithmeticOperators() {}

  public static EvaluationValue add(EvaluationValue left, EvaluationValue right, MathContext mc) {
    return EvaluationValue.of(left.getNumberValue().add(right.getNumberValue(), mc));
  }

  public static EvaluationValue subtract(
      EvaluationValue left, EvaluationValue right, MathContext mc) {
    return EvaluationValue.of(left.getNumberValue().subtract(right.getNumberValue(), mc));
  }

  public static EvaluationValue multiply(
      EvaluationValue left, EvaluationValue right, MathContext mc) {
    return EvaluationValue.of(left.getNumberValue().multiply(right.getNumberValue(), mc));
  }

  public static EvaluationValue divide(EvaluationValue left, EvaluationValue right, MathContext mc) {
    try {
      return EvaluationValue.of(left.getNumberValue().divide(right.getNumberValue(), mc));
    } catch (ArithmeticException e) {
      throw new EvaluationException("Division by zero");
    }
  }

  public static EvaluationValue negate(EvaluationValue value) {
    return EvaluationValue.of(value.getNumberValue().negate());
  }

  /** Unary plus still coerces its operand to a number. */
  public static EvaluationValue unaryPlus(EvaluationValue value) {
    return EvaluationValue.of(value.getNumberValue());
  }
}
```

`src/main/java/org/github/eval/operators/ComparisonOperators.java`:

```java
package org.github.eval.operators;

import java.math.BigDecimal;
import org.github.eval.EvaluationException;
import org.github.eval.data.EvaluationValue;
import org.github.eval.data.EvaluationValue.DataType;

/**
 * Excel-style comparison: numeric when both sides are numbers or numeric strings,
 * boolean when both sides are booleans, otherwise case-insensitive text.
 */
public final class ComparisonOperators {

  private ComparisonOperators() {}

  public static EvaluationValue apply(String operator, EvaluationValue left, EvaluationValue right) {
    int comparison = compare(left, right);
    boolean result =
        switch (operator) {
          case "=" -> comparison == 0;
          case "<>" -> comparison != 0;
          case "<" -> comparison < 0;
          case ">" -> comparison > 0;
          case "<=" -> comparison <= 0;
          case ">=" -> comparison >= 0;
          default -> throw new EvaluationException("Unknown comparison operator: " + operator);
        };
    return EvaluationValue.of(result);
  }

  private static int compare(EvaluationValue left, EvaluationValue right) {
    if (isNumeric(left) && isNumeric(right)) {
      return left.getNumberValue().compareTo(right.getNumberValue());
    }
    if (left.getDataType() == DataType.BOOLEAN && right.getDataType() == DataType.BOOLEAN) {
      return Boolean.compare(left.getBooleanValue(), right.getBooleanValue());
    }
    return left.getStringValue().compareToIgnoreCase(right.getStringValue());
  }

  private static boolean isNumeric(EvaluationValue value) {
    if (value.getDataType() == DataType.NUMBER) {
      return true;
    }
    if (value.getDataType() == DataType.STRING) {
      try {
        new BigDecimal(value.getStringValue().trim());
        return true;
      } catch (NumberFormatException e) {
        return false;
      }
    }
    return false;
  }
}
```

`src/main/java/org/github/eval/operators/ConcatenationOperator.java`:

```java
package org.github.eval.operators;

import org.github.eval.data.EvaluationValue;

/** Excel's {@code &} text concatenation: both operands are stringified first. */
public final class ConcatenationOperator {

  private ConcatenationOperator() {}

  public static EvaluationValue concat(EvaluationValue left, EvaluationValue right) {
    return EvaluationValue.of(left.getStringValue() + right.getStringValue());
  }
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew test --tests "org.github.eval.operators.OperatorsTest"`
Expected: PASS (10 tests). Key check: `divisionCarriesDecimal128Precision` yields 34 threes.

- [ ] **Step 5: Commit**

```bash
git add src/
git commit -m "feat: arithmetic, comparison, and concatenation operator semantics"
```

---

### Task 4: Evaluation visitor + `Expression` facade (literals and operators)

**Files:**
- Create: `src/main/java/org/github/eval/parser/EvaluationContext.java`
- Create: `src/main/java/org/github/eval/parser/EvaluationVisitor.java`
- Create: `src/main/java/org/github/eval/Expression.java`
- Test: `src/test/java/org/github/eval/ExpressionTest.java`

**Interfaces:**
- Consumes: generated parser (Task 1), `EvaluationValue` (2), operators + `ExpressionConfiguration` (3).
- Produces:
  - `EvaluationContext(ExpressionConfiguration, Map<String,EvaluationValue>, EvaluationVisitor)` with `evaluate(ExprParser.ComparisonContext)` → `EvaluationValue`, `getVariable(String)` → `EvaluationValue` (throws `EvaluationException` when unknown), `getMathContext()` → `MathContext`.
  - `EvaluationVisitor(ExpressionConfiguration, Map<String,EvaluationValue>) extends ExprBaseVisitor<EvaluationValue>`.
  - `Expression(String)`, `Expression(String, ExpressionConfiguration)`; `with(String, Object)` → `Expression`; `evaluate()` and `evaluate(Map<String, ?>)` → `EvaluationValue`.
  - Note: `EvaluationContext` will gain `getFunctionRegistry()` in Task 6; the visitor will gain `visitFunctionCall` and variable resolution lands in Task 5. In this task `visitPrimary` resolves variables already (the `context.getVariable` path) — only the facade's variable-binding API arrives in Task 5.

- [ ] **Step 1: Write the failing test**

`src/test/java/org/github/eval/ExpressionTest.java`:

```java
package org.github.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import org.github.eval.data.EvaluationValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ExpressionTest {

  private static EvaluationValue evaluate(String expression) {
    return new Expression(expression).evaluate();
  }

  private static EvaluationValue num(String value) {
    return EvaluationValue.of(new BigDecimal(value));
  }

  @ParameterizedTest
  @CsvSource({
    "1+2, 3",
    "2*3+4, 10",
    "2*(3+4), 14",
    "10-2-3, 5",
    "2*3/4, 1.5",
    "-2+5, 3",
    "+2*3, 6",
    "--2, 2",
    "0.1+0.2, 0.3",
    "(1+2)*3, 9"
  })
  void arithmeticRespectsPrecedenceAndPrecision(String expression, String expected) {
    assertEquals(num(expected), evaluate(expression));
  }

  @Test
  void divisionUsesDecimal128() {
    assertEquals(
        num("0.3333333333333333333333333333333333"), evaluate("1/3"));
  }

  @Test
  void divisionByZeroThrowsEvaluationException() {
    assertThrows(EvaluationException.class, () -> evaluate("1/0"));
  }

  @ParameterizedTest
  @CsvSource({
    "1=1, true",
    "1=2, false",
    "2>1, true",
    "2>=2, true",
    "1<2, true",
    "2<=1, false",
    "1<>2, true",
    "\"abc\"=\"ABC\", true",
    "\"b\">\"a\", true"
  })
  void comparisons(String expression, boolean expected) {
    assertEquals(EvaluationValue.of(expected), evaluate(expression));
  }

  @Test
  void concatenation() {
    assertEquals(EvaluationValue.of("Result: 3"), evaluate("\"Result: \" & 1+2"));
    assertEquals(EvaluationValue.of("12"), evaluate("1 & 2"));
  }

  @Test
  void stringEscapingAndBooleans() {
    assertEquals(EvaluationValue.of("say \"hi\""), evaluate("\"say \"\"hi\"\"\""));
    assertEquals(EvaluationValue.of(true), evaluate("TRUE"));
    assertEquals(EvaluationValue.of(false), evaluate("false"));
  }

  @Test
  void comparisonBindsLooserThanConcatenation() {
    // "a" & "b" = "ab"  →  TRUE
    assertEquals(EvaluationValue.of(true), evaluate("\"a\" & \"b\" = \"ab\""));
  }

  @Test
  void syntaxErrorThrowsParseException() {
    assertThrows(ParseException.class, () -> new Expression("1 +"));
  }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests "org.github.eval.ExpressionTest"`
Expected: compilation failure — `Expression` does not exist.

- [ ] **Step 3: Implement context, visitor, facade**

`src/main/java/org/github/eval/parser/EvaluationContext.java`:

```java
package org.github.eval.parser;

import java.math.MathContext;
import java.util.Map;
import org.github.eval.EvaluationException;
import org.github.eval.ExpressionConfiguration;
import org.github.eval.data.EvaluationValue;

/** Per-evaluation state: variables, configuration, and a bridge back to the visitor. */
public class EvaluationContext {

  private final ExpressionConfiguration configuration;
  private final Map<String, EvaluationValue> variables;
  private final EvaluationVisitor visitor;

  public EvaluationContext(
      ExpressionConfiguration configuration,
      Map<String, EvaluationValue> variables,
      EvaluationVisitor visitor) {
    this.configuration = configuration;
    this.variables = variables;
    this.visitor = visitor;
  }

  /** Evaluates a sub-expression — used by functions, lazily in the case of IF. */
  public EvaluationValue evaluate(ExprParser.ComparisonContext context) {
    return visitor.visitComparison(context);
  }

  public EvaluationValue getVariable(String name) {
    EvaluationValue value = variables.get(name);
    if (value == null) {
      throw new EvaluationException("Unknown variable: " + name);
    }
    return value;
  }

  public MathContext getMathContext() {
    return configuration.getMathContext();
  }
}
```

`src/main/java/org/github/eval/parser/EvaluationVisitor.java`:

```java
package org.github.eval.parser;

import java.math.BigDecimal;
import java.util.Map;
import org.github.eval.ExpressionConfiguration;
import org.github.eval.data.EvaluationValue;
import org.github.eval.operators.ArithmeticOperators;
import org.github.eval.operators.ComparisonOperators;
import org.github.eval.operators.ConcatenationOperator;

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
}
```

`src/main/java/org/github/eval/Expression.java`:

```java
package org.github.eval;

import java.util.Map;
import java.util.TreeMap;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.github.eval.data.EvaluationValue;
import org.github.eval.parser.EvaluationVisitor;
import org.github.eval.parser.ExprLexer;
import org.github.eval.parser.ExprParser;
import org.github.eval.parser.ThrowingErrorListener;

/** Public facade: parse once, then evaluate (optionally many times with different variables). */
public class Expression {

  private final ExprParser.ExpressionContext parseTree;
  private final ExpressionConfiguration configuration;
  private final Map<String, EvaluationValue> variables =
      new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

  public Expression(String expressionString) {
    this(expressionString, ExpressionConfiguration.defaultConfiguration());
  }

  public Expression(String expressionString, ExpressionConfiguration configuration) {
    this.configuration = configuration;
    ExprLexer lexer = new ExprLexer(CharStreams.fromString(expressionString));
    lexer.removeErrorListeners();
    lexer.addErrorListener(ThrowingErrorListener.INSTANCE);
    ExprParser parser = new ExprParser(new CommonTokenStream(lexer));
    parser.removeErrorListeners();
    parser.addErrorListener(ThrowingErrorListener.INSTANCE);
    this.parseTree = parser.expression();
  }

  /** Binds a variable (fluent). Lookup is case-insensitive. */
  public Expression with(String variableName, Object value) {
    variables.put(variableName, EvaluationValue.fromObject(value));
    return this;
  }

  public EvaluationValue evaluate() {
    return evaluate(Map.of());
  }

  public EvaluationValue evaluate(Map<String, ?> variables) {
    Map<String, EvaluationValue> allVariables = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    allVariables.putAll(this.variables);
    variables.forEach((name, value) -> allVariables.put(name, EvaluationValue.fromObject(value)));
    EvaluationVisitor visitor = new EvaluationVisitor(configuration, allVariables);
    return visitor.visit(parseTree);
  }
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew test --tests "org.github.eval.ExpressionTest"`
Expected: PASS. (Unknown-variable behavior is not exercised yet — no test binds variables in this task.)

- [ ] **Step 5: Commit**

```bash
git add src/
git commit -m "feat: evaluation visitor and Expression facade for literals and operators"
```

---

### Task 5: Variables

**Files:**
- Modify: `src/test/java/org/github/eval/ExpressionTest.java` (append tests; no main-code changes needed — resolution and binding API landed in Task 4)
- Test: `src/test/java/org/github/eval/VariablesTest.java`

**Interfaces:**
- Consumes: `Expression.with(String, Object)`, `Expression.evaluate(Map<String, ?>)`, `EvaluationContext.getVariable(String)` (all Task 4).
- Produces: nothing new — this task pins the behavior with tests.

- [ ] **Step 1: Write the failing test**

`src/test/java/org/github/eval/VariablesTest.java`:

```java
package org.github.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.Map;
import org.github.eval.data.EvaluationValue;
import org.junit.jupiter.api.Test;

class VariablesTest {

  @Test
  void withBindsVariables() {
    EvaluationValue result =
        new Expression("A1 * 2 + B1").with("A1", 3).with("B1", new BigDecimal("0.5")).evaluate();
    assertEquals(EvaluationValue.of(new BigDecimal("6.5")), result);
  }

  @Test
  void evaluateMapBindsVariables() {
    EvaluationValue result = new Expression("x + y").evaluate(Map.of("x", 1, "y", "2.5"));
    assertEquals(EvaluationValue.of(new BigDecimal("3.5")), result);
  }

  @Test
  void variableNamesAreCaseInsensitive() {
    EvaluationValue result = new Expression("total * 2").with("TOTAL", 21).evaluate();
    assertEquals(EvaluationValue.of(new BigDecimal("42")), result);
  }

  @Test
  void evaluateMapOverridesWithBindings() {
    EvaluationValue result =
        new Expression("x").with("x", 1).evaluate(Map.of("x", 99));
    assertEquals(EvaluationValue.of(new BigDecimal("99")), result);
  }

  @Test
  void unknownVariableThrows() {
    EvaluationException exception =
        assertThrows(EvaluationException.class, () -> new Expression("nope + 1").evaluate());
    assertEquals("Unknown variable: nope", exception.getMessage());
  }

  @Test
  void parseOnceEvaluateManyTimesWithDifferentValues() {
    Expression expression = new Expression("price * qty");
    assertEquals(
        EvaluationValue.of(new BigDecimal("10")),
        expression.evaluate(Map.of("price", "2.5", "qty", 4)));
    assertEquals(
        EvaluationValue.of(new BigDecimal("6")),
        expression.evaluate(Map.of("price", 3, "qty", 2)));
  }
}
```

- [ ] **Step 2: Run to verify it passes (or fix)**

Run: `./gradlew test --tests "org.github.eval.VariablesTest"`
Expected: PASS — Task 4 already implemented resolution and binding. If `evaluateMapOverridesWithBindings` fails, check the overlay order in `Expression.evaluate` (the `evaluate(Map)` argument must win over `with(...)` bindings).

- [ ] **Step 3: Commit**

```bash
git add src/
git commit -m "test: variable binding, case-insensitivity, and re-evaluation"
```

---

### Task 6: Function framework + `AND` / `OR`

**Files:**
- Create: `src/main/java/org/github/eval/functions/FunctionIfc.java`
- Create: `src/main/java/org/github/eval/functions/AbstractFunction.java`
- Create: `src/main/java/org/github/eval/functions/AndFunction.java`
- Create: `src/main/java/org/github/eval/functions/OrFunction.java`
- Create: `src/main/java/org/github/eval/functions/FunctionRegistry.java`
- Modify: `src/main/java/org/github/eval/parser/EvaluationContext.java` (add `getFunctionRegistry()`)
- Modify: `src/main/java/org/github/eval/parser/EvaluationVisitor.java` (add `visitFunctionCall`)
- Test: `src/test/java/org/github/eval/functions/AndOrTest.java`

**Interfaces:**
- Consumes: `EvaluationContext.evaluate(ComparisonContext)`, `EvaluationValue.getBooleanValue()`.
- Produces:
  - `FunctionIfc`: `boolean isLazyArguments()`; `EvaluationValue evaluate(List<ExprParser.ComparisonContext> arguments, EvaluationContext context)`.
  - `AbstractFunction implements FunctionIfc`: evaluates all arguments eagerly then delegates to `protected abstract EvaluationValue evaluateValues(List<EvaluationValue> arguments, EvaluationContext context)`; helpers `requireExactArgumentCount(List<?>, int, String)` and `requireMinArgumentCount(List<?>, int, String)` throwing `EvaluationException`.
  - `FunctionRegistry`: `register(String, FunctionIfc)`, `getFunction(String)` (case-insensitive; throws `EvaluationException` on unknown), `static defaultRegistry()`.
  - `EvaluationContext.getFunctionRegistry()` → `FunctionRegistry`.

- [ ] **Step 1: Write the failing test**

`src/test/java/org/github/eval/functions/AndOrTest.java`:

```java
package org.github.eval.functions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.github.eval.EvaluationException;
import org.github.eval.Expression;
import org.github.eval.data.EvaluationValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AndOrTest {

  private static EvaluationValue evaluate(String expression) {
    return new Expression(expression).evaluate();
  }

  @ParameterizedTest
  @CsvSource({
    "AND(TRUE, TRUE), true",
    "AND(TRUE, FALSE), false",
    "AND(1, 2, 3), true",
    "AND(1, 0), false",
    "AND(\"true\", 1), true",
    "AND(2>1, 3>2), true"
  })
  void and(String expression, boolean expected) {
    assertEquals(EvaluationValue.of(expected), evaluate(expression));
  }

  @ParameterizedTest
  @CsvSource({
    "OR(FALSE, FALSE), false",
    "OR(FALSE, TRUE), true",
    "OR(0, 0, 5), true",
    "OR(1=2, 2=2), true"
  })
  void or(String expression, boolean expected) {
    assertEquals(EvaluationValue.of(expected), evaluate(expression));
  }

  @Test
  void functionNamesAreCaseInsensitive() {
    assertEquals(EvaluationValue.of(true), evaluate("aNd(tRuE, True)"));
  }

  @Test
  void andRejectsNonBooleanOperand() {
    assertThrows(EvaluationException.class, () -> evaluate("AND(\"yes\")"));
  }

  @Test
  void andRequiresAtLeastOneArgument() {
    assertThrows(EvaluationException.class, () -> evaluate("AND()"));
  }

  @Test
  void unknownFunctionThrows() {
    EvaluationException exception =
        assertThrows(EvaluationException.class, () -> evaluate("NOSUCHFN(1)"));
    assertEquals("Unknown function: NOSUCHFN", exception.getMessage());
  }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests "org.github.eval.functions.AndOrTest"`
Expected: compilation failure — function framework does not exist.

- [ ] **Step 3: Implement the framework and `AND`/`OR`**

`src/main/java/org/github/eval/functions/FunctionIfc.java`:

```java
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
```

`src/main/java/org/github/eval/functions/AbstractFunction.java`:

```java
package org.github.eval.functions;

import java.util.ArrayList;
import java.util.List;
import org.github.eval.EvaluationException;
import org.github.eval.data.EvaluationValue;
import org.github.eval.parser.EvaluationContext;
import org.github.eval.parser.ExprParser;

/** Base for eager functions: evaluates every argument, then calls {@link #evaluateValues}. */
public abstract class AbstractFunction implements FunctionIfc {

  @Override
  public boolean isLazyArguments() {
    return false;
  }

  @Override
  public EvaluationValue evaluate(
      List<ExprParser.ComparisonContext> arguments, EvaluationContext context) {
    List<EvaluationValue> values = new ArrayList<>(arguments.size());
    for (ExprParser.ComparisonContext argument : arguments) {
      values.add(context.evaluate(argument));
    }
    return evaluateValues(values, context);
  }

  protected abstract EvaluationValue evaluateValues(
      List<EvaluationValue> arguments, EvaluationContext context);

  protected void requireExactArgumentCount(List<?> arguments, int expected, String name) {
    if (arguments.size() != expected) {
      throw new EvaluationException(
          name + " requires exactly " + expected + " argument(s), got " + arguments.size());
    }
  }

  protected void requireMinArgumentCount(List<?> arguments, int min, String name) {
    if (arguments.size() < min) {
      throw new EvaluationException(
          name + " requires at least " + min + " argument(s), got " + arguments.size());
    }
  }
}
```

`src/main/java/org/github/eval/functions/AndFunction.java`:

```java
package org.github.eval.functions;

import java.util.List;
import org.github.eval.data.EvaluationValue;
import org.github.eval.parser.EvaluationContext;

public class AndFunction extends AbstractFunction {

  @Override
  protected EvaluationValue evaluateValues(
      List<EvaluationValue> arguments, EvaluationContext context) {
    requireMinArgumentCount(arguments, 1, "AND");
    for (EvaluationValue argument : arguments) {
      if (!argument.getBooleanValue()) {
        return EvaluationValue.of(false);
      }
    }
    return EvaluationValue.of(true);
  }
}
```

`src/main/java/org/github/eval/functions/OrFunction.java`:

```java
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
```

`src/main/java/org/github/eval/functions/FunctionRegistry.java`:

```java
package org.github.eval.functions;

import java.util.Map;
import java.util.TreeMap;
import org.github.eval.EvaluationException;

/** Case-insensitive function lookup. */
public class FunctionRegistry {

  private final Map<String, FunctionIfc> functions = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

  public void register(String name, FunctionIfc function) {
    functions.put(name, function);
  }

  public FunctionIfc getFunction(String name) {
    FunctionIfc function = functions.get(name);
    if (function == null) {
      throw new EvaluationException("Unknown function: " + name);
    }
    return function;
  }

  public static FunctionRegistry defaultRegistry() {
    FunctionRegistry registry = new FunctionRegistry();
    registry.register("AND", new AndFunction());
    registry.register("OR", new OrFunction());
    return registry;
  }
}
```

Modify `EvaluationContext` — add the field and getter:

```java
  private final FunctionRegistry functionRegistry = FunctionRegistry.defaultRegistry();

  public FunctionRegistry getFunctionRegistry() {
    return functionRegistry;
  }
```

(add `import org.github.eval.functions.FunctionRegistry;`)

Modify `EvaluationVisitor` — append:

```java
  @Override
  public EvaluationValue visitFunctionCall(ExprParser.FunctionCallContext ctx) {
    FunctionIfc function =
        context.getFunctionRegistry().getFunction(ctx.IDENTIFIER().getText());
    return function.evaluate(ctx.comparison(), context);
  }
```

(add `import org.github.eval.functions.FunctionIfc;`)

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew test --tests "org.github.eval.functions.AndOrTest"`
Expected: PASS (12 tests).

- [ ] **Step 5: Commit**

```bash
git add src/
git commit -m "feat: function framework with AND and OR"
```

---

### Task 7: `ROUND`, `MAX`, `MIN`

**Files:**
- Create: `src/main/java/org/github/eval/functions/RoundFunction.java`
- Create: `src/main/java/org/github/eval/functions/MaxFunction.java`
- Create: `src/main/java/org/github/eval/functions/MinFunction.java`
- Modify: `src/main/java/org/github/eval/functions/FunctionRegistry.java` (register the three)
- Test: `src/test/java/org/github/eval/functions/RoundMaxMinTest.java`

**Interfaces:**
- Consumes: `AbstractFunction`, `requireExactArgumentCount`, `requireMinArgumentCount` (Task 6).
- Produces: nothing new beyond the three function classes registered as `ROUND`, `MAX`, `MIN`.

- [ ] **Step 1: Write the failing test**

`src/test/java/org/github/eval/functions/RoundMaxMinTest.java`:

```java
package org.github.eval.functions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import org.github.eval.EvaluationException;
import org.github.eval.Expression;
import org.github.eval.data.EvaluationValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RoundMaxMinTest {

  private static EvaluationValue evaluate(String expression) {
    return new Expression(expression).evaluate();
  }

  private static EvaluationValue num(String value) {
    return EvaluationValue.of(new BigDecimal(value));
  }

  @ParameterizedTest
  @CsvSource({
    "ROUND(2.5, 0), 3",
    "ROUND(-2.5, 0), -3",
    "ROUND(1.005, 2), 1.01",
    "ROUND(2.4, 0), 2",
    "ROUND(123.456, -1), 120",
    "ROUND(1.2345, 3), 1.235"
  })
  void roundUsesHalfAwayFromZero(String expression, String expected) {
    assertEquals(num(expected), evaluate(expression));
  }

  @Test
  void roundRequiresExactlyTwoArguments() {
    assertThrows(EvaluationException.class, () -> evaluate("ROUND(1.5)"));
  }

  @ParameterizedTest
  @CsvSource({
    "MAX(1, 2, 3), 3",
    "MAX(-1, -5), -1",
    "MAX(2.50, 2.5, 1), 2.50",
    "MIN(1, 2, 3), 1",
    "MIN(-1, -5), -5",
    "MIN(0.1, 0.10), 0.1"
  })
  void maxMin(String expression, String expected) {
    assertEquals(num(expected), evaluate(expression));
  }

  @Test
  void maxRequiresAtLeastOneArgument() {
    assertThrows(EvaluationException.class, () -> evaluate("MAX()"));
  }

  @Test
  void maxCoercesNumericStrings() {
    assertEquals(num("10"), evaluate("MAX(\"10\", 2)"));
  }

  @Test
  void nestedAndCombined() {
    assertEquals(num("2"), evaluate("MIN(MAX(1, 2), ROUND(2.5, 0))"));
  }
}
```

Note on `MAX(2.50, 2.5, 1)` → `2.50`: `EvaluationValue.equals` is scale-insensitive, so `2.50`/`2.5` both pass — the test asserts the maximum value, not its scale.

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests "org.github.eval.functions.RoundMaxMinTest"`
Expected: compilation failure — the three function classes do not exist.

- [ ] **Step 3: Implement**

`src/main/java/org/github/eval/functions/RoundFunction.java`:

```java
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
```

`src/main/java/org/github/eval/functions/MaxFunction.java`:

```java
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
```

`src/main/java/org/github/eval/functions/MinFunction.java`:

```java
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
```

In `FunctionRegistry.defaultRegistry()`, add after the OR registration:

```java
    registry.register("ROUND", new RoundFunction());
    registry.register("MAX", new MaxFunction());
    registry.register("MIN", new MinFunction());
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew test --tests "org.github.eval.functions.RoundMaxMinTest"`
Expected: PASS (11 tests). Watch `ROUND(1.005, 2) = 1.01` — passes because the literal is parsed exactly as `BigDecimal("1.005")`, not a binary double.

- [ ] **Step 5: Commit**

```bash
git add src/
git commit -m "feat: ROUND, MAX, and MIN functions"
```

---

### Task 8: Lazy `IF`

**Files:**
- Create: `src/main/java/org/github/eval/functions/IfFunction.java`
- Modify: `src/main/java/org/github/eval/functions/FunctionRegistry.java` (register `IF`)
- Test: `src/test/java/org/github/eval/functions/IfTest.java`

**Interfaces:**
- Consumes: `FunctionIfc.isLazyArguments()`, `EvaluationContext.evaluate(ComparisonContext)`.
- Produces: `IfFunction implements FunctionIfc` (directly — not via `AbstractFunction`, since it is lazy). Excel semantics: `IF(cond, then)` with a false condition and no else yields boolean FALSE.

- [ ] **Step 1: Write the failing test**

`src/test/java/org/github/eval/functions/IfTest.java`:

```java
package org.github.eval.functions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import org.github.eval.EvaluationException;
import org.github.eval.Expression;
import org.github.eval.data.EvaluationValue;
import org.junit.jupiter.api.Test;

class IfTest {

  private static EvaluationValue evaluate(String expression) {
    return new Expression(expression).evaluate();
  }

  private static EvaluationValue num(String value) {
    return EvaluationValue.of(new BigDecimal(value));
  }

  @Test
  void selectsThenBranchWhenTrue() {
    assertEquals(num("1"), evaluate("IF(TRUE, 1, 2)"));
  }

  @Test
  void selectsElseBranchWhenFalse() {
    assertEquals(num("2"), evaluate("IF(FALSE, 1, 2)"));
  }

  @Test
  void evaluatesConditionExpression() {
    assertEquals(num("10"), evaluate("IF(2 > 1, 10, 20)"));
  }

  @Test
  void untakenBranchIsNotEvaluated() {
    // Would throw division-by-zero if evaluated eagerly.
    assertEquals(num("1"), evaluate("IF(TRUE, 1, 1/0)"));
    assertEquals(num("2"), evaluate("IF(FALSE, 1/0, 2)"));
  }

  @Test
  void missingElseYieldsFalse() {
    assertEquals(EvaluationValue.of(false), evaluate("IF(FALSE, 1)"));
  }

  @Test
  void twoArgumentTrueCaseReturnsBranch() {
    assertEquals(num("7"), evaluate("IF(TRUE, 7)"));
  }

  @Test
  void nestedIf() {
    assertEquals(
        EvaluationValue.of("big"), evaluate("IF(10 > 100, \"huge\", IF(10 > 5, \"big\", \"small\"))"));
  }

  @Test
  void wrongArgumentCountThrows() {
    assertThrows(EvaluationException.class, () -> evaluate("IF(TRUE)"));
    assertThrows(EvaluationException.class, () -> evaluate("IF(TRUE, 1, 2, 3)"));
  }

  @Test
  void ifWithVariables() {
    EvaluationValue result =
        new Expression("IF(score >= 60, \"pass\", \"fail\")").with("score", 75).evaluate();
    assertEquals(EvaluationValue.of("pass"), result);
  }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests "org.github.eval.functions.IfTest"`
Expected: FAIL — `Unknown function: IF`.

- [ ] **Step 3: Implement `IfFunction` and register it**

`src/main/java/org/github/eval/functions/IfFunction.java`:

```java
package org.github.eval.functions;

import java.util.List;
import org.github.eval.EvaluationException;
import org.github.eval.data.EvaluationValue;
import org.github.eval.parser.EvaluationContext;
import org.github.eval.parser.ExprParser;

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
    if (condition) {
      return context.evaluate(arguments.get(1));
    }
    return arguments.size() == 3 ? context.evaluate(arguments.get(2)) : EvaluationValue.of(false);
  }
}
```

In `FunctionRegistry.defaultRegistry()`, add first (Excel order, cosmetic):

```java
    registry.register("IF", new IfFunction());
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew test --tests "org.github.eval.functions.IfTest"`
Expected: PASS (9 tests). The two `untakenBranchIsNotEvaluated` cases are the lazy-evaluation proof.

- [ ] **Step 5: Commit**

```bash
git add src/
git commit -m "feat: lazy IF function"
```

---

### Task 9: End-to-end integration suite + full build

**Files:**
- Test: `src/test/java/org/github/eval/IntegrationTest.java`

**Interfaces:**
- Consumes: everything; no new production code.
- Produces: nothing — final acceptance net.

- [ ] **Step 1: Write the integration test**

`src/test/java/org/github/eval/IntegrationTest.java`:

```java
package org.github.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.math.MathContext;
import org.github.eval.data.EvaluationValue;
import org.junit.jupiter.api.Test;

/** Spec acceptance: combined formulas, precision, configurability. */
class IntegrationTest {

  private static EvaluationValue num(String value) {
    return EvaluationValue.of(new BigDecimal(value));
  }

  @Test
  void specExample() {
    EvaluationValue result =
        new Expression("IF(A1 > 0, ROUND(B1, 2), 0)")
            .with("A1", 5)
            .with("B1", new BigDecimal("1.005"))
            .evaluate();
    assertEquals(num("1.01"), result);
  }

  @Test
  void combinedFormula() {
    // IF(AND(x>0, x<10), MAX(x*2, 5), MIN(x, 0)) with x = 4 → MAX(8, 5) = 8
    EvaluationValue result =
        new Expression("IF(AND(x > 0, x < 10), MAX(x * 2, 5), MIN(x, 0))")
            .with("x", 4)
            .evaluate();
    assertEquals(num("8"), result);
  }

  @Test
  void mixedTypesInOneFormula() {
    EvaluationValue result =
        new Expression("IF(ROUND(amount, 2) = 10.5, \"ok\" & \"!\", \"bad\")")
            .with("amount", new BigDecimal("10.4999"))
            .evaluate();
    assertEquals(EvaluationValue.of("ok!"), result);
  }

  @Test
  void longPrecisionChain() {
    // DECIMAL128: 1/3 = 0.33…3 (34 threes); ×3 = 0.99…9 (34 nines), no error blow-up.
    // Plain double arithmetic gives the coincidental-looking 1.0 here and hides the real value.
    EvaluationValue result = new Expression("(1/3)*3").evaluate();
    assertEquals(num("0.9999999999999999999999999999999999"), result);
  }

  @Test
  void decimalAdditionIsExact() {
    // double gives 0.30000000000000004; BigDecimal gives exactly 0.3.
    assertEquals(num("0.3"), new Expression("0.1 + 0.2").evaluate());
  }

  @Test
  void configurableMathContext() {
    EvaluationValue result =
        new Expression("1/3", ExpressionConfiguration.of(MathContext.DECIMAL32))
            .evaluate();
    assertEquals(num("0.3333333"), result);
  }

  @Test
  void concatenationWithEverything() {
    EvaluationValue result =
        new Expression("\"Total: \" & ROUND(MAX(a, b), 1) & \" (\" & IF(a > b, \"a\", \"b\") & \")\"")
            .with("a", new BigDecimal("2.55"))
            .with("b", new BigDecimal("2.5"))
            .evaluate();
    assertEquals(EvaluationValue.of("Total: 2.6 (a)"), result);
  }
}
```

Note on `longPrecisionChain`: DECIMAL128 `1/3` = `0.333…3` (34 threes); `×3` = `0.999…9` (34 nines) exactly — 34 nines fits the 34-digit precision, so `multiply` does NOT round it to `1`. The test pins this honest value rather than the `1.0` a `double` would coincidentally print.

Note on `configurableMathContext`: DECIMAL32 has 7 significant digits → `0.3333333`.

- [ ] **Step 2: Run the whole suite**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL — all tasks' tests green (`ExpressionParsingTest`, `EvaluationValueTest`, `OperatorsTest`, `ExpressionTest`, `VariablesTest`, `AndOrTest`, `RoundMaxMinTest`, `IfTest`, `IntegrationTest`).

- [ ] **Step 3: Commit**

```bash
git add src/
git commit -m "test: end-to-end integration and precision acceptance suite"
```

---

## Self-Review Notes

- **Spec coverage:** build wiring (§7)→Task 1; grammar (§4)→Task 1; `EvaluationValue`/coercions (§3,§5)→Task 2; operator semantics (§5)→Task 3; facade/context/visitor (§2,§3)→Task 4; variables→Task 5; function framework + AND/OR→Task 6; ROUND/MAX/MIN→Task 7; lazy IF (§2)→Task 8; error handling (§6)→`ParseException`/`EvaluationException` in Tasks 1–2, exercised throughout; precision + configurability (§5)→Tasks 3, 9.
- **Type consistency:** `EvaluationContext.evaluate(ExprParser.ComparisonContext)`, `getVariable(String)`, `getMathContext()`, `getFunctionRegistry()` used identically across Tasks 4–8; `FunctionIfc.evaluate(List<ExprParser.ComparisonContext>, EvaluationContext)` consistent between framework (6) and IF (8); `requireExactArgumentCount`/`requireMinArgumentCount` signatures match their uses.
