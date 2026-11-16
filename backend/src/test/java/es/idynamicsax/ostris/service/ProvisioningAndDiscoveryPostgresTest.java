package es.idynamicsax.ostris.service;

import static org.junit.jupiter.api.Assertions.*;

import es.idynamicsax.ostris.config.OstrisFlywayConfig;
import es.idynamicsax.ostris.core.*;
import java.math.BigInteger;
import java.security.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.*;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

/**
 * Proves the new public provisioning/discovery surface end to end: an external application (like
 * STIR) can create a Community/Unit, activate two participants (each generating their OWN Ed25519
 * keypair - this test simulates the client, never osTRIS holding a private key), discover the
 * resulting account/credential/balance state, propose+sign+commit a real EXCHANGE using exactly
 * those activated credentials, and read the transaction status/receipt back. Also proves discovery
 * fails closed on unknown IDs and provisioning fails closed on bad input/conflicts/credit floor.
 *
 * <p>Plus the adversarial controller-ownership suite: a caller that reaches this authoritative
 * service directly (bypassing STIR) must not be able to add a credential to, or revoke a device
 * from, a controller that is not theirs - no matter what tenant-wide permission or superuser status
 * they hold. See V6__controller_ownership.sql / ostris-doc CONTROLLER_OWNERSHIP.md.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = ProvisioningAndDiscoveryPostgresTest.TestApp.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@org.springframework.transaction.annotation.Transactional
class ProvisioningAndDiscoveryPostgresTest {
    @SpringBootConfiguration @EnableAutoConfiguration @EntityScan("es.idynamicsax.ostris.persistence") @EnableJpaRepositories("es.idynamicsax.ostris.persistence")
    @Import({OstrisFlywayConfig.class, es.idynamicsax.ostris.security.OperationApprovalGate.class, ProvisioningService.class, DiscoveryService.class, ProposalAuthorizationService.class, JournalCommitService.class, OutboxProtocolEventProofService.class})
    static class TestApp { @Bean com.fasterxml.jackson.databind.ObjectMapper objectMapper() { return new com.fasterxml.jackson.databind.ObjectMapper(); } }

    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17").withDatabaseName("ostris").withUsername("postgres").withPassword("test").withInitScript("postgres-init.sql");
    @DynamicPropertySource static void properties(org.springframework.test.context.DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl); r.add("spring.datasource.username", POSTGRES::getUsername); r.add("spring.datasource.password", POSTGRES::getPassword);
        r.add("spring.jpa.hibernate.ddl-auto", () -> "none"); r.add("spring.flyway.enabled", () -> "false");
    }

    @Autowired ProvisioningService provisioning;
    @Autowired DiscoveryService discovery;
    @Autowired ProposalAuthorizationService transactions;
    @Autowired JdbcTemplate jdbc;
    @jakarta.persistence.PersistenceContext jakarta.persistence.EntityManager entityManager;
    static final UUID TENANT = UUID.randomUUID();

    @BeforeEach void tenant() { jdbc.update("insert into idax_core.tenant values(?) on conflict do nothing", TENANT); }

    record Party(UUID participantId, UUID accountId, UUID controllerId, UUID credentialId, UUID ownerUserId, KeyPair key) {}

    Party activate(UUID community, UUID unit, String name, BigInteger creditFloor) throws Exception {
        return activate(community, unit, name, creditFloor, UUID.randomUUID());
    }
    Party activate(UUID community, UUID unit, String name, BigInteger creditFloor, UUID ownerUserId) throws Exception {
        KeyPair key = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        var result = provisioning.activateParticipant(TENANT, community, name, unit, rawPublic(key), creditFloor, ownerUserId);
        return new Party(result.participantId(), result.accountId(), result.controllerId(), result.credentialId(), ownerUserId, key);
    }

    @Test void communityUnitAndParticipantActivationProvisionAWorkingSignableAccount() throws Exception {
        var community = provisioning.createCommunity(TENANT, "Test Community");
        var unit = provisioning.createUnit(TENANT, community.communityId(), "TST", 2);
        assertEquals("TST", unit.code());

        Party ana = activate(community.communityId(), unit.unitId(), "Ana", null);
        Party pedro = activate(community.communityId(), unit.unitId(), "Pedro", null);
        assertNotEquals(ana.accountId(), pedro.accountId());

        var foundCommunity = discovery.community(TENANT, community.communityId());
        assertEquals("Test Community", foundCommunity.name());
        var foundUnit = discovery.unit(TENANT, community.communityId(), unit.unitId());
        assertEquals(2, foundUnit.scale());
        var anaAccount = discovery.account(TENANT, community.communityId(), ana.accountId());
        assertEquals("0", anaAccount.balanceProjection());
        assertEquals("-100000", anaAccount.creditFloor());
        var anaCredential = discovery.credential(TENANT, community.communityId(), ana.credentialId());
        assertEquals(ana.controllerId(), anaCredential.controllerId());
        assertEquals("Ed25519", anaCredential.algorithm());

        UUID tx = UUID.randomUUID();
        transactions.propose(new CommitTransactionCommand(TENANT, community.communityId(), unit.unitId(), tx, TransactionPurpose.EXCHANGE,
            List.of(new CommitTransactionCommand.Entry(pedro.accountId(), "-900"), new CommitTransactionCommand.Entry(ana.accountId(), "900")), Map.of(), null));

        var beforeCommit = discovery.transaction(TENANT, tx);
        assertEquals("PROPOSED", beforeCommit.status());
        assertNotNull(beforeCommit.authorizationPayload());
        assertTrue(beforeCommit.authorizedAccountIds().isEmpty());

        OstrisWireCodec wire = new OstrisWireCodec();
        byte[] message = wire.authorizationMessage(wire.parse(beforeCommit.authorizationPayload()));
        transactions.addAccountEvidence(TENANT, tx, pedro.accountId(), pedro.credentialId(), sign(message, pedro.key()));

        var afterOneSignature = discovery.transaction(TENANT, tx);
        assertEquals(1, afterOneSignature.authorizedAccountIds().size());

        transactions.addAccountEvidence(TENANT, tx, ana.accountId(), ana.credentialId(), sign(message, ana.key()));

        CommitReceipt receipt = transactions.commit(TENANT, tx);
        assertEquals(1L, receipt.communitySequence());
        entityManager.flush();

        var committed = discovery.transaction(TENANT, tx);
        assertEquals("COMMITTED", committed.status());
        assertEquals(receipt.protocolDigest(), committed.protocolDigest());
        assertEquals(1L, committed.committedSequence());

        assertEquals("900", discovery.account(TENANT, community.communityId(), ana.accountId()).balanceProjection());
        assertEquals("-900", discovery.account(TENANT, community.communityId(), pedro.accountId()).balanceProjection());
    }

    @Test void exchangeBeyondCreditFloorFailsClosedAndLeavesBalanceUnchanged() throws Exception {
        var community = provisioning.createCommunity(TENANT, "Floor Community");
        var unit = provisioning.createUnit(TENANT, community.communityId(), "FLR", 2);
        Party ana = activate(community.communityId(), unit.unitId(), "Ana", new BigInteger("-500"));
        Party pedro = activate(community.communityId(), unit.unitId(), "Pedro", new BigInteger("-500"));

        UUID tx = UUID.randomUUID();
        transactions.propose(new CommitTransactionCommand(TENANT, community.communityId(), unit.unitId(), tx, TransactionPurpose.EXCHANGE,
            List.of(new CommitTransactionCommand.Entry(pedro.accountId(), "-900"), new CommitTransactionCommand.Entry(ana.accountId(), "900")), Map.of(), null));
        var status = discovery.transaction(TENANT, tx);
        OstrisWireCodec wire = new OstrisWireCodec();
        byte[] message = wire.authorizationMessage(wire.parse(status.authorizationPayload()));
        transactions.addAccountEvidence(TENANT, tx, pedro.accountId(), pedro.credentialId(), sign(message, pedro.key()));
        transactions.addAccountEvidence(TENANT, tx, ana.accountId(), ana.credentialId(), sign(message, ana.key()));

        ProtocolException error = assertThrows(ProtocolException.class, () -> transactions.commit(TENANT, tx));
        assertTrue(error.code().contains("FLOOR") || error.code().contains("RISK"), error.code());

        assertEquals("PROPOSED", discovery.transaction(TENANT, tx).status());
        assertEquals("0", discovery.account(TENANT, community.communityId(), ana.accountId()).balanceProjection());
        assertEquals("0", discovery.account(TENANT, community.communityId(), pedro.accountId()).balanceProjection());
        assertEquals(0, jdbc.queryForObject("select count(*) from ostris.journal_transaction where id=?", Integer.class, tx));
    }

    @Test void discoveryFailsClosedOnUnknownIds() {
        assertThrows(ProtocolException.class, () -> discovery.community(TENANT, UUID.randomUUID()));
        assertThrows(ProtocolException.class, () -> discovery.unit(TENANT, UUID.randomUUID(), UUID.randomUUID()));
        assertThrows(ProtocolException.class, () -> discovery.account(TENANT, UUID.randomUUID(), UUID.randomUUID()));
        assertThrows(ProtocolException.class, () -> discovery.credential(TENANT, UUID.randomUUID(), UUID.randomUUID()));
        assertThrows(ProtocolException.class, () -> discovery.transaction(TENANT, UUID.randomUUID()));
    }

    @Test void discoveryIsTenantScoped() {
        var community = provisioning.createCommunity(TENANT, "Scoped");
        UUID otherTenant = UUID.randomUUID();
        jdbc.update("insert into idax_core.tenant values(?) on conflict do nothing", otherTenant);
        assertThrows(ProtocolException.class, () -> discovery.community(otherTenant, community.communityId()));
        assertTrue(discovery.communities(otherTenant).stream().noneMatch(c -> c.communityId().equals(community.communityId())));
    }

    @Test void provisioningRejectsUnknownCommunityOrUnitAndDuplicateUnitCodeOrCredentialKey() throws Exception {
        assertThrows(ProtocolException.class, () -> provisioning.createUnit(TENANT, UUID.randomUUID(), "XX", 2));
        var community = provisioning.createCommunity(TENANT, "Dup");
        var unit = provisioning.createUnit(TENANT, community.communityId(), "DUP", 2);
        assertProtocolExceptionAfterConstraintViolation(() -> provisioning.createUnit(TENANT, community.communityId(), "DUP", 4));
        assertThrows(ProtocolException.class, () -> provisioning.activateParticipant(TENANT, UUID.randomUUID(), "X", unit.unitId(), rawPublic(KeyPairGenerator.getInstance("Ed25519").generateKeyPair()), null, UUID.randomUUID()));
        assertThrows(ProtocolException.class, () -> provisioning.activateParticipant(TENANT, community.communityId(), "X", UUID.randomUUID(), rawPublic(KeyPairGenerator.getInstance("Ed25519").generateKeyPair()), null, UUID.randomUUID()));

        KeyPair key = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        provisioning.activateParticipant(TENANT, community.communityId(), "First", unit.unitId(), rawPublic(key), null, UUID.randomUUID());
        assertProtocolExceptionAfterConstraintViolation(() -> provisioning.activateParticipant(TENANT, community.communityId(), "Second", unit.unitId(), rawPublic(key), null, UUID.randomUUID()));
    }

    private void assertProtocolExceptionAfterConstraintViolation(org.junit.jupiter.api.function.Executable action) {
        jdbc.execute("savepoint expected_failure");
        try { assertThrows(ProtocolException.class, action); }
        finally { jdbc.execute("rollback to savepoint expected_failure"); jdbc.execute("release savepoint expected_failure"); }
    }

    @Test void provisioningRejectsInvalidUnitCodeCreditFloorAndPublicKey() throws Exception {
        var community = provisioning.createCommunity(TENANT, "Bad Input");
        assertThrows(ProtocolException.class, () -> provisioning.createUnit(TENANT, community.communityId(), "lowercase", 2));
        var unit = provisioning.createUnit(TENANT, community.communityId(), "OK", 2);
        assertThrows(ProtocolException.class, () -> provisioning.activateParticipant(TENANT, community.communityId(), "X", unit.unitId(), "not-a-valid-key", null, UUID.randomUUID()));
        assertThrows(ProtocolException.class, () -> provisioning.activateParticipant(TENANT, community.communityId(), "X", unit.unitId(),
            rawPublic(KeyPairGenerator.getInstance("Ed25519").generateKeyPair()), new BigInteger("100"), UUID.randomUUID()));
    }

    @Test void deviceLifecycleAddsASecondCredentialToTheSameControllerAndRevocationDisablesOnlyThatOne() throws Exception {
        var community = provisioning.createCommunity(TENANT, "Devices");
        var unit = provisioning.createUnit(TENANT, community.communityId(), "DEV", 2);
        Party ana = activate(community.communityId(), unit.unitId(), "Ana", null);
        Party pedro = activate(community.communityId(), unit.unitId(), "Pedro", null);

        KeyPair phoneKey = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        var phoneCredential = provisioning.addCredential(TENANT, community.communityId(), ana.controllerId(), rawPublic(phoneKey), ana.ownerUserId(), null);
        assertEquals(ana.controllerId(), phoneCredential.controllerId());

        var credentials = discovery.credentialsForController(TENANT, community.communityId(), ana.controllerId());
        assertEquals(2, credentials.size());
        assertTrue(credentials.stream().anyMatch(c -> c.credentialId().equals(ana.credentialId())));
        assertTrue(credentials.stream().anyMatch(c -> c.credentialId().equals(phoneCredential.credentialId())));

        OstrisWireCodec wire = new OstrisWireCodec();

        UUID tx = UUID.randomUUID();
        transactions.propose(new CommitTransactionCommand(TENANT, community.communityId(), unit.unitId(), tx, TransactionPurpose.EXCHANGE,
            List.of(new CommitTransactionCommand.Entry(pedro.accountId(), "-100"), new CommitTransactionCommand.Entry(ana.accountId(), "100")), Map.of(), null));
        byte[] message = wire.authorizationMessage(wire.parse(discovery.transaction(TENANT, tx).authorizationPayload()));
        transactions.addAccountEvidence(TENANT, tx, pedro.accountId(), pedro.credentialId(), sign(message, pedro.key()));
        transactions.addAccountEvidence(TENANT, tx, ana.accountId(), phoneCredential.credentialId(), sign(message, phoneKey));
        CommitReceipt receipt = transactions.commit(TENANT, tx);
        assertEquals(1L, receipt.communitySequence());
        entityManager.flush();

        provisioning.revokeCredential(TENANT, community.communityId(), phoneCredential.credentialId(), ana.ownerUserId(), null);
        UUID tx2 = UUID.randomUUID();
        transactions.propose(new CommitTransactionCommand(TENANT, community.communityId(), unit.unitId(), tx2, TransactionPurpose.EXCHANGE,
            List.of(new CommitTransactionCommand.Entry(pedro.accountId(), "-50"), new CommitTransactionCommand.Entry(ana.accountId(), "50")), Map.of(), null));
        byte[] message2 = wire.authorizationMessage(wire.parse(discovery.transaction(TENANT, tx2).authorizationPayload()));
        transactions.addAccountEvidence(TENANT, tx2, pedro.accountId(), pedro.credentialId(), sign(message2, pedro.key()));
        transactions.addAccountEvidence(TENANT, tx2, ana.accountId(), phoneCredential.credentialId(), sign(message2, phoneKey));
        ProtocolException revokedError = assertThrows(ProtocolException.class, () -> transactions.commit(TENANT, tx2));
        assertEquals("CREDENTIAL_NOT_ELIGIBLE", revokedError.code());

        UUID tx3 = UUID.randomUUID();
        transactions.propose(new CommitTransactionCommand(TENANT, community.communityId(), unit.unitId(), tx3, TransactionPurpose.EXCHANGE,
            List.of(new CommitTransactionCommand.Entry(pedro.accountId(), "-50"), new CommitTransactionCommand.Entry(ana.accountId(), "50")), Map.of(), null));
        byte[] message3 = wire.authorizationMessage(wire.parse(discovery.transaction(TENANT, tx3).authorizationPayload()));
        transactions.addAccountEvidence(TENANT, tx3, pedro.accountId(), pedro.credentialId(), sign(message3, pedro.key()));
        transactions.addAccountEvidence(TENANT, tx3, ana.accountId(), ana.credentialId(), sign(message3, ana.key()));
        CommitReceipt receipt2 = transactions.commit(TENANT, tx3);
        assertEquals(2L, receipt2.communitySequence());
    }

    @Test void deviceCredentialManagementRejectsUnknownControllerDuplicateKeyAndDoubleRevoke() throws Exception {
        var community = provisioning.createCommunity(TENANT, "Devices Bad Input");
        var unit = provisioning.createUnit(TENANT, community.communityId(), "DVB", 2);
        Party ana = activate(community.communityId(), unit.unitId(), "Ana", null);

        assertThrows(ProtocolException.class, () -> provisioning.addCredential(TENANT, UUID.randomUUID(), ana.controllerId(),
            rawPublic(KeyPairGenerator.getInstance("Ed25519").generateKeyPair()), ana.ownerUserId(), null));
        assertThrows(ProtocolException.class, () -> provisioning.addCredential(TENANT, community.communityId(), UUID.randomUUID(),
            rawPublic(KeyPairGenerator.getInstance("Ed25519").generateKeyPair()), ana.ownerUserId(), null));
        assertProtocolExceptionAfterConstraintViolation(() -> provisioning.addCredential(TENANT, community.communityId(), ana.controllerId(), rawPublic(ana.key()), ana.ownerUserId(), null));

        provisioning.revokeCredential(TENANT, community.communityId(), ana.credentialId(), ana.ownerUserId(), null);
        assertThrows(ProtocolException.class, () -> provisioning.revokeCredential(TENANT, community.communityId(), ana.credentialId(), ana.ownerUserId(), null));
        assertThrows(ProtocolException.class, () -> provisioning.revokeCredential(TENANT, community.communityId(), UUID.randomUUID(), ana.ownerUserId(), null));
    }

    // --- Adversarial controller-ownership suite (the finding this migration closes) ---

    @Test void activationRecordsOwnerAndProvenance() throws Exception {
        var community = provisioning.createCommunity(TENANT, "Owner Provenance");
        var unit = provisioning.createUnit(TENANT, community.communityId(), "OWN", 2);
        UUID ana = UUID.randomUUID();
        Party party = activate(community.communityId(), unit.unitId(), "Ana", null, ana);
        var row = jdbc.queryForMap("select owner_user_id,owner_established_by,owner_established_sequence from ostris.protocol_controller where id=?", party.controllerId());
        assertEquals(ana, row.get("owner_user_id"));
        assertEquals("ACTIVATION", row.get("owner_established_by"));
        assertEquals(0L, ((Number) row.get("owner_established_sequence")).longValue());
    }

    @Test void activationRequiresAnAuthenticatedUser() throws Exception {
        var community = provisioning.createCommunity(TENANT, "No User");
        var unit = provisioning.createUnit(TENANT, community.communityId(), "NOU", 2);
        ProtocolException error = assertThrows(ProtocolException.class, () -> provisioning.activateParticipant(
            TENANT, community.communityId(), "Ana", unit.unitId(), rawPublic(KeyPairGenerator.getInstance("Ed25519").generateKeyPair()), null, null));
        assertEquals("USER_REQUIRED", error.code());
    }

    @Test void aNonOwnerCannotAddACredentialToAnotherUsersController() throws Exception {
        var community = provisioning.createCommunity(TENANT, "Takeover Add");
        var unit = provisioning.createUnit(TENANT, community.communityId(), "TKA", 2);
        Party ana = activate(community.communityId(), unit.unitId(), "Ana", null);
        UUID pedro = UUID.randomUUID(); // a different authenticated user, with OSTRIS_CREDENTIAL_MANAGE at the HTTP layer
        KeyPair pedroKey = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();

        ProtocolException error = assertThrows(ProtocolException.class,
            () -> provisioning.addCredential(TENANT, community.communityId(), ana.controllerId(), rawPublic(pedroKey), pedro, null));
        assertEquals("CONTROLLER_NOT_OWNED", error.code());
        // Nothing was bound: Ana's controller still has exactly her genesis credential.
        assertEquals(1, discovery.credentialsForController(TENANT, community.communityId(), ana.controllerId()).size());
    }

    @Test void aNonOwnerCannotRevokeAnotherUsersCredential() throws Exception {
        var community = provisioning.createCommunity(TENANT, "Takeover Revoke");
        var unit = provisioning.createUnit(TENANT, community.communityId(), "TKR", 2);
        Party ana = activate(community.communityId(), unit.unitId(), "Ana", null);
        UUID pedro = UUID.randomUUID();

        ProtocolException error = assertThrows(ProtocolException.class,
            () -> provisioning.revokeCredential(TENANT, community.communityId(), ana.credentialId(), pedro, null));
        assertEquals("CONTROLLER_NOT_OWNED", error.code());
        // Ana's credential is untouched (still active).
        assertNull(discovery.credential(TENANT, community.communityId(), ana.credentialId()).revocationSequence());
    }

    @Test void theOwnerCanManageTheirOwnController() throws Exception {
        var community = provisioning.createCommunity(TENANT, "Owner OK");
        var unit = provisioning.createUnit(TENANT, community.communityId(), "OOK", 2);
        Party ana = activate(community.communityId(), unit.unitId(), "Ana", null);
        var added = provisioning.addCredential(TENANT, community.communityId(), ana.controllerId(),
            rawPublic(KeyPairGenerator.getInstance("Ed25519").generateKeyPair()), ana.ownerUserId(), null);
        assertEquals(2, discovery.credentialsForController(TENANT, community.communityId(), ana.controllerId()).size());
        provisioning.revokeCredential(TENANT, community.communityId(), added.credentialId(), ana.ownerUserId(), null);
        assertNotNull(discovery.credential(TENANT, community.communityId(), added.credentialId()).revocationSequence());
    }

    @Test void aPreExistingUnownedControllerIsFailClosedForCredentialManagement() throws Exception {
        var community = provisioning.createCommunity(TENANT, "Unowned");
        var unit = provisioning.createUnit(TENANT, community.communityId(), "UNO", 2);
        Party ana = activate(community.communityId(), unit.unitId(), "Ana", null);
        // Simulate a controller that predates V6: wipe its recorded owner (all-or-nothing).
        jdbc.update("update ostris.protocol_controller set owner_user_id=null,owner_established_by=null,owner_established_sequence=null where id=?", ana.controllerId());

        ProtocolException addError = assertThrows(ProtocolException.class,
            () -> provisioning.addCredential(TENANT, community.communityId(), ana.controllerId(), rawPublic(KeyPairGenerator.getInstance("Ed25519").generateKeyPair()), ana.ownerUserId(), null));
        assertEquals("CONTROLLER_OWNERSHIP_UNASSIGNED", addError.code());
        ProtocolException revokeError = assertThrows(ProtocolException.class,
            () -> provisioning.revokeCredential(TENANT, community.communityId(), ana.credentialId(), ana.ownerUserId(), null));
        assertEquals("CONTROLLER_OWNERSHIP_UNASSIGNED", revokeError.code());
    }

    private static String rawPublic(KeyPair pair) {
        byte[] encoded = pair.getPublic().getEncoded();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(Arrays.copyOfRange(encoded, encoded.length - 32, encoded.length));
    }
    private static String sign(byte[] message, KeyPair pair) throws Exception {
        Signature s = Signature.getInstance("Ed25519"); s.initSign(pair.getPrivate()); s.update(message);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(s.sign());
    }
}
