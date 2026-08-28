# ADR-007 — Policy-driven credit floors

Status: Accepted — 2026-08-24, Phase 3 enforced-liability semantics

## Context

Communities need different explainable capacity rules; a balance is not a limit.

## Decision candidate

Versioned CreditPolicy produces an effective creditFloor. A floor change affects only future voluntary exposure, never balance/history/default. Available negative exposure is `max(0, balance - creditFloor)`. Transactions snapshot the policy reference and evaluated facts.

## Consequences

Voluntary exposure cannot cross floor. FINAL-finding RESTITUTION/PENALTY may cross it and produce `enforcedLiability=max(0,floor-balance)`, never additional credit.
