# ADR-004 — Ledger as proof layer

Status: Accepted — 2026-08-24, Phase 2 human decision review

## Context

IDAX Ledger 0.1.0 ya ofrece Proof, Anchor, Verification y LedgerProvider sin dominio económico.

## Decision

osTRIS mantiene el libro y datos completos. IDAX Ledger recibe digests e identificadores opacos para eventos seleccionados. osTRIS no conoce detalles XRPL y Ledger no conoce balances, crédito, reputación o gobernanza.

## Consequences

Ledger outage no invalida un commit osTRIS; Proof tiene ciclo independiente. `VALIDATED_MATCH` prueba anchor, no verdad, autorización ni ejecución. Batching/Merkle queda abierto.
