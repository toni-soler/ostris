// Non-production independent verifier for selected osTRIS Core v0.1 vectors.
import fs from "node:fs";
import path from "node:path";
import crypto from "node:crypto";
import { fileURLToPath } from "node:url";
import canonicalize from "canonicalize";

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, "../..");
const vectorPath = path.join(root, "test-vectors", "core-v0.1.json");
const proofVectorPath = path.join(root, "test-vectors", "protocol-event-proof-v1.json");
const vectors = JSON.parse(fs.readFileSync(vectorPath, "utf8"));
const proofVectors = JSON.parse(fs.readFileSync(proofVectorPath, "utf8"));
const amountRe = /^(?:0|-?[1-9][0-9]{0,77})$/;
const uuid7Re =
  /^[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;
const b64u = (bytes) => Buffer.from(bytes).toString("base64url");
const canonical = (value) => Buffer.from(canonicalize(value), "utf8");
const sha256 = (bytes) =>
  crypto.createHash("sha256").update(bytes).digest("hex");
const errors = [];
const proofFields = ["ostrisCoreVersion","wireFormat","communityId","unitId","transactionId","transactionDigest","communitySequence","commitState"].sort();
const digestRe = /^[0-9a-f]{64}$/;
const sequenceRe = /^(?:0|[1-9][0-9]*)$/;
const proofBytes = (payload) => Buffer.concat([Buffer.from(proofVectors.domain,"ascii"),Buffer.from([0]),canonical(payload)]);
const externalReference = (communityId,transactionId) => "ostris:v1:"+sha256(Buffer.concat([Buffer.from(proofVectors.externalReferenceDomain,"ascii"),Buffer.from([0]),Buffer.from(communityId,"ascii"),Buffer.from([0]),Buffer.from(transactionId,"ascii")]));
const proofPayloadValid = (p,source) => p!==null&&typeof p==="object"&&!Array.isArray(p)&&JSON.stringify(Object.keys(p).sort())===JSON.stringify(proofFields)&&p.ostrisCoreVersion==="0.1"&&p.wireFormat==="OSTRIS-CORE-JCS-1"&&[p.communityId,p.unitId,p.transactionId].every(v=>typeof v==="string"&&uuid7Re.test(v))&&typeof p.transactionDigest==="string"&&digestRe.test(p.transactionDigest)&&typeof p.communitySequence==="string"&&sequenceRe.test(p.communitySequence)&&p.commitState==="COMMITTED"&&p.communityId===source.communityId&&p.unitId===source.unitId&&p.transactionId===source.transactionId&&p.transactionDigest===source.protocolDigest&&p.communitySequence===source.communitySequence;

for (const c of vectors.canonicalization) {
  const bytes = canonical(c.input);
  if (
    bytes.toString("utf8") !== c.expectedCanonical ||
    sha256(bytes) !== c.expectedSha256
  )
    errors.push(`canonicalization:${c.name}`);
}

const auth = vectors.authorization;
const seed = Buffer.from(
  "9d61b19deffd5a60ba844af492ec2cc44449c5697b326919703bac031cae7f60",
  "hex",
);
const pkcs8Prefix = Buffer.from("302e020100300506032b657004220420", "hex");
const privateKey = crypto.createPrivateKey({
  key: Buffer.concat([pkcs8Prefix, seed]),
  format: "der",
  type: "pkcs8",
});
const publicDer = crypto
  .createPublicKey(privateKey)
  .export({ format: "der", type: "spki" });
const publicRaw = publicDer.subarray(-32);
const payload = vectors.canonicalization.find(
  (c) => c.name === auth.payloadCase,
).input;
const message = Buffer.concat([
  Buffer.from(auth.domain, "ascii"),
  Buffer.from([0]),
  canonical(payload),
]);
const signature = crypto.sign(null, message, privateKey);
if (b64u(seed) !== auth.privateSeedBase64Url) errors.push("authorization:seed");
if (b64u(publicRaw) !== auth.publicKeyBase64Url)
  errors.push("authorization:publicKey");
if (sha256(message) !== auth.expectedMessageSha256)
  errors.push("authorization:digest");
if (
  b64u(signature) !== auth.expectedSignatureBase64Url ||
  !crypto.verify(null, message, crypto.createPublicKey(privateKey), signature)
)
  errors.push("authorization:signature");

const gov = vectors.governanceAuthorization;
const govPayload = vectors.canonicalization.find((c) => c.name === gov.payloadCase).input;
const govMessage = Buffer.concat([Buffer.from(gov.domain,"ascii"),Buffer.from([0]),canonical(govPayload)]);
const govSignature = crypto.sign(null,govMessage,privateKey);
if(sha256(govMessage)!==gov.expectedMessageSha256||b64u(govSignature)!==gov.expectedSignatureBase64Url||!crypto.verify(null,govMessage,crypto.createPublicKey(privateKey),govSignature))errors.push("governanceAuthorization:crypto");
const governedAllowed={PENALTY:new Set(["FINAL_FINDING"]),RESTITUTION:new Set(["FINAL_FINDING","FINAL_DISPUTE_RESOLUTION"]),WRITE_OFF:new Set(["FINAL_DEFAULT_DECISION"]),LOSS_OFFSET:new Set(["COMMUNITY_GOVERNANCE_DECISION"]),REVERSAL:new Set(["FINAL_DISPUTE_RESOLUTION"]),SETTLEMENT:new Set(["FINAL_DISPUTE_RESOLUTION"])};
for(const c of vectors.governanceDecisions){const eligible=new Set(c.signers.filter(s=>s!=="REVOKED"&&s!=="UNKNOWN"));const actual=!!governedAllowed[c.purpose]?.has(c.basis)&&c.final&&c.covered==="EXACT"&&eligible.size>=c.threshold&&c.signatureMatches;if(actual!==c.expected)errors.push(`governanceDecision:${c.name}`);}
const exactKeys=(o,keys)=>o!==null&&typeof o==="object"&&!Array.isArray(o)&&JSON.stringify(Object.keys(o).sort())===JSON.stringify([...keys].sort());
function governedEconomicPolicyValid(c){if(!governedAllowed[c.purpose]?.has(c.basis)||!c.final||!c.governanceValid)return false;const s=c.specialAccount;if(s!=="NONE"){const permitted=(s==="COMMUNITY_PENALTY"&&["PENALTY","LOSS_OFFSET"].includes(c.purpose))||(s==="COMMUNITY_LOSS"&&["WRITE_OFF","LOSS_OFFSET"].includes(c.purpose))||(s==="BOTH"&&c.purpose==="LOSS_OFFSET")||(c.purpose==="REVERSAL"&&c.originalContainsSpecial&&["COMMUNITY_PENALTY","COMMUNITY_LOSS"].includes(s));if(!permitted)return false;}if(["PENALTY","RESTITUTION"].includes(c.purpose)){const k=["schema","penaltyAllowed","restitutionFromFinalFindingAllowed","restitutionFromFinalDisputeResolutionAllowed"],x=c.config;if(!c.policyEffective||!exactKeys(x,k)||x.schema!=="OSTRIS-SANCTION-POLICY-1"||k.slice(1).some(v=>typeof x[v]!=="boolean"))return false;const flag=c.purpose==="PENALTY"?"penaltyAllowed":c.basis==="FINAL_FINDING"?"restitutionFromFinalFindingAllowed":"restitutionFromFinalDisputeResolutionAllowed";return x[flag];}if(c.purpose==="WRITE_OFF"){const x=c.config;return c.policyEffective&&exactKeys(x,["schema","writeOffAllowed"])&&x.schema==="OSTRIS-DEFAULT-POLICY-1"&&typeof x.writeOffAllowed==="boolean"&&x.writeOffAllowed&&c.writeOffFull;}return true;}
for(const c of vectors.governedEconomicPolicy)if(governedEconomicPolicyValid(c)!==c.expected)errors.push(`governedEconomicPolicy:${c.name}`);
function restitutionReferenceValid(c){const f=c.references.finalFindingId,d=c.references.disputeResolutionId;if(c.path==="VOLUNTARY")return c.accountAuthorization&&!c.governanceAuthorization&&c.basisType===null&&c.basisId===null&&f===undefined&&d===undefined&&c.signatureMatches&&c.scopeValid;if(c.path!=="IMPOSED"||c.accountAuthorization||!c.governanceAuthorization||!c.policyAllowed||!c.signatureMatches||!c.scopeValid)return false;if(c.basisType==="FINAL_FINDING")return f===c.basisId&&d===undefined;if(c.basisType==="FINAL_DISPUTE_RESOLUTION")return d===c.basisId&&f===undefined;return false;}
for(const c of vectors.restitutionReferences)if(restitutionReferenceValid(c)!==c.expected)errors.push(`restitutionReference:${c.name}`);
for(const c of vectors.controllerQuorum){const byCredential=new Map();for(const b of c.bindings){if(!byCredential.has(b[0]))byCredential.set(b[0],[]);byCredential.get(b[0]).push(b);}let invalid=[...byCredential.values()].some(bs=>new Set(bs.map(b=>b[1])).size!==1);const keyControllers=new Map();for(const b of c.bindings.filter(b=>b[3]&&c.activeControllers.includes(b[1])&&c.policyControllers.includes(b[1]))){if(!keyControllers.has(b[2]))keyControllers.set(b[2],new Set());keyControllers.get(b[2]).add(b[1]);}invalid ||= [...keyControllers.values()].some(s=>s.size>1);const controllers=new Set();for(const credential of c.signatures){const bs=byCredential.get(credential)||[];if(bs.length!==1||!bs[0][3]||!c.activeControllers.includes(bs[0][1])||!c.policyControllers.includes(bs[0][1]))invalid=true;else controllers.add(bs[0][1]);}const contribution=invalid?0:controllers.size;const valid=!invalid&&contribution>=c.threshold;if(contribution!==c.expectedContribution||valid!==c.expectedValid)errors.push(`controllerQuorum:${c.name}`);}

for (const c of vectors.amounts) {
  const valid = typeof c.value === "string" && amountRe.test(c.value);
  if (valid !== c.valid) errors.push(`amount:${c.value}`);
}
for (const c of vectors.identifiers)
  if (uuid7Re.test(c.value) !== c.valid) errors.push(`identifier:${c.value}`);
for (const c of vectors.accounting) {
  const valid =
    c.entries.length >= 2 &&
    c.entries.every((v) => amountRe.test(v)) &&
    c.entries.reduce((a, v) => a + BigInt(v), 0n) === 0n;
  if (valid !== c.valid) errors.push(`accounting:${c.name}`);
}
if (
  JSON.stringify(vectors.transactionPurposes) !==
  JSON.stringify([
    "EXCHANGE",
    "REVERSAL",
    "SETTLEMENT",
    "WRITE_OFF",
    "RESTITUTION",
    "PENALTY",
    "LOSS_OFFSET",
  ])
)
  errors.push("transactionPurposes");
for (const c of vectors.reversals) {
  const valid =
    c.originalKnown &&
    c.originalPurpose !== "REVERSAL" &&
    c.existingCommittedReversalCount === 0 &&
    c.original.length === c.reversal.length &&
    c.original.every((v, i) => BigInt(v) === -BigInt(c.reversal[i]));
  if (valid !== c.valid) errors.push(`reversal:${c.name}`);
}
for (const c of vectors.credit) {
  const balance = BigInt(c.balance),
    floor = BigInt(c.floor),
    projected = balance + BigInt(c.delta);
  const final = c.kind === "FINAL_PENALTY" || c.kind === "FINAL_RESTITUTION";
  const actual = {
    accepted: projected >= floor || final || projected >= balance,
    projected: String(projected),
    available: String(projected > floor ? projected - floor : 0n),
    enforcedLiability: String(floor > projected ? floor - projected : 0n),
  };
  if (Object.entries(actual).some(([k, v]) => c[k] !== v))
    errors.push(`credit:${c.name}`);
}
for (const c of vectors.ordering) {
  const valid = BigInt(c.commitSequence) < BigInt(c.revocationSequence);
  const policy =
    BigInt(c.policyV2ActivationSequence) < BigInt(c.commitSequence)
      ? "v2"
      : "v1";
  if (valid !== c.expectedAuthorizationValid || policy !== c.expectedPolicy)
    errors.push(`ordering:${c.name}`);
}
for (const c of vectors.identityContinuity) {
  const actual =
    c.manualStatus ||
    (c.subjectRefA !== null && c.subjectRefA === c.subjectRefB
      ? "CONFIRMED"
      : "NO_MATCH");
  if (actual !== c.expected) errors.push(`identity:${c.name}`);
}
for (const c of vectors.findingFinality) {
  const final =
    (c.status === "ISSUED" && c.appealDeadlineElapsed) ||
    (c.status === "APPEALED" && c.appealDecision === "UPHELD");
  if (final !== c.expectedFinal) errors.push(`finding:${c.name}`);
}

function riskMetrics(risk) {
  const S = BigInt(risk.prospectiveSequence),
    lower =
      S > BigInt(risk.windowSizeSequences)
        ? S - BigInt(risk.windowSizeSequences)
        : 0n;
  const txs = risk.transactions.filter(
    (t) =>
      t.purpose === "EXCHANGE" &&
      BigInt(t.sequence) >= lower &&
      BigInt(t.sequence) < S,
  );
  const inflows = new Map(),
    counterparties = new Set();
  let recent = 0n,
    inbound = 0n,
    outbound = 0n;
  for (const tx of txs) {
    const mine = tx.entries.find((e) => e.accountId === risk.subjectAccountId);
    if (!mine || BigInt(mine.amount) === 0n) continue;
    const mineAmount = BigInt(mine.amount);
    recent += mineAmount < 0n ? -mineAmount : mineAmount;
    for (const e of tx.entries)
      if (e.accountId !== risk.subjectAccountId && BigInt(e.amount) !== 0n)
        counterparties.add(e.accountId);
    if (tx.entries.length !== 2) continue;
    const other = tx.entries.find((e) => e.accountId !== risk.subjectAccountId);
    const otherAmount = BigInt(other.amount);
    if (mineAmount > 0n && otherAmount < 0n) {
      inflows.set(
        other.accountId,
        (inflows.get(other.accountId) || 0n) + mineAmount,
      );
      if (other.accountId === risk.reciprocalCounterpartyId)
        inbound += mineAmount;
    } else if (
      mineAmount < 0n &&
      otherAmount > 0n &&
      other.accountId === risk.reciprocalCounterpartyId
    )
      outbound += -mineAmount;
  }
  const total = [...inflows.values()].reduce((a, v) => a + v, 0n);
  const maxIn = inflows.size
    ? [...inflows.values()].reduce((a, v) => (v > a ? v : a), 0n)
    : 0n;
  const projected = BigInt(risk.projectedBalance),
    floor = BigInt(risk.creditFloor);
  const util = projected >= 0n ? 0n : (-projected * 10000n) / -floor;
  const reciprocal =
    inbound === 0n && outbound === 0n
      ? 0n
      : ((inbound < outbound ? inbound : outbound) * 10000n) /
        (inbound > outbound ? inbound : outbound);
  return {
    FLOOR_UTILIZATION: String(util),
    COUNTERPARTY_CONCENTRATION: String(
      total === 0n ? 0n : (maxIn * 10000n) / total,
    ),
    COUNTERPARTY_COUNT: String(counterparties.size),
    RECENT_VOLUME: String(recent),
    RECIPROCAL_EXPOSURE: String(reciprocal),
  };
}
if (
  JSON.stringify(riskMetrics(vectors.risk)) !==
  JSON.stringify(vectors.risk.expected)
)
  errors.push("risk:metrics");

const rank = new Map(
  vectors.outcomePrecedence.orderedStrongestFirst.map((v, i) => [v, i]),
);
for (const c of vectors.outcomePrecedence.cases) {
  const actual = [...c.inputs].sort((a, b) => rank.get(a) - rank.get(b))[0];
  if (actual !== c.expected) errors.push(`outcome:${c.expected}`);
}

const suiteCopy = structuredClone(vectors);
suiteCopy.expectedSuiteSha256 = "";
const proofSuiteCopy = structuredClone(proofVectors);
proofSuiteCopy.expectedSuiteSha256 = "";
const suiteSha = sha256(canonical({core:suiteCopy,protocolEventProof:proofSuiteCopy}));
if (suiteSha !== vectors.expectedSuiteSha256) errors.push("suiteSha256");
const proofDerived = new Map();
for(const c of proofVectors.valid){const actual={canonical:canonical(c.payload).toString("utf8"),proofDigest:sha256(proofBytes(c.payload))};proofDerived.set(c.name,actual);const source={...proofVectors.sourceEvent,communitySequence:c.payload.communitySequence};if(!proofPayloadValid(c.payload,source)||c.expectedCanonical!==actual.canonical||c.expectedProofDigest!==actual.proofDigest)errors.push(`protocolEventProof:${c.name}`);}
const base=proofVectors.valid[0].payload;
for(const c of proofVectors.invalid){const candidate={...base,...(c.mutation||{})};if(c.remove)delete candidate[c.remove];if(proofPayloadValid(candidate,proofVectors.sourceEvent))errors.push(`protocolEventProofInvalid:${c.name}`);}
const replay={...base,communityId:proofVectors.replay.otherCommunityId};if(proofPayloadValid(replay,proofVectors.sourceEvent)||sha256(proofBytes(replay))===proofDerived.get(proofVectors.valid[0].name).proofDigest)errors.push("protocolEventProof:replay");
for(const c of proofVectors.mutations){const candidate={...base,[c.field]:c.value};if(sha256(proofBytes(candidate))===proofDerived.get(proofVectors.valid[0].name).proofDigest)errors.push(`protocolEventProofMutation:${c.name}`);}
if(sha256(canonical(base))===proofDerived.get(proofVectors.valid[0].name).proofDigest)errors.push("protocolEventProof:domain");
const refs=proofVectors.externalReferences.map(c=>{const actual=externalReference(c.communityId,c.transactionId);if(actual!==c.expectedExternalReference||actual.length>255)errors.push(`externalReference:${c.name}`);return actual;});if(new Set(refs).size!==refs.length)errors.push("externalReference:distinct");
if(suiteSha!==proofVectors.expectedSuiteSha256)errors.push("proofSuiteSha256");
if (errors.length) throw new Error(`FAIL ${errors.join(", ")}`);
console.log(
  `PASS node vectors=${path.basename(vectorPath)}+${path.basename(proofVectorPath)} suiteSha256=${suiteSha}`,
);
