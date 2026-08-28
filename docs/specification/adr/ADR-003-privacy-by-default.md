# ADR-003 — Privacy by default

Status: Accepted — 2026-08-24, Phase 2 human decision review

## Context

Transparencia total expone PII, relaciones, balances y metadatos; hashes ingenuos pueden ser reversibles por diccionario.

## Decision

Clasificar datos como private, participant-visible, community-visible, publicly-verifiable o public. PII y contenido completo permanecen off-ledger. Sólo commitments opacos, mínimos y cuidadosamente construidos pueden anclarse.

## Consequences

La verificación puede requerir disclosure/inclusion proof adicional. Access control, retención, batching y mitigación de correlación forman parte del protocolo/implementación, con revisión GDPR y de privacidad.
