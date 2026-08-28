# Threat Model

Estado: análisis inicial; requiere talleres con seguridad, privacidad, economía y operadores comunitarios.

## Activos y fronteras

Activos: integridad del journal, autorizaciones, disponibilidad, identidad/roles, PII, reputación, políticas, claves y receipts de federación. Fronteras: cliente↔implementación, control comunitario, comunidad↔comunidad e implementación↔IDAX Ledger.

Adversarios incluyen participante individual, coalición, administrador, comunidad remota, atacante externo y operador de infraestructura. Un ledger honesto no implica que el dato original o la autorización sean honestos.

## Riesgos y controles candidatos

| Amenaza                       | Impacto                               | Controles candidatos / límite residual                                                                                     |
| ----------------------------- | ------------------------------------- | -------------------------------------------------------------------------------------------------------------------------- |
| Sybil identities              | multiplicar crédito/endorsements      | patrocinio, verificación selectiva, floors progresivos, cluster detection; no elimina identidades humanas múltiples        |
| fake transactions / collusion | crédito/reputación artificial         | autorización multipartes, límites de exposición, análisis y auditoría; partes coludidas pueden afirmar intercambios falsos |
| credit farming                | subir límites y abandonar             | maduración, métricas multidimensionales, garantía, revisión; falsos positivos y discriminación                             |
| account takeover              | transacciones no autorizadas          | MFA, claves protegidas, delegación limitada, revocación, recovery gobernado                                                |
| duplicate / replay            | doble commit o cross-community replay | IDs idempotentes, domain separation, community/unit/version, nonce/expiry                                                  |
| double spending of credit     | exceder floor con concurrencia        | commit serializable/equivalente y reserva de exposición pendiente                                                          |
| false reputation / brigading  | manipulación social                   | provenance, scope, sample size, contestación, límites a endorsements                                                       |
| malicious administrator       | cambia reglas, PII o journal          | separación de funciones, approvals, append-only audit, Proofs selectivos, export/reconciliation                            |
| malicious community           | fraude federado/default               | límites bilaterales, capabilities verificadas, suspensión y dispute agreement                                              |
| data tampering                | balances/historia corruptos           | journal inmutable, reconstrucción, firmas/receipts, anchors y backups                                                      |
| privacy leakage               | daño personal/social                  | minimización, ACL, batching, opaque IDs, retención y threat review                                                         |
| denial of service             | impedir comercio/settlement           | rate limit, queues, redundancia, degraded read, recovery y prioridades                                                     |

## Privacy threat model

- `transaction graph deanonymization`: relaciones por identificadores repetidos;
- `balance inference`: diferencias temporales y agregados pequeños;
- `social graph inference`: endorsements, garantías y disputas;
- `metadata leakage`: tipo, tamaño, frecuencia y comunidad;
- `timing correlation`: evento privado y anchor cercano;
- `public ledger leakage`: commitments de bajo espacio o IDs correlacionables.

Batching reduce correlación uno-a-uno, pero el tamaño/horario del batch y las pruebas entregadas todavía filtran información. Hashing sin secreto/nonce no basta.

## Abuso económico y default

Blockchain no evalúa capacidad de devolver valor. Floors, garantías, reservas y seguros desplazan riesgo, no lo eliminan. Todo mecanismo debe documentar quién soporta la pérdida y evitar incentivos para inflar crédito.

## Collusive credit extraction and identity-reset attacks

The primary economic abuse for Core v0.1 is collusive credit extraction: A and B fabricate `A +500 / B -500`; B disappears/defaults and the community socializes B's loss while A retains or spends the positive balance. A per-account creditFloor only bounds B's extraction and cannot determine whether A's credit was earned legitimately.

| Attack/variant                            | Prevent                               | Detect                           | Limit damage                      | Recover                                         |
| ----------------------------------------- | ------------------------------------- | -------------------------------- | --------------------------------- | ----------------------------------------------- |
| collusive extraction                      | progressive floor, relationship limit | concentration/burst/link signals | review/block staged exposure      | finding, restitution, loss accounting           |
| circular fake trading                     | limits and maturity                   | reciprocal loops/short cycles    | flag/review                       | case and compensating entries                   |
| many-account/straw collusion              | assurance/sponsorship                 | shared graph/timing patterns     | aggregate relationship controls   | restrict linked confirmed actors                |
| serial re-registration                    | private RiskSubject continuity        | provider subject match           | no automatic clean floor          | appealable link and prior confirmed events      |
| synthetic identity/provider abuse         | provider assurance/key controls       | contradictions/revocations       | restrict capabilities             | revoke claim and investigate case               |
| penalty evasion via new profile/org       | verified continuity                   | historical account links         | retain scoped restrictions        | restitution/rehabilitation process              |
| laundering proceeds through third parties | staged/high-risk flow review          | rapid fan-out and extraction     | stop only new suspicious exposure | do not reverse innocent trades; debtor recovery |

Signals do not establish guilt. False identity matching, graph guilt-by-association and invasive fingerprinting are threats themselves. KYC reuse must be minimal, community-scoped, audited and appealable.

## Control strategy boundary

Not every attack can be prevented. Core v0.1 must provide safe primitives to prevent obvious violations, detect deterministic signals, limit exposure and recover through explicit accounting/case processes. It must not claim fraud certainty from a graph metric or opaque AI score.

## Requisitos de seguridad derivados

- domain separation en firmas/hashes por versión, comunidad, unidad y tipo;
- protección de downgrade y replay;
- mínimo privilegio y segregación de administración, crédito y disputa;
- recuperación de cuenta que no permita apropiarse de balances sin evidencia;
- audit logs que no incluyan secretos/PII innecesaria;
- planes de key compromise, fork, outage y reconciliación;
- tests interoperables y revisión criptográfica externa antes de producción.

## Riesgos no resueltos

Propagación de defaults federados, privacidad del trust graph, coerción/compromiso de administradores, seguridad del recovery y collusion económicamente rentable requieren modelos cuantitativos posteriores.
