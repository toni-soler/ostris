# ADR-001 — Mutual credit instead of token issuance

Status: Accepted — 2026-08-24, Phase 2 human decision review

## Context

osTRIS necesita compensar intercambio sin suministro, venta o activo especulativo previo.

## Decision

El modelo base es mutual credit: cada transacción comprometida crea entradas compensadas que suman cero. No hay ICO, mining, staking, token supply, peg ni convertibilidad garantizada. La unidad no cambia por el paso del tiempo.

## Consequences

El sistema soporta balances negativos bajo floors y no requiere custodia fiat/cripto. Debe gestionar riesgo/default socialmente; no puede delegarlo a escasez de token o blockchain. Estudiar tokens XRPL sería una decisión futura distinta.
