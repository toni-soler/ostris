# ADR-006 — AccountControlPolicy and recoverable control

Status: Accepted — 2026-08-24, Phase 3 wire/ordering semantics

## Context

An account cannot safely assume one user, one operator or one permanent key.

## Decision candidate

Every account references a versioned AccountControlPolicy defining controllers, threshold, delegations, validity, revocation and recovery. Account ID, balance and history survive key/controller rotation. Recovery requires explicit evidence/audit and cannot grant one administrator silent universal takeover.

## Consequences

AuthorizationPayload/Ed25519 encode participant intent; CommunitySequence orders activation/revocation/commit and authorization is re-evaluated at commit. Recovery still requires community policy but cannot bypass evidence/audit.
