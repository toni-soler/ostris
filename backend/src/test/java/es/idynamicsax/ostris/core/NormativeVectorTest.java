package es.idynamicsax.ostris.core;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;

class NormativeVectorTest {
    private static final String SUITE = "91bbeaf0bf97b7a20bdbb14f0414a995b5c4a844133b86fd422e6d05a0c828d6";
    private final ObjectMapper mapper = new ObjectMapper();
    private final OstrisWireCodec wire = new OstrisWireCodec();

    private JsonNode vectors() throws Exception {
        try (var input = getClass().getResourceAsStream("/normative/core-v0.1.json")) {
            assertNotNull(input, "Normative vectors must be packaged with tests");
            return mapper.readTree(input);
        }
    }

    @Test void canonicalizationSuiteDigestAndAuthorizationMatch() throws Exception {
        JsonNode root = vectors();
        for (JsonNode test : root.path("canonicalization")) {
            byte[] canonical = wire.canonicalize(test.path("input"));
            assertEquals(test.path("expectedCanonical").asText(), new String(canonical, StandardCharsets.UTF_8), test.path("name").asText());
            assertEquals(test.path("expectedSha256").asText(), OstrisWireCodec.sha256(canonical));
        }
        JsonNode auth = root.path("authorization");
        JsonNode payload = null;
        for (JsonNode test : root.path("canonicalization")) if (test.path("name").asText().equals(auth.path("payloadCase").asText())) payload = test.path("input");
        assertNotNull(payload);
        byte[] message = wire.authorizationMessage(payload);
        assertEquals(auth.path("expectedMessageSha256").asText(), OstrisWireCodec.sha256(message));
        assertTrue(Ed25519Authorization.verify(auth.path("publicKeyBase64Url").asText(), auth.path("expectedSignatureBase64Url").asText(), message));
        JsonNode governed = root.path("governanceAuthorization");
        JsonNode governedPayload = null;
        for (JsonNode test : root.path("canonicalization")) if (test.path("name").asText().equals(governed.path("payloadCase").asText())) governedPayload = test.path("input");
        assertNotNull(governedPayload);
        byte[] governedMessage = wire.governanceAuthorizationMessage(governedPayload);
        assertEquals(governed.path("expectedMessageSha256").asText(), OstrisWireCodec.sha256(governedMessage));
        assertTrue(Ed25519Authorization.verify(governed.path("publicKeyBase64Url").asText(), governed.path("expectedSignatureBase64Url").asText(), governedMessage));
        ((com.fasterxml.jackson.databind.node.ObjectNode) root).put("expectedSuiteSha256", "");
        assertEquals(SUITE, OstrisWireCodec.sha256(wire.canonicalize(root)));
    }

    @Test void amountUuidAccountingAndPurposeVectorsMatch() throws Exception {
        JsonNode root = vectors();
        for (JsonNode test : root.path("amounts")) {
            boolean valid;
            try { OstrisAmount.parse(test.path("value").isTextual() ? test.path("value").asText() : test.path("value").numberValue()); valid = true; }
            catch (ProtocolException ex) { valid = false; }
            assertEquals(test.path("valid").asBoolean(), valid, test.path("value").toString());
        }
        for (JsonNode test : root.path("identifiers")) {
            boolean valid;
            try { UuidV7.parse(test.path("value").asText()); valid = true; }
            catch (ProtocolException ex) { valid = false; }
            assertEquals(test.path("valid").asBoolean(), valid, test.path("value").asText());
        }
        assertEquals(List.of(TransactionPurpose.values()).stream().map(Enum::name).toList(), mapper.convertValue(root.path("transactionPurposes"), mapper.getTypeFactory().constructCollectionType(List.class, String.class)));
        UUID account = UuidV7.parse("018f6f9a-7b1c-7a2b-8c3d-4e5f60718293");
        for (JsonNode test : root.path("accounting")) {
            List<TransactionEntry> entries = new ArrayList<>();
            int index = 0;
            for (JsonNode amount : test.path("entries")) entries.add(new TransactionEntry(new UUID(account.getMostSignificantBits(), account.getLeastSignificantBits() + index++), OstrisAmount.parse(amount.asText())));
            boolean valid;
            try { TransactionValidator.validateBalanced(entries); valid = true; }
            catch (ProtocolException ex) { valid = false; }
            assertEquals(test.path("valid").asBoolean(), valid, test.path("name").asText());
        }
    }

    @Test void controllerQuorumVectorsMatch() throws Exception {
        for (JsonNode test : vectors().path("controllerQuorum")) {
            Map<String,List<JsonNode>> bindings = new HashMap<>();
            for (JsonNode binding : test.path("bindings")) bindings.computeIfAbsent(binding.get(0).asText(), ignored -> new ArrayList<>()).add(binding);
            boolean invalid = bindings.values().stream().anyMatch(rows -> rows.stream().map(row -> row.get(1).asText()).distinct().count() != 1);
            Map<String,Set<String>> keyControllers = new HashMap<>();
            for (JsonNode binding : test.path("bindings")) if (binding.get(3).asBoolean() && contains(test.path("activeControllers"),binding.get(1).asText()) && contains(test.path("policyControllers"),binding.get(1).asText())) keyControllers.computeIfAbsent(binding.get(2).asText(), ignored -> new HashSet<>()).add(binding.get(1).asText());
            invalid |= keyControllers.values().stream().anyMatch(set -> set.size() > 1);
            Set<String> controllers = new HashSet<>();
            for (JsonNode signature : test.path("signatures")) { List<JsonNode> rows=bindings.getOrDefault(signature.asText(),List.of()); if(rows.size()!=1 || !rows.getFirst().get(3).asBoolean() || !contains(test.path("activeControllers"),rows.getFirst().get(1).asText()) || !contains(test.path("policyControllers"),rows.getFirst().get(1).asText())) invalid=true; else controllers.add(rows.getFirst().get(1).asText()); }
            int contribution=invalid?0:controllers.size();
            assertEquals(test.path("expectedContribution").asInt(),contribution,test.path("name").asText());
            assertEquals(test.path("expectedValid").asBoolean(),!invalid&&contribution>=test.path("threshold").asInt(),test.path("name").asText());
        }
    }

    @Test void governedDecisionVectorsMatch() throws Exception {
        for (JsonNode test : vectors().path("governanceDecisions")) {
            TransactionPurpose purpose = TransactionPurpose.valueOf(test.path("purpose").asText());
            ResolutionBasisType basis = ResolutionBasisType.valueOf(test.path("basis").asText());
            Set<String> eligible = new HashSet<>();
            for (JsonNode signer : test.path("signers")) if (!Set.of("REVOKED", "UNKNOWN").contains(signer.asText())) eligible.add(signer.asText());
            boolean actual = GovernedAuthorizationRules.allowed(purpose, basis)
                    && test.path("final").asBoolean()
                    && "EXACT".equals(test.path("covered").asText())
                    && eligible.size() >= test.path("threshold").asInt()
                    && test.path("signatureMatches").asBoolean();
            assertEquals(test.path("expected").asBoolean(), actual, test.path("name").asText());
        }
    }

    @Test void governedEconomicPolicyVectorsMatch() throws Exception {
        for (JsonNode test : vectors().path("governedEconomicPolicy"))
            assertEquals(test.path("expected").asBoolean(), governedEconomicPolicyValid(test), test.path("name").asText());
    }

    @Test void restitutionReferenceVectorsMatch() throws Exception {
        for(JsonNode c:vectors().path("restitutionReferences")){JsonNode refs=c.path("references");String finding=refs.path("finalFindingId").isMissingNode()?null:refs.path("finalFindingId").asText();String dispute=refs.path("disputeResolutionId").isMissingNode()?null:refs.path("disputeResolutionId").asText();boolean actual;if("VOLUNTARY".equals(c.path("path").asText()))actual=c.path("accountAuthorization").asBoolean()&&!c.path("governanceAuthorization").asBoolean()&&c.path("basisType").isNull()&&c.path("basisId").isNull()&&finding==null&&dispute==null&&c.path("signatureMatches").asBoolean()&&c.path("scopeValid").asBoolean();else{actual="IMPOSED".equals(c.path("path").asText())&&!c.path("accountAuthorization").asBoolean()&&c.path("governanceAuthorization").asBoolean()&&c.path("policyAllowed").asBoolean()&&c.path("signatureMatches").asBoolean()&&c.path("scopeValid").asBoolean();if(actual)actual=switch(c.path("basisType").asText()){case "FINAL_FINDING"->Objects.equals(finding,c.path("basisId").asText())&&dispute==null;case "FINAL_DISPUTE_RESOLUTION"->Objects.equals(dispute,c.path("basisId").asText())&&finding==null;default->false;};}assertEquals(c.path("expected").asBoolean(),actual,c.path("name").asText());}
    }

    private boolean governedEconomicPolicyValid(JsonNode c) {
        TransactionPurpose purpose;
        ResolutionBasisType basis;
        try { purpose=TransactionPurpose.valueOf(c.path("purpose").asText()); basis=ResolutionBasisType.valueOf(c.path("basis").asText()); }
        catch (IllegalArgumentException ex) { return false; }
        if (!GovernedAuthorizationRules.allowed(purpose,basis)||!c.path("final").asBoolean()||!c.path("governanceValid").asBoolean()) return false;
        String special=c.path("specialAccount").asText();
        if (!"NONE".equals(special)) {
            boolean permitted=("COMMUNITY_PENALTY".equals(special)&&Set.of(TransactionPurpose.PENALTY,TransactionPurpose.LOSS_OFFSET).contains(purpose))
                    ||("COMMUNITY_LOSS".equals(special)&&Set.of(TransactionPurpose.WRITE_OFF,TransactionPurpose.LOSS_OFFSET).contains(purpose))
                    ||("BOTH".equals(special)&&purpose==TransactionPurpose.LOSS_OFFSET)
                    ||(purpose==TransactionPurpose.REVERSAL&&c.path("originalContainsSpecial").asBoolean()&&Set.of("COMMUNITY_PENALTY","COMMUNITY_LOSS").contains(special));
            if(!permitted)return false;
        }
        JsonNode config=c.path("config");
        if(purpose==TransactionPurpose.PENALTY||purpose==TransactionPurpose.RESTITUTION){Set<String> keys=Set.of("schema","penaltyAllowed","restitutionFromFinalFindingAllowed","restitutionFromFinalDisputeResolutionAllowed");if(!c.path("policyEffective").asBoolean()||!config.isObject()||!fieldNames(config).equals(keys)||!"OSTRIS-SANCTION-POLICY-1".equals(config.path("schema").asText())||keys.stream().filter(k->!k.equals("schema")).anyMatch(k->!config.path(k).isBoolean()))return false;String flag=purpose==TransactionPurpose.PENALTY?"penaltyAllowed":basis==ResolutionBasisType.FINAL_FINDING?"restitutionFromFinalFindingAllowed":"restitutionFromFinalDisputeResolutionAllowed";return config.path(flag).asBoolean();}
        if(purpose==TransactionPurpose.WRITE_OFF)return c.path("policyEffective").asBoolean()&&config.isObject()&&fieldNames(config).equals(Set.of("schema","writeOffAllowed"))&&"OSTRIS-DEFAULT-POLICY-1".equals(config.path("schema").asText())&&config.path("writeOffAllowed").isBoolean()&&config.path("writeOffAllowed").asBoolean()&&c.path("writeOffFull").asBoolean();
        return true;
    }

    private Set<String> fieldNames(JsonNode node){Set<String> names=new HashSet<>();node.fieldNames().forEachRemaining(names::add);return names;}

    private boolean contains(JsonNode array,String value){for(JsonNode node:array)if(node.asText().equals(value))return true;return false;}

    @Test void reversalCreditOrderingContinuityAndFinalityMatch() throws Exception {
        JsonNode root = vectors();
        UUID base = UuidV7.parse("018f6f9a-7b1c-7a2b-8c3d-4e5f60718293");
        for (JsonNode test : root.path("reversals")) {
            List<TransactionEntry> originalEntries = entries(base, test.path("original"));
            List<TransactionEntry> reversalEntries = entries(base, test.path("reversal"));
            JournalTransaction original = test.path("originalKnown").asBoolean() ? new JournalTransaction(1, TransactionPurpose.parse(test.path("originalPurpose").asText()), originalEntries) : null;
            boolean valid;
            try { TransactionValidator.validateFullReversal(original, reversalEntries, test.path("existingCommittedReversalCount").asLong()); valid = true; }
            catch (ProtocolException ex) { valid = false; }
            assertEquals(test.path("valid").asBoolean(), valid, test.path("name").asText());
        }
        for (JsonNode test : root.path("credit")) {
            OstrisAmount balance = OstrisAmount.parse(test.path("balance").asText());
            OstrisAmount floor = OstrisAmount.parse(test.path("floor").asText());
            OstrisAmount delta = OstrisAmount.parse(test.path("delta").asText());
            CreditPosition projected = new CreditPosition(balance.add(delta), floor);
            boolean enforced = test.path("kind").asText().startsWith("FINAL_");
            assertEquals(test.path("accepted").asBoolean(), new CreditPosition(balance, floor).permits(delta, enforced));
            assertEquals(test.path("projected").asText(), projected.balance().wire());
            assertEquals(test.path("available").asText(), projected.availableNegativeExposure().wire());
            assertEquals(test.path("enforcedLiability").asText(), projected.enforcedLiability().wire());
        }
        for (JsonNode test : root.path("ordering")) {
            boolean valid = test.path("commitSequence").asLong() < test.path("revocationSequence").asLong();
            String policy = test.path("policyV2ActivationSequence").asLong() < test.path("commitSequence").asLong() ? "v2" : "v1";
            assertEquals(test.path("expectedAuthorizationValid").asBoolean(), valid);
            assertEquals(test.path("expectedPolicy").asText(), policy);
        }
        for (JsonNode test : root.path("identityContinuity")) {
            String actual = !test.path("manualStatus").isNull() ? test.path("manualStatus").asText()
                    : (!test.path("subjectRefA").isNull() && test.path("subjectRefA").equals(test.path("subjectRefB")) ? "CONFIRMED" : "NO_MATCH");
            assertEquals(test.path("expected").asText(), actual);
        }
        for (JsonNode test : root.path("findingFinality")) {
            boolean actual = (test.path("status").asText().equals("ISSUED") && test.path("appealDeadlineElapsed").asBoolean())
                    || (test.path("status").asText().equals("APPEALED") && test.path("appealDecision").asText().equals("UPHELD"));
            assertEquals(test.path("expectedFinal").asBoolean(), actual);
        }
    }

    @Test void riskMetricsAndOutcomePrecedenceMatch() throws Exception {
        JsonNode root = vectors(); JsonNode risk = root.path("risk");
        List<JournalTransaction> journal = new ArrayList<>();
        for (JsonNode transaction : risk.path("transactions")) {
            List<TransactionEntry> entries = new ArrayList<>();
            for (JsonNode entry : transaction.path("entries")) entries.add(new TransactionEntry(UuidV7.parse(entry.path("accountId").asText()), OstrisAmount.parse(entry.path("amount").asText())));
            journal.add(new JournalTransaction(transaction.path("sequence").asLong(), TransactionPurpose.parse(transaction.path("purpose").asText()), entries));
        }
        Map<RiskMetric, BigInteger> actual = RiskMetrics.calculate(
                UuidV7.parse(risk.path("subjectAccountId").asText()), UuidV7.parse(risk.path("reciprocalCounterpartyId").asText()),
                risk.path("prospectiveSequence").asLong(), risk.path("windowSizeSequences").asLong(),
                OstrisAmount.parse(risk.path("creditFloor").asText()), OstrisAmount.parse(risk.path("projectedBalance").asText()), journal);
        for (RiskMetric metric : RiskMetric.values()) assertEquals(risk.path("expected").path(metric.name()).asText(), actual.get(metric).toString(), metric.name());
        for (JsonNode test : root.path("outcomePrecedence").path("cases")) {
            List<RiskOutcome> outcomes = new ArrayList<>(); test.path("inputs").forEach(value -> outcomes.add(RiskOutcome.valueOf(value.asText())));
            assertEquals(test.path("expected").asText(), RiskOutcome.strongest(outcomes).name());
        }
    }

    private static List<TransactionEntry> entries(UUID base, JsonNode amounts) {
        List<TransactionEntry> result = new ArrayList<>(); int index = 0;
        for (JsonNode amount : amounts) result.add(new TransactionEntry(new UUID(base.getMostSignificantBits(), base.getLeastSignificantBits() + index++), OstrisAmount.parse(amount.asText())));
        return result;
    }
}
