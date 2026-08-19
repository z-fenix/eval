# Excel-Style Expression Evaluator (ANTLR4) — Design

**Date:** 2026-08-20
**Project:** `eval` (`org.github.eval`, Gradle 9.7 / Kotlin DSL, JDK 25, JUnit 6)
**Reference:** EvalEx (`D:\workplace\project\EvalEx`) — reuse its value/precision concepts, replace its shunting-yard front-end with an ANTLR4 grammar.

## 1. Goal & Scope

A Java library that parses and evaluates Excel-style expressions.

**In scope (acceptance target):**
- Functions: `IF`, `AND`, `OR`, `ROUND`, `MAX`, `MIN` — names case-insensitive.
- Operators: arithmetic `+ - * /`, comparison `= > < >= <= <>`, text concatenation `&`.
- Named variables (e.g. `x`, `A1`) bound at `evaluate()` time; lookup case-insensitive.
- `BigDecimal` arithmetic throughout, `MathContext.DECIMAL128` default, `ROUND` uses `HALF_UP`.
- Literals: numbers (incl. decimal and scientific notation), double-quoted strings (`""` escape), booleans `TRUE`/`FALSE` (case-insensitive).
- Lazy `IF`: only the selected branch is evaluated.

**Out of scope:** date/time, arrays, cell ranges, custom user-defined functions/operators via public API (internal structure keeps them easy to add), Excel error values (`#DIV/0!` etc. — Java exceptions are thrown instead).

## 2. Architecture & Data Flow

`Expression` is the public facade. The constructor parses the expression string once into an ANTLR parse tree, which is retained. Each `evaluate(...)` call creates a fresh `EvaluationVisitor` that walks the tree, resolving variables from the supplied map and computing `EvaluationValue`s bottom-up. Parse-once / evaluate-many: re-evaluating with different variable values re-walks the same tree without re-parsing.

```
Expression("IF(A1>0, ROUND(B1,2), 0)")
   └─ ANTLR: CharStream → ExprLexer → CommonTokenStream → ExprParser → ParseTree (retained)
        └─ evaluate({A1=5, B1=1.005}) → EvaluationVisitor(tree, variables, config)
             └─ EvaluationValue(NUMBER 1.01)
```

Lazy `IF` falls out of direct visitor evaluation: the visitor never visits the untaken branch's subtree, so `IF(TRUE, 1, 1/0)` does not throw.

## 3. Package Layout (`org.github.eval`)

| Package | Contents |
|---|---|
| (root) | `Expression` facade — `new Expression(String)`, `with(String, Object)` (fluent variable binding), `evaluate()` / `evaluate(Map<String,?>)` → `EvaluationValue`. `ExpressionConfiguration` (holds `MathContext`, default `DECIMAL128`). `EvaluationException` (runtime evaluation failures), `ParseException` (syntax errors, carries line:column). |
| `data` | `EvaluationValue` — immutable tagged union of `NUMBER(BigDecimal)` / `STRING(String)` / `BOOLEAN(Boolean)` with Excel-style coercion accessors (`getNumberValue`, `getStringValue`, `getBooleanValue`). |
| `parser` | Generated `ExprLexer`/`ExprParser`/`ExprBaseVisitor` (from `Expr.g4`) plus a throwing ANTLR `BaseErrorListener` that converts syntax errors into `ParseException` (no stderr output). |
| `functions` | `FunctionIfc` (with `boolean isLazy()`), `AbstractFunction` (eager-argument helper for non-lazy functions), implementations `If/And/Or/Round/Max/Min`, and `FunctionRegistry` keyed by UPPERCASE name. Unknown function name → `EvaluationException`. |
| `operators` | Arithmetic, comparison, and concatenation semantics with operand coercion — kept out of the visitor so each rule is unit-testable in isolation. |

The visitor stays thin: syntax-dispatch and lazy-`IF` control flow live in the visitor; all value semantics live in `data`, `functions`, and `operators`.

## 4. Grammar (`src/main/antlr/org/github/eval/parser/Expr.g4`)

Recursive-descent rules encode Excel precedence, loosest binding first:

```
expression  : comparison EOF ;   // entry rule
comparison : concatenation (('='|'<>'|'<'|'>'|'<='|'>=') concatenation)* ;
concatenation : additive ('&' additive)* ;
additive    : multiplicative (('+'|'-') multiplicative)* ;
multiplicative : unary (('*'|'/') unary)* ;
unary       : ('-'|'+') unary | primary ;
primary     : NUMBER | STRING | TRUE | FALSE
            | functionCall | variable | '(' comparison ')' ;
functionCall: IDENTIFIER '(' (comparison (',' comparison)*)? ')' ;
```

- Precedence order (loosest→tightest): comparison, `&`, `+ -`, `* /`, unary `- +`, primary. All binary operators left-associative.
- `NUMBER`: integer/decimal with optional exponent (`1`, `3.14`, `.5`, `1E-3`).
- `STRING`: double-quoted, `""` is an escaped quote.
- `TRUE`/`FALSE` and function names matched case-insensitively.
- `IDENTIFIER`: `[A-Za-z_][A-Za-z0-9_.]*` — covers plain names and cell-style refs like `A1`; resolved case-insensitively.
- Whitespace skipped.

## 5. Precision & Value Semantics

- All numbers are `BigDecimal`; never `double`. Literals are parsed straight to `BigDecimal`.
- Division uses the configured `MathContext` (default `DECIMAL128`, 34 digits): `a.divide(b, mc)`. `0.1 + 0.2 == 0.3` exactly; `1/3` carries 34 significant digits.
- `ROUND(x, n)` = `x.setScale(n, RoundingMode.HALF_UP)` — Excel's round-half-away-from-zero: `ROUND(2.5,0)=3`, `ROUND(-2.5,0)=-3`, `ROUND(1.005,2)=1.01`.
- `MAX`/`MIN` accept ≥1 numeric argument and compare via `BigDecimal.compareTo` (scale-insensitive).
- Numeric comparison via `compareTo`; text comparison case-insensitive (`"abc" = "ABC"` is TRUE), matching Excel.

**Coercion rules (Excel-like):**
- Arithmetic/comparison: a `STRING` that parses as a `BigDecimal` is treated as a number; `BOOLEAN` coerces to 1/0 in arithmetic.
- `&`: both operands stringified (`NUMBER` rendered with `toPlainString`, trailing zeros stripped; `BOOLEAN` as `TRUE`/`FALSE`).
- `AND`/`OR`: operands coerced from BOOLEAN, NUMBER (`0`=false), or `"TRUE"`/`"FALSE"` strings; anything else → `EvaluationException`.

## 6. Error Handling

- **Syntax errors:** ANTLR error listener throws `ParseException` with the offending text and line:column.
- **Evaluation errors** (`EvaluationException`): unknown variable, unknown function, wrong argument count, non-coercible type (e.g. `"abc" + 1`), division by zero.
- No partial results, no stderr printing — failures always surface as typed exceptions.

## 7. Build Changes (`build.gradle.kts`)

- Add plugin `id("antlr")`.
- Dependencies: `antlr("org.antlr:antlr4:4.13.2")` (generator tool) and `implementation("org.antlr:antlr4-runtime:4.13.2")`.
- Grammar at `src/main/antlr/org/github/eval/parser/Expr.g4` → generated into `org.github.eval.parser` with `-visitor` enabled; the plugin wires `generateGrammarSource` into `compileJava` automatically.

## 8. Testing (JUnit 6)

Parameterized tests, one suite per function/operator, plus:

- **Precision:** `0.1+0.2=0.3`; 34-digit `1/3`; `ROUND(2.5,0)=3`, `ROUND(-2.5,0)=-3`, `ROUND(1.005,2)=1.01`; `MAX`/`MIN` with mixed-scale operands.
- **Lazy IF:** `IF(TRUE, 1, 1/0)` → 1 (no throw); `IF(FALSE, 1/0, 2)` → 2.
- **Case-insensitivity:** `if(...)`, `And(...)`, `true`; variable `A1` bound as `a1`.
- **Variables:** binding via `with(...)` and via `evaluate(Map)`; unknown variable throws.
- **Coercion & concatenation:** `"1"+2=3`; `1&2="12"`; `"abc"="ABC"` TRUE.
- **Errors:** syntax error position, div-by-zero, wrong arity, type mismatch.
