# ADR-005 — Protocol independent of IDAX implementation

Status: Accepted — 2026-08-24, Phase 2 human decision review

## Context

Definir osTRIS mediante SQL, JPA o DTO Java impediría implementaciones independientes.

## Decision

El protocolo define modelo semántico, invariantes, estados, formatos versionados, identifiers, canonicalización, autorización y capabilities. PostgreSQL/Java/IDAX serán decisiones de una referencia, no requisitos protocolarios.

## Consequences

Se necesitan especificación normativa, test vectors y conformance suite entre lenguajes. Cambios de implementación no cambian protocolo; incompatibilidades requieren versionado explícito.
