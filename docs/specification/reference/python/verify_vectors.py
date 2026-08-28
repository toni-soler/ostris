"""Non-production reference verifier for osTRIS Core v0.1 normative vectors."""

from __future__ import annotations

import base64
import hashlib
import json
import re
import sys
from pathlib import Path

import rfc8785
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
from cryptography.hazmat.primitives.serialization import Encoding, PublicFormat

ROOT = Path(__file__).resolve().parents[2]
VECTOR_PATH = ROOT / "test-vectors" / "core-v0.1.json"
PROOF_VECTOR_PATH = ROOT / "test-vectors" / "protocol-event-proof-v1.json"
AMOUNT_RE = re.compile(r"^(?:0|-?[1-9][0-9]{0,77})$")
UUID7_RE = re.compile(r"^[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")
PROOF_DIGEST_RE = re.compile(r"^[0-9a-f]{64}$")
SEQUENCE_RE = re.compile(r"^(?:0|[1-9][0-9]*)$")
PROOF_FIELDS = {"ostrisCoreVersion", "wireFormat", "communityId", "unitId", "transactionId", "transactionDigest", "communitySequence", "commitState"}


def b64u(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).decode("ascii").rstrip("=")


def canonical(value: object) -> bytes:
    return rfc8785.dumps(value)


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def amount_valid(value: object) -> bool:
    return isinstance(value, str) and AMOUNT_RE.fullmatch(value) is not None


def credit_result(case: dict) -> dict:
    balance, floor, delta = map(int, (case["balance"], case["floor"], case["delta"]))
    projected = balance + delta
    final = case["kind"] in {"FINAL_PENALTY", "FINAL_RESTITUTION"}
    improves = projected >= balance
    accepted = projected >= floor or final or improves
    return {
        "accepted": accepted,
        "projected": str(projected),
        "available": str(max(0, projected - floor)),
        "enforcedLiability": str(max(0, floor - projected)),
    }


def reversal_valid(case: dict) -> bool:
    return (
        case["originalKnown"]
        and case["originalPurpose"] != "REVERSAL"
        and case["existingCommittedReversalCount"] == 0
        and len(case["original"]) == len(case["reversal"])
        and all(int(a) == -int(b) for a, b in zip(case["original"], case["reversal"]))
    )


def governed_economic_policy_valid(case: dict) -> bool:
    allowed = {"PENALTY":{"FINAL_FINDING"},"RESTITUTION":{"FINAL_FINDING","FINAL_DISPUTE_RESOLUTION"},"WRITE_OFF":{"FINAL_DEFAULT_DECISION"},"LOSS_OFFSET":{"COMMUNITY_GOVERNANCE_DECISION"},"REVERSAL":{"FINAL_DISPUTE_RESOLUTION"},"SETTLEMENT":{"FINAL_DISPUTE_RESOLUTION"}}
    if case["basis"] not in allowed.get(case["purpose"], set()) or not case["final"] or not case["governanceValid"]:
        return False
    special = case["specialAccount"]
    if special != "NONE":
        permitted = (special == "COMMUNITY_PENALTY" and case["purpose"] in {"PENALTY","LOSS_OFFSET"}) or (special == "COMMUNITY_LOSS" and case["purpose"] in {"WRITE_OFF","LOSS_OFFSET"}) or (special == "BOTH" and case["purpose"] == "LOSS_OFFSET") or (case["purpose"] == "REVERSAL" and case["originalContainsSpecial"] and special in {"COMMUNITY_PENALTY","COMMUNITY_LOSS"})
        if not permitted: return False
    if case["purpose"] in {"PENALTY","RESTITUTION"}:
        config = case["config"]
        keys = {"schema","penaltyAllowed","restitutionFromFinalFindingAllowed","restitutionFromFinalDisputeResolutionAllowed"}
        if not case["policyEffective"] or not isinstance(config, dict) or set(config) != keys or config.get("schema") != "OSTRIS-SANCTION-POLICY-1" or any(type(config[k]) is not bool for k in keys - {"schema"}): return False
        flag = "penaltyAllowed" if case["purpose"] == "PENALTY" else ("restitutionFromFinalFindingAllowed" if case["basis"] == "FINAL_FINDING" else "restitutionFromFinalDisputeResolutionAllowed")
        return config[flag]
    if case["purpose"] == "WRITE_OFF":
        config = case["config"]
        return case["policyEffective"] and isinstance(config, dict) and set(config) == {"schema","writeOffAllowed"} and config.get("schema") == "OSTRIS-DEFAULT-POLICY-1" and type(config.get("writeOffAllowed")) is bool and config["writeOffAllowed"] and case["writeOffFull"]
    return True


def restitution_reference_valid(case: dict) -> bool:
    refs = case["references"]
    finding, dispute = refs.get("finalFindingId"), refs.get("disputeResolutionId")
    if case["path"] == "VOLUNTARY":
        return case["accountAuthorization"] and not case["governanceAuthorization"] and case["basisType"] is None and case["basisId"] is None and finding is None and dispute is None and case["signatureMatches"] and case["scopeValid"]
    if case["path"] != "IMPOSED" or case["accountAuthorization"] or not case["governanceAuthorization"] or not case["policyAllowed"] or not case["signatureMatches"] or not case["scopeValid"]:
        return False
    if case["basisType"] == "FINAL_FINDING": return finding == case["basisId"] and dispute is None
    if case["basisType"] == "FINAL_DISPUTE_RESOLUTION": return dispute == case["basisId"] and finding is None
    return False


def risk_metrics(risk: dict) -> dict:
    subject = risk["subjectAccountId"]
    reciprocal = risk["reciprocalCounterpartyId"]
    sequence = int(risk["prospectiveSequence"])
    lower = max(0, sequence - int(risk["windowSizeSequences"]))
    txs = [t for t in risk["transactions"] if lower <= int(t["sequence"]) < sequence and t["purpose"] == "EXCHANGE"]
    projected, floor = int(risk["projectedBalance"]), int(risk["creditFloor"])
    if projected >= 0:
        floor_utilization = 0
    elif floor == 0:
        raise ValueError("negative projected balance with zero floor")
    else:
        floor_utilization = abs(projected) * 10000 // abs(floor)

    inflows: dict[str, int] = {}
    counterparties: set[str] = set()
    recent_volume = 0
    reciprocal_in = reciprocal_out = 0
    for tx in txs:
        entries = [(e["accountId"], int(e["amount"])) for e in tx["entries"]]
        subject_entries = [amount for account, amount in entries if account == subject]
        if not subject_entries:
            continue
        subject_amount = subject_entries[0]
        if subject_amount == 0:
            continue
        recent_volume += abs(subject_amount)
        counterparties.update(account for account, amount in entries if account != subject and amount != 0)
        bilateral = len(entries) == 2 and entries[0][0] != entries[1][0] and sorted(a for _, a in entries)[0] < 0 < sorted(a for _, a in entries)[1]
        if not bilateral:
            continue
        other_account, other_amount = next((a, n) for a, n in entries if a != subject)
        if subject_amount > 0 and other_amount < 0:
            inflows[other_account] = inflows.get(other_account, 0) + subject_amount
            if other_account == reciprocal:
                reciprocal_in += subject_amount
        elif subject_amount < 0 and other_amount > 0 and other_account == reciprocal:
            reciprocal_out += -subject_amount

    total_inflow = sum(inflows.values())
    concentration = 0 if total_inflow == 0 else max(inflows.values()) * 10000 // total_inflow
    reciprocal_ratio = 0 if reciprocal_in == reciprocal_out == 0 else min(reciprocal_in, reciprocal_out) * 10000 // max(reciprocal_in, reciprocal_out)
    return {
        "FLOOR_UTILIZATION": str(floor_utilization),
        "COUNTERPARTY_CONCENTRATION": str(concentration),
        "COUNTERPARTY_COUNT": str(len(counterparties)),
        "RECENT_VOLUME": str(recent_volume),
        "RECIPROCAL_EXPOSURE": str(reciprocal_ratio),
    }


def proof_bytes(proof_vectors: dict, payload: dict) -> bytes:
    return proof_vectors["domain"].encode("ascii") + b"\x00" + canonical(payload)


def external_reference(proof_vectors: dict, community_id: str, transaction_id: str) -> str:
    material = proof_vectors["externalReferenceDomain"].encode("ascii") + b"\x00" + community_id.encode("ascii") + b"\x00" + transaction_id.encode("ascii")
    return "ostris:v1:" + sha256(material)


def proof_payload_valid(payload: object, source: dict) -> bool:
    return (
        isinstance(payload, dict) and set(payload) == PROOF_FIELDS
        and payload["ostrisCoreVersion"] == "0.1" and payload["wireFormat"] == "OSTRIS-CORE-JCS-1"
        and all(isinstance(payload[k], str) and UUID7_RE.fullmatch(payload[k]) for k in ("communityId", "unitId", "transactionId"))
        and isinstance(payload["transactionDigest"], str) and PROOF_DIGEST_RE.fullmatch(payload["transactionDigest"])
        and isinstance(payload["communitySequence"], str) and SEQUENCE_RE.fullmatch(payload["communitySequence"])
        and payload["commitState"] == "COMMITTED"
        and payload["communityId"] == source["communityId"] and payload["unitId"] == source["unitId"]
        and payload["transactionId"] == source["transactionId"] and payload["transactionDigest"] == source["protocolDigest"]
        and payload["communitySequence"] == source["communitySequence"]
    )


def derived(vectors: dict, proof_vectors: dict) -> dict:
    canonical_cases = {}
    for case in vectors["canonicalization"]:
        encoded = canonical(case["input"])
        canonical_cases[case["name"]] = {"canonical": encoded.decode("utf-8"), "sha256": sha256(encoded)}

    auth = vectors["authorization"]
    seed = bytes.fromhex("9d61b19deffd5a60ba844af492ec2cc44449c5697b326919703bac031cae7f60")
    private_key = Ed25519PrivateKey.from_private_bytes(seed)
    public_key = private_key.public_key().public_bytes(Encoding.Raw, PublicFormat.Raw)
    payload = next(c["input"] for c in vectors["canonicalization"] if c["name"] == auth["payloadCase"])
    message = auth["domain"].encode("ascii") + b"\x00" + canonical(payload)
    signature = private_key.sign(message)
    gov = vectors["governanceAuthorization"]
    gov_payload = next(c["input"] for c in vectors["canonicalization"] if c["name"] == gov["payloadCase"])
    gov_message = gov["domain"].encode("ascii") + b"\x00" + canonical(gov_payload)
    gov_signature = private_key.sign(gov_message)

    suite_copy = json.loads(json.dumps(vectors)); suite_copy["expectedSuiteSha256"] = ""
    proof_copy = json.loads(json.dumps(proof_vectors)); proof_copy["expectedSuiteSha256"] = ""
    proof_cases = {case["name"]: {"canonical": canonical(case["payload"]).decode("utf-8"), "proofDigest": sha256(proof_bytes(proof_vectors, case["payload"]))} for case in proof_vectors["valid"]}
    external_references = {case["name"]: external_reference(proof_vectors, case["communityId"], case["transactionId"]) for case in proof_vectors["externalReferences"]}
    return {
        "canonicalization": canonical_cases,
        "authorization": {
            "privateSeedBase64Url": b64u(seed),
            "publicKeyBase64Url": b64u(public_key),
            "messageSha256": sha256(message),
            "signatureBase64Url": b64u(signature),
        },
        "governanceAuthorization": {"messageSha256": sha256(gov_message), "signatureBase64Url": b64u(gov_signature)},
        "protocolEventProof": proof_cases,
        "externalReferences": external_references,
        "suiteSha256": sha256(canonical({"core": suite_copy, "protocolEventProof": proof_copy})),
    }


def verify() -> None:
    vectors = json.loads(VECTOR_PATH.read_text(encoding="utf-8"))
    proof_vectors = json.loads(PROOF_VECTOR_PATH.read_text(encoding="utf-8"))
    d = derived(vectors, proof_vectors)
    errors: list[str] = []
    for case in vectors["canonicalization"]:
        actual = d["canonicalization"][case["name"]]
        if case["expectedCanonical"] != actual["canonical"] or case["expectedSha256"] != actual["sha256"]:
            errors.append(f"canonicalization:{case['name']}")
    auth = vectors["authorization"]
    expected_auth = d["authorization"]
    for field, derived_field in (("privateSeedBase64Url", "privateSeedBase64Url"), ("publicKeyBase64Url", "publicKeyBase64Url"), ("expectedMessageSha256", "messageSha256"), ("expectedSignatureBase64Url", "signatureBase64Url")):
        if auth[field] != expected_auth[derived_field]:
            errors.append(f"authorization:{field}")
    gov = vectors["governanceAuthorization"]
    if gov["expectedMessageSha256"] != d["governanceAuthorization"]["messageSha256"] or gov["expectedSignatureBase64Url"] != d["governanceAuthorization"]["signatureBase64Url"]:
        errors.append("governanceAuthorization:crypto")
    allowed = {"PENALTY":{"FINAL_FINDING"},"RESTITUTION":{"FINAL_FINDING","FINAL_DISPUTE_RESOLUTION"},"WRITE_OFF":{"FINAL_DEFAULT_DECISION"},"LOSS_OFFSET":{"COMMUNITY_GOVERNANCE_DECISION"},"REVERSAL":{"FINAL_DISPUTE_RESOLUTION"},"SETTLEMENT":{"FINAL_DISPUTE_RESOLUTION"}}
    for case in vectors["governanceDecisions"]:
        eligible = {s for s in case["signers"] if s not in {"REVOKED","UNKNOWN"}}
        actual = case["purpose"] in allowed and case["basis"] in allowed.get(case["purpose"],set()) and case["final"] and case["covered"] == "EXACT" and len(eligible) >= case["threshold"] and case["signatureMatches"]
        if actual != case["expected"]: errors.append(f"governanceDecision:{case['name']}")
    for case in vectors["governedEconomicPolicy"]:
        if governed_economic_policy_valid(case) != case["expected"]: errors.append(f"governedEconomicPolicy:{case['name']}")
    for case in vectors["restitutionReferences"]:
        if restitution_reference_valid(case) != case["expected"]: errors.append(f"restitutionReference:{case['name']}")
    for case in vectors["controllerQuorum"]:
        by_credential: dict[str, list] = {}
        for binding in case["bindings"]: by_credential.setdefault(binding[0], []).append(binding)
        rebound = any(len({b[1] for b in bindings}) != 1 for bindings in by_credential.values())
        eligible_bindings = [b for b in case["bindings"] if b[3] and b[1] in case["activeControllers"] and b[1] in case["policyControllers"]]
        key_controllers: dict[str, set[str]] = {}
        for binding in eligible_bindings: key_controllers.setdefault(binding[2], set()).add(binding[1])
        alias = any(len(controllers) > 1 for controllers in key_controllers.values())
        invalid = rebound or alias
        controllers: set[str] = set()
        for credential in case["signatures"]:
            bindings = by_credential.get(credential, [])
            if len(bindings) != 1 or not bindings[0][3] or bindings[0][1] not in case["activeControllers"] or bindings[0][1] not in case["policyControllers"]:
                invalid = True
            else: controllers.add(bindings[0][1])
        contribution = 0 if invalid else len(controllers)
        valid = not invalid and contribution >= case["threshold"]
        if contribution != case["expectedContribution"] or valid != case["expectedValid"]: errors.append(f"controllerQuorum:{case['name']}")
    for case in vectors["amounts"]:
        if amount_valid(case["value"]) != case["valid"]:
            errors.append(f"amount:{case['value']}")
    for case in vectors["identifiers"]:
        if (UUID7_RE.fullmatch(case["value"]) is not None) != case["valid"]:
            errors.append(f"identifier:{case['value']}")
    for case in vectors["accounting"]:
        valid = len(case["entries"]) >= 2 and all(amount_valid(v) for v in case["entries"]) and sum(map(int, case["entries"])) == 0
        if valid != case["valid"]:
            errors.append(f"accounting:{case['name']}")
    if vectors["transactionPurposes"] != ["EXCHANGE", "REVERSAL", "SETTLEMENT", "WRITE_OFF", "RESTITUTION", "PENALTY", "LOSS_OFFSET"]:
        errors.append("transactionPurposes")
    for case in vectors["reversals"]:
        if reversal_valid(case) != case["valid"]:
            errors.append(f"reversal:{case['name']}")
    for case in vectors["credit"]:
        actual = credit_result(case)
        if any(actual[k] != case[k] for k in actual):
            errors.append(f"credit:{case['name']}")
    for case in vectors["ordering"]:
        valid = int(case["commitSequence"]) < int(case["revocationSequence"])
        policy = "v2" if int(case["policyV2ActivationSequence"]) < int(case["commitSequence"]) else "v1"
        if valid != case["expectedAuthorizationValid"] or policy != case["expectedPolicy"]:
            errors.append(f"ordering:{case['name']}")
    for case in vectors["identityContinuity"]:
        actual = case["manualStatus"] or ("CONFIRMED" if case["subjectRefA"] is not None and case["subjectRefA"] == case["subjectRefB"] else "NO_MATCH")
        if actual != case["expected"]:
            errors.append(f"identity:{case['name']}")
    for case in vectors["findingFinality"]:
        final = (case["status"] == "ISSUED" and case["appealDeadlineElapsed"]) or (case["status"] == "APPEALED" and case["appealDecision"] == "UPHELD")
        if final != case["expectedFinal"]:
            errors.append(f"finding:{case['name']}")
    if risk_metrics(vectors["risk"]) != vectors["risk"]["expected"]:
        errors.append("risk:metrics")
    rank = {name: index for index, name in enumerate(vectors["outcomePrecedence"]["orderedStrongestFirst"])}
    for case in vectors["outcomePrecedence"]["cases"]:
        if min(case["inputs"], key=rank.get) != case["expected"]:
            errors.append(f"outcome:{case['expected']}")
    if vectors["expectedSuiteSha256"] != d["suiteSha256"]:
        errors.append("suiteSha256")
    base = proof_vectors["valid"][0]["payload"]
    for case in proof_vectors["valid"]:
        actual = d["protocolEventProof"][case["name"]]
        if not proof_payload_valid(case["payload"], {**proof_vectors["sourceEvent"], "communitySequence": case["payload"]["communitySequence"]}) or case["expectedCanonical"] != actual["canonical"] or case["expectedProofDigest"] != actual["proofDigest"]:
            errors.append(f"protocolEventProof:{case['name']}")
    for case in proof_vectors["invalid"]:
        candidate = dict(base)
        if "remove" in case: candidate.pop(case["remove"], None)
        candidate.update(case.get("mutation", {}))
        if proof_payload_valid(candidate, proof_vectors["sourceEvent"]): errors.append(f"protocolEventProofInvalid:{case['name']}")
    replay = dict(base); replay["communityId"] = proof_vectors["replay"]["otherCommunityId"]
    if proof_payload_valid(replay, proof_vectors["sourceEvent"]) or sha256(proof_bytes(proof_vectors, replay)) == d["protocolEventProof"][proof_vectors["valid"][0]["name"]]["proofDigest"]: errors.append("protocolEventProof:replay")
    for case in proof_vectors["mutations"]:
        candidate = dict(base); candidate[case["field"]] = case["value"]
        if sha256(proof_bytes(proof_vectors, candidate)) == d["protocolEventProof"][proof_vectors["valid"][0]["name"]]["proofDigest"]: errors.append(f"protocolEventProofMutation:{case['name']}")
    if sha256(canonical(base)) == d["protocolEventProof"][proof_vectors["valid"][0]["name"]]["proofDigest"]: errors.append("protocolEventProof:domain")
    for case in proof_vectors["externalReferences"]:
        if case["expectedExternalReference"] != d["externalReferences"][case["name"]] or len(case["expectedExternalReference"]) > 255: errors.append(f"externalReference:{case['name']}")
    if len(set(d["externalReferences"].values())) != len(d["externalReferences"]): errors.append("externalReference:distinct")
    if proof_vectors["expectedSuiteSha256"] != d["suiteSha256"]: errors.append("proofSuiteSha256")
    if errors:
        raise SystemExit("FAIL " + ", ".join(errors))
    print(f"PASS python vectors={VECTOR_PATH.name}+{PROOF_VECTOR_PATH.name} suiteSha256={d['suiteSha256']}")


if __name__ == "__main__":
    vectors = json.loads(VECTOR_PATH.read_text(encoding="utf-8"))
    proof_vectors = json.loads(PROOF_VECTOR_PATH.read_text(encoding="utf-8"))
    if "--print-derived" in sys.argv:
        print(json.dumps(derived(vectors, proof_vectors), ensure_ascii=False, indent=2))
    else:
        verify()
