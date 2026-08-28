# Architecture Scenarios

Todos los importes pertenecen a una única unidad salvo indicación. Los escenarios validan arquitectura; no son código ni fijan políticas.

## A — Basic mutual credit

| Entry | Account | Amount |
| ----- | ------- | -----: |
| 1     | A       |    +20 |
| 2     | B       |    -20 |

Suma 0; balances nacen del journal; autorización A/B según política.

## B — Circular exchange

A→B 20: `A+20 B-20`; B→C 15: `B+15 C-15`; C→A 10: `C+10 A-10`. Resultado `A+10, B-5, C-5`, total 0. Demuestra compensación sin deuda bilateral ni doble coincidencia.

## C — Credit limit reached

B está en -90 y su floor es -100. Una propuesta que carga -20 produciría -110: no se compromete. Puede reducirse a 10, obtener excepción versionada o aportar valor primero. El rechazo no crea asientos ni perjudica automáticamente reputación.

## D — Reversal

Original: `A+20, B-20`. Reversal total referenciado: `A-20, B+20`. Ambas quedan `COMMITTED`; posición neta 0 y cada transacción suma 0. Repetir el reversal debe rechazarse como sobre-reversión/idempotencia.

## E — Dispute

Una transacción comprometida se disputa. El journal no cambia. Mediación decide ajuste parcial 5: nueva settlement `A-5, B+5`. La decisión y el asiento tienen evidencias/autorizaciones separadas.

## F — Participant leaves while negative

B desea salir en -80. La política puede bloquear cierre, acordar restitución, activar guarantor/reserva o declarar default/write-off. Borrar B rompería auditabilidad. Si una reserva absorbe 80: `B+80, Reserve-80`; total 0 y la reserva queda explícitamente expuesta.

## G — Organization account

Coop X tiene cuenta controlada por operador y aprobador; una transacción de 500 requiere ambos según política. Usuario ≠ organización ≠ cuenta. Revocar al operador no altera el balance ni identidad de la organización.

## H — Two communities federate

A y B acuerdan ratio temporal `2 A-unit = 1 B-unit` y floors bilaterales. Un intercambio genera asientos equilibrados en cada libro y receipts correlacionados. Si B no confirma, el protocolo necesita suspense/compensación definida; un Merkle Proof compartido no resuelve atomicidad. La paridad no es intrínseca ni permanente.

## I — Multipart transaction

Servicio conjunto valorado en 100: proveedor `+100`, comprador `-60`, patrocinador `-40`. Suma 0; tres cuentas afectadas deben quedar autorizadas y dentro de floor.

## J — Ledger unavailable

La transacción válida se compromete localmente y el Proof queda pendiente. El balance existe y es reconstruible; cuando Ledger vuelve, se ancla el mismo commitment idempotente. Si política de alto riesgo exige anclaje previo a una entrega posterior, ésta espera, no el commit ya realizado.

## Resultado

Los escenarios conservan suma cero, inmutabilidad, autorización, floors explícitos, separación reputacional y privacidad. Revelan tres decisiones críticas abiertas: salida/default, atomicidad federada y relación temporal commit↔anchor.

# Phase 2 Core and Risk Scenarios

## K — KYC-verified new participant

Provider returns a minimal active assurance claim; osTRIS stores no document scan. IdentityPolicy allows membership and CreditPolicy assigns a policy-derived floor. KYC does not imply trust/honesty and RiskPolicy still screens proposals. No accounting entry is created by verification or floor assignment.

## L — Social reset after clean history

Profile A is retired and public/social data minimized. Profile B links privately to the same RiskSubject only where lawful. Public reputation starts afresh; journal obligations and minimal risk/accounting history remain restricted. No balance mutation occurs.

## M — Social reset after sanction

Profile B does not reveal old Profile A publicly, but RiskPolicy may use confirmed, relevant, non-expired events through the private RiskSubject. A reasoned review/restriction is possible; automatic eternal punishment and global blacklist are not.

## N — ZOMBIE recovery

Account `-80` meets policy-defined inactivity and becomes ZOMBIE without entries. The person returns, re-verifies and agrees a plan. Policy moves it to ACTIVE or RESTRICTED; no write-off occurred and the balance remains `-80`.

## O — ZOMBIE to DEFAULTED and write-off

After configured contact attempts, grace and review, DefaultPolicy declares DEFAULTED. A governed write-off commits participant `+80`, CommunityLossAccount `-80`. Original transactions remain; all balances including special accounts sum zero.

## P — Fraud restitution

Original `A +500 / B -500` remains. A final finding authorizes restitution. If A can pay, a new transaction debits A and credits the loss/victim account. Finding, authorization, policy references and appeal outcome remain separate from journal entries.

## Q — Economic penalty and loss offset

Penalty: sanctioned account `-50`, CommunityPenaltyAccount `+50`. Later governed offset: PenaltyAccount `-50`, LossAccount `+50`. Both transactions sum zero; loss and penalty semantics are not mixed by direct balance updates.

## R — Repeat offender and rehabilitation

Only prior confirmed/upheld RiskEvents are considered. A repeated confirmed fraud may yield stronger restrictions under a versioned policy. Completed restitution, served restriction and sustained correct operation may restore capabilities progressively; history is not erased.

## S — False-positive fraud/identity appeal

A concentration signal opens a case but does not establish fraud. Reviewer incorrectly links two RiskSubjects; participant appeals with provider evidence. Finding is overturned, restrictions lifted and appeal event retained. No economic transaction was created, so no reversal is needed.

## T — Organization with delegated ControlPolicy

Organization account uses 2-of-3 approval above a threshold and grants an operator a smaller delegated limit. Key rotation produces a new ControlPolicy version without changing account ID/balance. Recovery requires threshold plus organization verification; a single administrator cannot seize it.

## U — Collusive credit extraction case study

1. A and B are verified; verification does not prove trade authenticity.
2. Fabricated transaction proposes `A +500 / B -500`, nearly exhausting B's floor.
3. Available pre-commit signals: new accounts, unusually large amount, 100% single-counterparty concentration, rapid utilization and possible linked activity.
4. RiskPolicy could require review/block new exposure; it cannot declare fraud automatically.
5. If committed, B disappears: ZOMBIE then possibly DEFAULTED under policy, not merely elapsed time.
6. Write-off `B +500 / LossAccount -500` makes loss explicit. A's `+500` remains pending a separate finding/remedy.
7. Fraud requires case evidence, affected transactions, review and appeal. A confirmed resolution can authorize restitution and penalty.

Every accounting step is zero-sum; creditFloor alone demonstrably failed to prevent extraction.

## V — Fraud proceeds already spent

A spends fraudulent `+500` with innocent C/D before the finding. C/D transactions remain committed. Community loss remains until governed recovery. After a FINAL Finding, A can incur RESTITUTION below ordinary floor, creating enforced liability and zero voluntary credit; later positive entries reduce it.

## W — Penalty below floor edge

Account is already at floor and receives proposed `-50` PENALTY. It commits only with FINAL Finding and SanctionPolicy authorization. Balance crosses floor by 50, `enforcedLiability=50`, available voluntary exposure remains zero and CommunityPenaltyAccount receives `+50`.

## Phase 2 invariant check

K–W preserve immutable commits, no direct balance mutation, zero sum including special accounts, policy traceability, separation of prevention/finality/sanction, enforced liability and social-reset privacy. Normative edge cases are mirrored in the test vectors.
