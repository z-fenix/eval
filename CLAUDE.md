# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Goal

`eval` is a Java library that evaluates **Excel-style expressions** using an **ANTLR4-generated parser**. It is a from-scratch reimplementation inspired by the sibling project **EvalEx** (`D:\workplace\project\EvalEx`), but uses a real grammar/parser (ANTLR4) instead of EvalEx's hand-written shunting-yard tokenizer.

Required feature scope (the acceptance target):
- **Functions:** `IF`, `AND`, `OR`, `ROUND`, `MAX`, `MIN`
- **Operators:** arithmetic `+ - * /`, comparison `= > < >= <= <>`, and text concatenation `&`
- Function names are case-insensitive (Excel convention).

## Reference Architecture (EvalEx)

EvalEx (`com.ezylang.evalex`) is the design reference. Reuse its *concepts*, not its parser. Key ideas worth mirroring:

- **`EvaluationValue`** (`data/`) — a single tagged-union value type wrapping every runtime type (BigDecimal number, Boolean, String, …) with conversion accessors. Adopt this "one value type" model rather than passing raw `Object` around.
- **`BigDecimal` for all numbers** — never `double`, to match Excel-grade precision and configurable `MathContext`/rounding.
- **`functions/` + `operators/` packages** — each function/operator is a small class behind a `FunctionIfc` / `OperatorIfc` interface. Note `IF` uses **lazy parameter evaluation** (only the taken branch is evaluated); preserve this when implementing `IF` in ANTLR (evaluate the selected branch, not all three children).
- **`Expression`** — the public facade: parse the string once, hold the AST, then `evaluate()` (optionally with variables).

The big architectural **difference**: EvalEx hand-rolls `Tokenizer` + `ShuntingYardConverter` to build an `ASTNode` tree. This project replaces that entire front-end with an **ANTLR4 `.g4` grammar → generated lexer/parser → listener/visitor that evaluates (or builds an AST)**.

## Current State / Build Setup

- Gradle **9.7** wrapper, Kotlin DSL (`build.gradle.kts`), Java toolchain (JDK 25 installed). Group `org.github.eval`.
- `src/` is **empty** — implementation has not started.
- **The ANTLR plugin/dependency is NOT yet configured.** Before writing the grammar you must add to `build.gradle.kts`:
  - the `antlr` plugin (`id("antlr")`),
  - dependency `org.antlr:antlr4-runtime` (and `antlr4` tool, typically via the `antlr` configuration),
  - grammar location `src/main/antlr/`, generated sources wired into `src/main/java` compilation.

## Commands

Use the wrapper (`./gradlew`, or `gradlew.bat` on Windows):

- Build: `./gradlew build`
- Compile only: `./gradlew compileJava`
- Run all tests: `./gradlew test`
- Run a single test class: `./gradlew test --tests "org.github.eval.ExpressionTest"`
- Run a single test method: `./gradlew test --tests "org.github.eval.ExpressionTest.shouldEvaluateIf"`
- Generate ANTLR parser (once plugin added): `./gradlew generateGrammarSource`

Tests are **JUnit 6** (Jupiter, via the `org.junit:junit-bom:6.0.0` platform) running on the JUnit Platform.

## Conventions

- Base package: `org.github.eval`.
- Keep the grammar (`.g4`) focused on syntax; put evaluation semantics in Java visitor/listener code, not embedded grammar actions, so the value/`BigDecimal`/lazy-`IF` rules stay testable.
