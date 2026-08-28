# Core Wire & Decision Semantics v0.1

Status: normative candidate for osTRIS Core `0.1`, Single Community only.

Normative references: RFC 8785 (JCS), RFC 7493 (I-JSON), RFC 9562 (UUID), RFC 8032 (Ed25519), RFC 4648 (base64url), SHA-256. Test vectors under `test-vectors/` are normative.

## 1. Version layers and fail-closed behavior

- `ostrisCoreVersion`: semantic protocol version, exactly `"0.1"` here.
- `wireFormat`: exactly `"OSTRIS-CORE-JCS-1"`.
- policy IDs/versions: community configuration versions, independent of Core/wire versions.
- implementation version: local and non-normative.

Unknown transaction purpose, policy type, risk metric, signature algorithm, signed-payload field or normative top-level field **MUST** fail closed. Non-normative metadata is allowed only under `extensions`, whose keys are reverse-DNS namespaces. Extensions are not signed unless explicitly referenced by a normative contractual digest and may not alter Core semantics or invariants.

## 2. Canonical JSON

Protocol objects are I-JSON. Canonical bytes are RFC 8785 JCS output encoded UTF-8, with no BOM. Parsers **MUST** reject duplicate names, lone surrogates, invalid UTF-8, NaN/Infinity and non-I-JSON numbers. Strings are preserved as-is; Unicode normalization is forbidden. Object keys use JCS UTF-16 code-unit ordering; array order is preserved.

Economic amounts, sequences and other unbounded protocol integers are JSON strings, never JSON numbers. JCS libraries do not define domain validation; implementations validate the schema before canonicalization.

## 3. Amounts, units and ratios

`amount` is a signed base-10 integer in minor units encoded as a JSON string. Grammar:

```abnf
amount = "0" / ( ["-"] nonzero-digit *DIGIT )
nonzero-digit = %x31-39
```

Accepted: `"0"`, `"100"`, `"-325"`, `"2050"`. Rejected: `"+100"`, `"00100"`, `"-0"`, `"1.0"`, `"1e3"`, JSON number `100`. Maximum encoded magnitude is 78 decimal digits in v0.1; arithmetic remains exact and overflow is an error.

A Unit has immutable UUIDv7 `unitId`, uppercase ASCII `code` matching `[A-Z][A-Z0-9_-]{0,15}`, and integer JSON number `scale` in `0..18`. Scale is immutable. Human `20.50` in scale 2 is amount `"2050"`; conversion/rounding into minor units occurs before protocol authorization.

Ratios are non-negative basis-point JSON integer strings using the unsigned equivalent grammar. `10000` is 100%. Metrics may exceed `10000` only where explicitly stated (`FLOOR_UTILIZATION`); concentration and reciprocal exposure are bounded `0..10000`.

## 4. Identifiers

All newly created protocol entities use RFC 9562 UUIDv7 represented as lowercase canonical text `8-4-4-4-12`. The UUID version nibble must be `7` and variant bits RFC 4122/9562 (`8`, `9`, `a` or `b` at the first character of group four). IDs include community, unit, participant, RiskSubject, account, credential, policy/version, transaction, authorization, case, finding, appeal, continuity decision and normative event IDs.

Controller IDs and CommunityResolutionAuthority IDs are also UUIDv7. A controller is an opaque authorization principal, not identity/KYC data.

IDs are opaque. PII or hashes of PII are forbidden. UUID timestamp ordering is not normative precedence; CommunitySequence is.

## 5. Transaction purposes and relationships

The closed vocabulary is:

| Purpose       | Required semantics/references                                                                                                     |
| ------------- | --------------------------------------------------------------------------------------------------------------------------------- |
| `EXCHANGE`    | ordinary authorized exchange; no required related transaction                                                                     |
| `REVERSAL`    | exactly one `reversesTransactionId`; entries exactly negate the original in original entry order                                  |
| `SETTLEMENT`  | autonomous agreed/adjudicated adjustment with at least one `caseId`, `agreementId` or `settlesTransactionId`                      |
| `WRITE_OFF`   | `defaultDecisionId`; full-only: exactly one positive entry equal to the defaulted account's exact negative pre-commit balance and one balancing negative entry to CommunityLossAccount |
| `RESTITUTION` | voluntary: neither mandate reference nor ResolutionBasis; imposed finding: `finalFindingId`; imposed dispute: `disputeResolutionId`; economic recovery may cross floor only on the governed path |
| `PENALTY`     | `finalFindingId`; debit sanctioned account and credit CommunityPenaltyAccount; may cross floor                                    |
| `LOSS_OFFSET` | governanceDecisionId; debit CommunityPenaltyAccount and credit CommunityLossAccount                                               |

All use one `MutualCreditTransaction` and `entries[]`. A v0.1 reversal is full, exactly negates one known committed transaction, and an original may have zero or one committed reversal. A reversal cannot reverse another reversal. Partial or second reversals fail; partial correction uses SETTLEMENT. Original and reversal remain COMMITTED.

Special-account participation is closed. CommunityPenaltyAccount is permitted directly only in PENALTY and LOSS_OFFSET; CommunityLossAccount only in WRITE_OFF and LOSS_OFFSET. Either may occur in REVERSAL only when the original contains that same account and exact full-reversal reconstruction mechanically reproduces it. Direct EXCHANGE and every other non-listed use reject. REVERSAL cannot introduce a special account absent from the original. PENALTY debits the sanctioned account and credits CommunityPenaltyAccount; WRITE_OFF follows the full-only rule above; LOSS_OFFSET debits CommunityPenaltyAccount and credits CommunityLossAccount.

`references.disputeResolutionId` is the lowercase canonical UUIDv7 of the persisted FINAL_DISPUTE_RESOLUTION that mandates an imposed RESTITUTION. RESTITUTION reference rules are closed: voluntary forbids both typed mandate references and has no ResolutionBasis; imposed FINAL_FINDING requires only `finalFindingId` equal to basis ID; imposed FINAL_DISPUTE_RESOLUTION requires only `disputeResolutionId` equal to basis ID. Both references, missing/mismatched references, mixed Account/Governance evidence, nonexistent/wrong-scope bases and any post-signature change reject. No `restitutionMode` field exists because optional ResolutionBasis plus the exclusive authorization path is canonical and sufficient.

REVERSAL and SETTLEMENT were reviewed for the same dual-path ambiguity. `reversesTransactionId` identifies the economic original in both reversal paths, while an adjudicated mandate is separately and exactly bound by GovernanceAuthorization's FINAL_DISPUTE_RESOLUTION. SETTLEMENT's existing `caseId`/`agreementId`/`settlesTransactionId` references identify its economic context; an adjudicated mandate is likewise separately bound by its governance basis. Neither overloads a Finding reference, so no new typed mandate reference is added for them.

Legitimate later transactions with third parties are final and never automatically reversed when earlier proceeds are found fraudulent. Liability remains with the accountable subject, community loss and future recovery.

## 6. AuthorizationPayload v1

Participant authorization signs only economic intent:

```json
{
  "communityId": "<uuidv7>",
  "contractualMetadataDigest": "<optional lowercase sha256 hex>",
  "entries": [{ "accountId": "<uuidv7>", "amount": "<integer string>" }],
  "ostrisCoreVersion": "0.1",
  "purpose": "EXCHANGE",
  "references": {},
  "transactionId": "<uuidv7>",
  "unitId": "<uuidv7>",
  "wireFormat": "OSTRIS-CORE-JCS-1"
}
```

`contractualMetadataDigest` is omitted when no external terms are contractual. Display labels, local notes and UI descriptions are never included. Entry array order is significant and signed; duplicate account IDs are forbidden. `references` contains only purpose-allowed keys and is `{}` when none.

The signed message bytes are exactly:

```text
UTF8("OSTRIS:TX:AUTH:V1") || 0x00 || JCS_UTF8(AuthorizationPayload)
```

`authorizationDigest` is lowercase hex SHA-256 of these message bytes. The Ed25519 signature is over the same bytes, not over the digest.

## 7. Ed25519 encoding and verification

`signatureAlgorithm` is `"Ed25519"`. Public keys are raw 32-byte RFC 8032 compressed public keys encoded base64url without `=` padding. Signatures are raw 64-byte Ed25519 signatures encoded the same way. Decoders reject padding, non-canonical alphabet, wrong decoded length or re-encoding mismatch. Test seeds are raw 32-byte base64url and are present only in clearly marked test vectors; production private keys never enter protocol messages.

Verification validates schema/domain first, reconstructs message bytes, decodes key/signature canonically, then invokes a standard Ed25519 primitive. Algorithms other than Ed25519 fail closed in v0.1.

Credential eligibility at commit is resolved through one immutable `credentialId → controllerId` binding in the same Community. Binding and controller activation must precede commit; their revocation must not. A credential ID cannot later bind another controller. Multiple active credentials for one controller count once; the same public key cannot make distinct controllers eligible in one Community. Unknown, unbound, revoked, rebound or ambiguous credentials/keys fail closed.

### GovernanceAuthorizationPayload v1

Governed authorization signs this closed I-JSON object using the existing `OSTRIS-CORE-JCS-1` wire format:

```json
{"authorityId":"<uuidv7>","authorityPolicyVersion":1,"communityId":"<uuidv7>","coveredAccounts":["<uuidv7>"],"entries":[{"accountId":"<uuidv7>","amount":"-50"}],"ostrisCoreVersion":"0.1","purpose":"PENALTY","references":{"finalFindingId":"<uuidv7>"},"resolutionBasis":{"id":"<uuidv7>","type":"FINAL_FINDING"},"transactionId":"<uuidv7>","unitId":"<uuidv7>","wireFormat":"OSTRIS-CORE-JCS-1"}
```

`coveredAccounts` is non-empty, duplicate-free, in ascending UUID text order, and every member must be an affected entry account. It identifies exactly where governed authorization substitutes account authorization. `authorityPolicyVersion` is a positive JSON integer because it is bounded configuration metadata. Unknown fields fail closed.

The signed bytes are exactly:

```text
UTF8("OSTRIS:TX:GOVAUTH:V1") || 0x00 || JCS_UTF8(GovernanceAuthorizationPayload)
```

The digest/signature and raw Ed25519/base64url rules are identical to participant authorization. The distinct domain prevents cross-mode replay. Purpose/basis combinations are closed by the matrix in `CORE_PROTOCOL_V0_1.md`; EXCHANGE always fails. Entries, references, basis, authority/version and covered accounts are therefore cryptographically bound.

## 8. Commit decision and CommunitySequence

`communitySequence` is a strictly increasing, gap-permitted, unique positive integer string within one community. It establishes normative total order. Wall-clock timestamps are audit metadata and never break precedence ties.

The following normative events receive a sequence:

- PolicySet/policy-version activation;
- AccountControlPolicy activation and controller/delegation/credential revocation;
- IdentityAssurance revocation;
- IdentityContinuityDecision status transition;
- Finding issue, appeal and finality/overturn;
- MutualCreditTransaction commit.

UI events, proposal edits and signature creation do not. A sequenced event observes all lower sequences and none higher. The sequencer allocates the next sequence atomically with event commit.

Transactions use policies effective at their commit sequence. Authorization credentials, delegations and assurance claims must still be valid immediately before that commit event. Revocation at a lower sequence invalidates later commit; revocation at a higher sequence does not retroactively invalidate it. Proposal/signature time does not override this rule.

The community commit decision appends `communitySequence`, commit timestamp, immutable policy references, evaluated facts/outcomes and evaluator version. These are not participant intent and are not inserted into the original AuthorizationPayload. Community authorization/audit protects the commit decision separately.

## 9. Credit floor and enforced liability

For exact integer balance `B` and non-positive floor `F`:

```text
availableNegativeExposure(B,F) = max(0, B - F)
enforcedLiability(B,F) = max(0, F - B)
```

Ordinary EXCHANGE/voluntary SETTLEMENT cannot produce `projectedBalance < F`. Positive/improving operations remain possible below floor. Worsening below floor is permitted only for governed PENALTY with FINAL_FINDING and effective `OSTRIS-SANCTION-POLICY-1.penaltyAllowed=true`, or imposed RESTITUTION with its matching final basis and matching SanctionPolicy flag. All authorization, basis, policy and coverage gates must pass at commit sequence. Such an entry creates enforced liability; it is not additional credit. WRITE_OFF rules are governed separately and do not imply fraud.

## 10. Finding finality and identity continuity

A `Finding` lifecycle is `UNDER_REVIEW → ISSUED → FINAL` or `ISSUED → APPEALED → FINAL|OVERTURNED`. `OPEN` belongs to RiskCase, not Finding. `FINAL` means appeal deadline expired without appeal, or appeal upheld. Only FINAL findings may authorize culpability-based RESTITUTION/PENALTY. Prior preventive measures can require review, block new exposure, request assurance or temporarily restrict risk; they never alter balances or assert guilt.

A Finding stores finding/case IDs, rule and evidence references, authority, reason codes/reasoning, issuedAt/sequence, appeal deadline, status transitions and finality sequence.

Identity continuity uses a provider's stable opaque community/pairwise subject reference when available. Otherwise an `IdentityContinuityDecision` records decision ID, RiskSubject/Participant IDs, evidence, authority, reason, status and sequence. Status is `CONFIRMED`, `CONTESTED` or `REJECTED`. Only CONFIRMED links apply historical risk automatically. CONTESTED links may cause REQUIRE_REVIEW or temporary prevention but never sanction/restitution solely from the link. Resolution and appeal are sequenced/audited. Simple hashes of PII are forbidden.

## 11. Five normative risk metrics

At evaluation for prospective sequence `S`, RiskPolicy resolves `windowSizeSequences=W` (`1..1000000`). The window includes committed transactions with `max(0,S-W) <= communitySequence < S`. Only the same community/unit is considered. Thresholds and W are community policy; formulas are protocol.

All division uses exact non-negative integer arithmetic with floor toward zero. Basis points use `floor(numerator * 10000 / denominator)`.

### FLOOR_UTILIZATION

Uses the proposal's projected balance. If projected balance `>=0`, result `0`. If floor `<0`, result is `floor(abs(min(0,projectedBalance))*10000/abs(floor))`; it may exceed 10000 for authorized enforced liability. If floor `=0` and projected balance `<0`, the operation is invalid before metric evaluation.

### COUNTERPARTY_CONCENTRATION

V0.1 directional counterparty metrics include only eligible bilateral EXCHANGE transactions: exactly two distinct nonzero entries, one positive and one negative. For the subject, calculate gross positive bilateral EXCHANGE inflow per negative counterparty. Result is maximum counterparty inflow divided by total such inflow in the window, in bps. Zero denominator returns `0`. SETTLEMENT and all other purposes are excluded.

### COUNTERPARTY_COUNT

Number of distinct other account IDs with a nonzero entry in any committed EXCHANGE containing a nonzero subject entry in the window. Bilateral and multipart exchanges count; there is no minimum volume beyond nonzero amount. Result is an unsigned integer string.

### RECENT_VOLUME

Sum of `abs(subject entry amount)` for committed EXCHANGE in the window, including multipart exchanges. Result is minor units as unsigned integer string.

### RECIPROCAL_EXPOSURE

Using eligible bilateral EXCHANGE only, for the subject/counterparty pair selected by the rule, let `out` be gross minor units where subject is negative and counterparty positive, and `in` where subject is positive. If both zero, result `0`; otherwise `floor(min(in,out)*10000/max(in,out))`, bounded 0..10000.

## 12. Risk outcomes, precedence and reasons

Closed outcomes and precedence:

```text
BLOCK_NEW_EXPOSURE > REQUIRE_REVIEW > ALLOW_WITH_FLAG > ALLOW
```

The aggregate is the strongest rule outcome. `BLOCK_NEW_EXPOSURE` blocks only proposals that worsen negative/exposure facts identified by the rule; improving/required recovery remains evaluable. Stable reason codes use uppercase dotted namespace `OSTRIS.<DOMAIN>.<REASON>` matching `[A-Z][A-Z0-9_]*(\.[A-Z][A-Z0-9_]*){2,}`. Examples: `OSTRIS.RISK.COUNTERPARTY_CONCENTRATION`, `OSTRIS.IDENTITY.ASSURANCE_REQUIRED`.

PolicyDecisionSnapshot records policy ID/version, resolved sequence window, metric inputs/results, rule ID/operator/threshold, per-rule outcome, aggregate outcome, reason codes and commit sequence.

## 13. ProtocolEventProofPayload v1

`protocolDigest = SHA-256(JCS_UTF8(canonical transaction intent))` remains the commitment to transaction intent. It does not by itself prove commit state, CommunitySequence or external anchoring.

A Proof for a committed event has exactly these fields and no others:

```json
{
  "ostrisCoreVersion": "0.1",
  "wireFormat": "OSTRIS-CORE-JCS-1",
  "communityId": "<UUIDv7>",
  "unitId": "<UUIDv7>",
  "transactionId": "<UUIDv7>",
  "transactionDigest": "<64 lowercase hexadecimal SHA-256>",
  "communitySequence": "<canonical non-negative integer string>",
  "commitState": "COMMITTED"
}
```

All eight fields are required. Unknown fields, wrong types, non-canonical UUIDv7, uppercase/non-hex digests, leading-zero/non-integer sequences and any `commitState` other than `COMMITTED` reject. `transactionDigest` MUST equal a fresh recomputation of the committed transaction's persisted `protocolDigest`; community, unit and transaction IDs and sequence MUST equal the immutable journal source event.

Exact Proof bytes are:

```text
UTF8("OSTRIS:PROTOCOL:EVENT:PROOF:V1") || 0x00 || JCS_UTF8(ProtocolEventProofPayload)
```

`proofDigest` is lowercase hexadecimal `SHA-256` of those exact bytes. The payload uses I-JSON, RFC 8785 JCS and UTF-8 without BOM. It contains no timestamp, entries, balances, identity/risk data, decision bodies, policy config, suite digest or Ledger/provider identifiers.

The external Ledger profile is `EXTERNAL:OSTRIS-PROTOCOL-EVENT-PROOF-V1`; it is metadata and is not hashed. The deterministic off-ledger reference is:

```text
externalReferenceDigest = SHA-256(
  UTF8("OSTRIS:LEDGER:EXTERNAL-REF:V1") || 0x00 ||
  UTF8(communityId) || 0x00 || UTF8(transactionId)
)
externalReference = "ostris:v1:" || lowercaseHex(externalReferenceDigest)
```

It is 74 ASCII characters, stable across retries and below IDAX Ledger 0.1.0's 255-character `externalId` limit. It does not affect `proofDigest` and the plaintext transaction UUID is not used as the external reference.

## 14. Normative vector gate

`test-vectors/core-v0.1.json` and `test-vectors/protocol-event-proof-v1.json` are normative. Any implementation must reproduce canonical UTF-8, SHA-256, signatures, Core decisions, exact Proof bytes/digests, source binding, replay/mutation rejection and external references. The suite digest is computed over both collections using the structure defined in `test-vectors/README.md`. Reference scripts are non-normative tools; vectors and this specification govern conflicts.
