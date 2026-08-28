# ADR-010 — Economic resolution through special accounts

Status: Accepted — 2026-08-24, Phase 3 transaction-purpose semantics

## Context

Write-offs and penalties must make losses/transfers explicit without rewriting balances.

## Decision candidate

Write-off, restitution, penalty and loss offset are zero-sum journal transactions. Governed CommunityLossAccount and CommunityPenaltyAccount have explicit control/purpose and are included in community-wide zero sum.

## Consequences

WRITE_OFF, RESTITUTION, PENALTY and LOSS_OFFSET share the journal and fixed references. Restitution/penalty below floor creates liability. Distribution/reserve policy remains optional/out of Core.
