# ADR-002 — Double-entry immutable journal

Status: Accepted — 2026-08-24, Phase 2 human decision review

## Context

Mutar campos de saldo directamente impide reconstrucción, auditoría y correcciones seguras.

## Decision

Toda mutación procede de una transacción atómica con 2..n entradas cuya suma exacta es cero. Commit y entries son inmutables; reversal/correction/settlement añaden transacciones relacionadas. Los balances son proyecciones reconstruibles.

## Consequences

Concurrencia debe respetar floors en un orden serial equivalente. Cuentas especiales siguen equilibradas y explícitas. Se requieren precisión decimal y reglas canónicas de redondeo.
