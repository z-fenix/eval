# Evaluation Trace / Explanation Feature — Design

**Date:** 2026-08-20
**Project:** `eval` (`org.github.eval`) — extends the ANTLR4 Excel-expression evaluator
**Builds on:** `docs/superpowers/specs/2026-08-20-excel-expression-evaluator-design.md`

## 1. Goal & Scope

Add an **explanation** capability: alongside the evaluated value, produce a human-readable, step-by-step breakdown of how an expression was evaluated.

Example — `new Expression("IF(amount>100000, amount*0.13, amount*0.03)").with("amount", 150000)` produces, in order:

```
1. Resolve variable amount = 150,000
2. 150,000 > 100,000 = true
3. Evaluate condition amount>100000 → true
4. Take branch amount×0.13
5. Resolve variable amount = 150,000
6. 150,000 × 0.13 = 19,500
7. Call IF(amount>100000,amount×0.13,amount×0.03) = 19,500
8. Result: IF(amount>100000, amount*0.13, amount*0.03) = 19,500
```

This generalizes the user's illustrative 3-step shape (condition → branch → result): steps 3/4/6 above correspond to it, with variable, operation, function, and final-result steps filled in around them per the "every operation" granularity decision.

**In scope:**
- A structured trace: every explanation is an ordered `List<Step>` plus the final `EvaluationValue`.
- A step for **every meaningful evaluation event**: variable resolution, each comparison/arithmetic/concatenation/unary operation, each function call, each IF condition and selected branch, and the final result.
- English step descriptions and result rendering (revised from Chinese per user decision), with grouped numbers (`150,000`) and `×`/`÷` operator symbols in rendered text.
- New public API `Expression.explain(...)` returning an `Explanation` (value + steps + `format()`).

**Out of scope:** configurable locales (English only for now), tracing of the untaken IF branch (only the taken branch is traced, matching lazy evaluation), partial traces on error, a streaming/callback trace API.

## 2. Architecture & Data Flow

A **tracer hook** is added to the existing `EvaluationVisitor`. The visitor already computes every operand, operator, and result during its bottom-up walk; when a tracer is attached it emits a `Step` at each event. When no tracer is attached (plain `evaluate()`), a `NoOpTracer` makes the hook cost nothing and behavior is byte-for-byte identical to today.

Rejected alternatives (documented for rationale): a separate re-walk evaluator (duplicates semantics → drift), and subclassing the visitor (re-implements operator loops to capture operands → fragile).

```
Expression.explain({amount=150000})
   └─ evaluate with EvaluationContext(tracer = CollectingTracer)
        └─ EvaluationVisitor emits Step at each event (in depth-first order)
        └─ IfFunction emits CONDITION + BRANCH steps via the context's tracer
   └─ Explanation(value, steps)  →  .format() renders numbered English text
```

Steps record in natural depth-first evaluation order, so nested `IF`/functions read correctly as a numbered sequence.

## 3. New `trace` Package (`org.github.eval.trace`)

| Class | Responsibility |
|---|---|
| `StepType` | enum: `VARIABLE`, `OPERATION`, `CONDITION`, `BRANCH`, `FUNCTION`, `RESULT` |
| `Step` | immutable value: `getType()`, `getDescription()` (English, pre-rendered), `getValue()` (nullable `EvaluationValue`). Structured via `type`+`value`; description rendered once at capture. |
| `EvaluationTracer` | interface: `void record(Step step)` |
| `NoOpTracer` | singleton default; `record` is a no-op |
| `CollectingTracer` | accumulates an ordered `List<Step>` |
| `TraceFormatter` | single place for English wording + number formatting (grouping, `×`/`÷`, boolean → `true`/`false`). No strings hard-coded in the visitor or functions. |

## 4. Step Emission Points

- **Visitor** `visitComparison` / `visitConcatenation` / `visitAdditive` / `visitMultiplicative` / `visitUnary`: one `OPERATION` step per operator applied — e.g. `150,000 × 0.13 = 19,500`. Uses the operands/result the visitor already holds; `ctx.getText()` provides the sub-expression text.
- **Visitor** `visitPrimary` (variable branch): `VARIABLE` step — `Resolve variable amount = 150,000`.
- **Visitor** `visitFunctionCall`: `FUNCTION` step — `Call MAX(...) = ...`.
- **`IfFunction`** (it alone knows which subtree is the condition and which branch is taken): `CONDITION` step — `Evaluate condition amount>100000 → true|false` — then `BRANCH` step — `Take branch amount×0.13`. Reaches the tracer through `EvaluationContext`.
- **`Expression.explain(...)`**: appends the terminal `RESULT` step — `Result: <original expression> = 19,500`.

## 5. Public API

- `Expression.explain()` → `Explanation`
- `Expression.explain(Map<String, ?> variables)` → `Explanation`
- works with `.with(name, value)` bindings exactly like `evaluate()`.

`Explanation` (`org.github.eval`): `getValue()` → `EvaluationValue`; `getSteps()` → `List<Step>`; `format()` → numbered multi-line English string.

`EvaluationContext` gains a tracer field (defaults to `NoOpTracer`) and a `getTracer()` accessor; a package-private way for `Expression`/`EvaluationVisitor` to supply the `CollectingTracer`. `IfFunction` reads the tracer from the context.

## 6. Error Handling

Tracing is observe-only and never changes evaluation semantics. If evaluation throws (`ParseException` / `EvaluationException`), the exception propagates exactly as in `evaluate()`; no `Explanation` is returned and any steps captured before the throw are discarded.

## 7. Testing (JUnit 6)

- Unit test per emission point: variable, each operator kind, unary, function call, IF condition + branch, result.
- Acceptance test reproducing the exact 3-step example above.
- Nested-IF step ordering.
- Number formatting: grouping, `×`/`÷`, negative numbers, booleans.
- A test proving plain `evaluate()` emits no trace and is behaviorally unchanged.
- Error case: `explain` on a division-by-zero propagates `EvaluationException`.
