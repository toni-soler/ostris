package es.idynamicsax.ostris.service;

import static org.junit.jupiter.api.Assertions.*;

import es.idynamicsax.ostris.config.OstrisFlywayConfig;
import es.idynamicsax.ostris.core.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

/**
 * The resolution application surface end to end: an authority and a basis registered through
 * ResolutionService, a governance payload read back for signing, and an adjudicated REVERSAL that
 * commits only once the basis is FINAL and the authority's threshold has signed that payload.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = ResolutionLifecyclePostgresTest.TestApp.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@org.springframework.transaction.annotation.Transactional
class ResolutionLifecyclePostgresTest {
    @SpringBootConfiguration @EnableAutoConfiguration @EntityScan("es.idynamicsax.ostris.persistence")
    @EnableJpaRepositories("es.idynamicsax.ostris.persistence")
    @Import({OstrisFlywayConfig.class, ProposalAuthorizationService.class, JournalCommitService.class,
        OutboxProtocolEventProofService.class, ResolutionService.class})
    static class TestApp { @Bean com.fasterxml.jackson.databind.ObjectMapper objectMapper() { return new com.fasterxml.jackson.databind.ObjectMapper(); } }

    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17")
        .withDatabaseName("ostris").withUsername("postgres").withPassword("test").withInitScript("postgres-init.sql");

    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl); r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword); r.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        r.add("spring.flyway.enabled", () -> "false");
    }

    @Autowired ProposalAuthorizationService service;
    @Autowired ResolutionService resolution;
    @Autowired JdbcTemplate jdbc;

    static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-00000000000a"),
        COMMUNITY = UUID.fromString("018f6f9a-7b1c-7a2b-8c3d-4e5f60719001"), UNIT = UUID.fromString("018f6f9a-7b1c-7a2b-8c3d-4e5f60719002"),
        A = UUID.fromString("018f6f9a-7b1c-7a2b-8c3d-4e5f60719003"), B = UUID.fromString("018f6f9a-7b1c-7a2b-8c3d-4e5f60719004");
    static final OstrisWireCodec WIRE = new OstrisWireCodec();
    Signer party, first, second;

    @BeforeEach void setup() throws Exception {
        jdbc.update("insert into idax_core.tenant values(?) on conflict do nothing", TENANT);
        jdbc.update("insert into ostris.community(id,tenant_id,name) values(?,?,?) on conflict do nothing", COMMUNITY, TENANT, "RESOLUTION");
        jdbc.update("insert into ostris.unit_of_account(id,tenant_id,community_id,code,scale) values(?,?,?,?,2) on conflict do nothing", UNIT, TENANT, COMMUNITY, "RES");
        for (UUID account : List.of(A, B))
            jdbc.update("insert into ostris.account(id,tenant_id,community_id,unit_id,account_type,name,credit_floor) values(?,?,?,?,'INDIVIDUAL',?,-10000)",
                account, TENANT, COMMUNITY, UNIT, account.equals(A) ? "A" : "B");
        party = signer(); first = signer(); second = signer();
        for (UUID account : List.of(A, B)) {
            UUID policy = UUID.randomUUID();
            jdbc.update("insert into ostris.account_control_policy(id,tenant_id,community_id,account_id,policy_version,threshold,activation_sequence) values(?,?,?,?,1,1,0)",
                policy, TENANT, COMMUNITY, account);
            jdbc.update("insert into ostris.account_controller(tenant_id,control_policy_id,controller_id) values(?,?,?)", TENANT, policy, party.controller);
        }
    }

    @Test void adjudicatedReversalCommitsOnlyWithFinalBasisAndAuthorityThreshold() throws Exception {
        UUID original = committedExchange("100");
        var authority = resolution.registerAuthority(TENANT, COMMUNITY, List.of(first.controller, second.controller), 2);
        assertEquals(1, authority.authorityVersion());
        assertEquals(authority.authorityId(), resolution.currentAuthority(TENANT, COMMUNITY).authorityId());

        UUID basis = UUID.randomUUID();
        assertEquals("PENDING", resolution.recordBasis(TENANT, COMMUNITY, basis, ResolutionBasisType.FINAL_DISPUTE_RESOLUTION).status());
        assertEquals("PENDING", resolution.recordBasis(TENANT, COMMUNITY, basis, ResolutionBasisType.FINAL_DISPUTE_RESOLUTION).status(), "idempotent");
        assertEquals("RESOLUTION_BASIS_CONFLICT", code(() -> resolution.recordBasis(TENANT, COMMUNITY, basis, ResolutionBasisType.FINAL_FINDING)));

        UUID reversal = UUID.randomUUID();
        service.propose(new CommitTransactionCommand(TENANT, COMMUNITY, UNIT, reversal, TransactionPurpose.REVERSAL,
            List.of(new CommitTransactionCommand.Entry(A, "-100"), new CommitTransactionCommand.Entry(B, "100")),
            Map.of("reversesTransactionId", original.toString()), null, ResolutionBasisType.FINAL_DISPUTE_RESOLUTION, basis));
        Set<UUID> covered = Set.of(A, B);
        var signing = service.governanceSigningPayload(TENANT, reversal, authority.authorityId(), 1, ResolutionBasisType.FINAL_DISPUTE_RESOLUTION, basis, covered);
        assertEquals("OSTRIS:TX:GOVAUTH:V1", signing.signingDomain());
        byte[] message = message(signing.signingDomain(), signing.governancePayload());
        assertEquals(OstrisWireCodec.sha256(message), signing.governanceDigest());
        assertEquals("RESOLUTION_BASIS_REFERENCE_MISMATCH", code(() -> service.governanceSigningPayload(TENANT, reversal, authority.authorityId(), 1,
            ResolutionBasisType.FINAL_DISPUTE_RESOLUTION, UUID.randomUUID(), covered)));

        govern(reversal, authority.authorityId(), basis, covered, first, message);
        assertEquals("RESOLUTION_BASIS_NOT_FINAL", code(() -> service.commit(TENANT, reversal)));

        var finalBasis = resolution.finalizeBasis(TENANT, COMMUNITY, basis);
        assertEquals("FINAL", finalBasis.status());
        assertNotNull(finalBasis.finalitySequence());
        assertThrows(ProtocolException.class, () -> service.commit(TENANT, reversal), "one of two authority signatures is not enough");

        govern(reversal, authority.authorityId(), basis, covered, second, message);
        service.commit(TENANT, reversal);
        assertEquals(0, balance(A));
        assertEquals(0, balance(B));
        assertEquals("PROPOSAL_NOT_OPEN", code(() -> service.governanceSigningPayload(TENANT, reversal, authority.authorityId(), 1,
            ResolutionBasisType.FINAL_DISPUTE_RESOLUTION, basis, covered)));
    }

    @Test void newAuthorityVersionRevokesThePreviousOneForLaterCommits() throws Exception {
        UUID original = committedExchange("40");
        var v1 = resolution.registerAuthority(TENANT, COMMUNITY, List.of(first.controller), 1);
        var v2 = resolution.registerAuthority(TENANT, COMMUNITY, List.of(second.controller), 1);
        assertEquals(2, v2.authorityVersion());
        assertEquals(v2.authorityId(), resolution.currentAuthority(TENANT, COMMUNITY).authorityId());
        assertNotNull(jdbc.queryForObject("select revocation_sequence from ostris.community_resolution_authority where id=?", Long.class, v1.authorityId()));

        UUID basis = UUID.randomUUID();
        resolution.recordBasis(TENANT, COMMUNITY, basis, ResolutionBasisType.FINAL_DISPUTE_RESOLUTION);
        resolution.finalizeBasis(TENANT, COMMUNITY, basis);
        UUID reversal = UUID.randomUUID();
        service.propose(new CommitTransactionCommand(TENANT, COMMUNITY, UNIT, reversal, TransactionPurpose.REVERSAL,
            List.of(new CommitTransactionCommand.Entry(A, "-40"), new CommitTransactionCommand.Entry(B, "40")),
            Map.of("reversesTransactionId", original.toString()), null, ResolutionBasisType.FINAL_DISPUTE_RESOLUTION, basis));
        var stale = service.governanceSigningPayload(TENANT, reversal, v1.authorityId(), 1, ResolutionBasisType.FINAL_DISPUTE_RESOLUTION, basis, Set.of(A, B));
        govern(reversal, v1.authorityId(), basis, Set.of(A, B), first, message(stale.signingDomain(), stale.governancePayload()), 1);
        assertEquals("AUTHORITY_NOT_EFFECTIVE", code(() -> service.commit(TENANT, reversal)));
    }

    @Test void authorityAndBasisInputsAreValidated() throws Exception {
        assertEquals("INVALID_RESOLUTION_AUTHORITY", code(() -> resolution.registerAuthority(TENANT, COMMUNITY, List.of(first.controller), 2)));
        assertEquals("INVALID_RESOLUTION_AUTHORITY", code(() -> resolution.registerAuthority(TENANT, COMMUNITY, List.of(first.controller, first.controller), 1)));
        assertEquals("UNKNOWN_CONTROLLER", code(() -> resolution.registerAuthority(TENANT, COMMUNITY, List.of(UUID.randomUUID()), 1)));
        assertEquals("NO_RESOLUTION_AUTHORITY", code(() -> resolution.currentAuthority(TENANT, COMMUNITY)));
        UUID basis = UUID.randomUUID();
        resolution.recordBasis(TENANT, COMMUNITY, basis, ResolutionBasisType.FINAL_DISPUTE_RESOLUTION);
        assertEquals("OVERTURNED", resolution.overturnBasis(TENANT, COMMUNITY, basis).status());
        assertEquals("RESOLUTION_BASIS_NOT_PENDING", code(() -> resolution.finalizeBasis(TENANT, COMMUNITY, basis)));
        assertEquals("UNKNOWN_RESOLUTION_BASIS", code(() -> resolution.finalizeBasis(TENANT, COMMUNITY, UUID.randomUUID())));
        UUID exchange = UUID.randomUUID();
        service.propose(new CommitTransactionCommand(TENANT, COMMUNITY, UNIT, exchange, TransactionPurpose.EXCHANGE,
            List.of(new CommitTransactionCommand.Entry(A, "1"), new CommitTransactionCommand.Entry(B, "-1")), Map.of(), null));
        var authority = resolution.registerAuthority(TENANT, COMMUNITY, List.of(first.controller), 1);
        assertEquals("GOVERNED_PURPOSE_BASIS_NOT_ALLOWED", code(() -> service.governanceSigningPayload(TENANT, exchange, authority.authorityId(), 1,
            ResolutionBasisType.FINAL_DISPUTE_RESOLUTION, basis, Set.of(A, B))));
    }

    @Test void finalBasisCannotReturnToPending() {
        UUID basis = UUID.randomUUID();
        resolution.recordBasis(TENANT, COMMUNITY, basis, ResolutionBasisType.FINAL_DISPUTE_RESOLUTION);
        resolution.finalizeBasis(TENANT, COMMUNITY, basis);
        assertThrows(Exception.class, () -> jdbc.update("update ostris.resolution_basis set status='PENDING' where id=?", basis));
    }

    @Test void basisIsNeverDeleted() {
        UUID basis = UUID.randomUUID();
        resolution.recordBasis(TENANT, COMMUNITY, basis, ResolutionBasisType.FINAL_DISPUTE_RESOLUTION);
        assertThrows(Exception.class, () -> jdbc.update("delete from ostris.resolution_basis where id=?", basis));
    }

    @Test void authorityThresholdIsImmutable() {
        var authority = resolution.registerAuthority(TENANT, COMMUNITY, List.of(first.controller, second.controller), 2);
        assertThrows(Exception.class, () -> jdbc.update("update ostris.community_resolution_authority set threshold=1 where id=?", authority.authorityId()));
    }

    @Test void authorityControllersAreFixed() {
        var authority = resolution.registerAuthority(TENANT, COMMUNITY, List.of(first.controller), 1);
        assertThrows(Exception.class, () -> jdbc.update("delete from ostris.community_resolution_controller where authority_id=?", authority.authorityId()));
    }

    private UUID committedExchange(String amount) throws Exception {
        UUID tx = UUID.randomUUID();
        service.propose(new CommitTransactionCommand(TENANT, COMMUNITY, UNIT, tx, TransactionPurpose.EXCHANGE,
            List.of(new CommitTransactionCommand.Entry(A, amount), new CommitTransactionCommand.Entry(B, "-" + amount)), Map.of(), null));
        String payload = jdbc.queryForObject("select authorization_payload from ostris.transaction_proposal where id=?", String.class, tx);
        String signature = sign(WIRE.authorizationMessage(WIRE.parse(payload)), party.key);
        for (UUID account : List.of(A, B)) service.addAccountEvidence(TENANT, tx, account, party.credential, signature);
        service.commit(TENANT, tx);
        return tx;
    }

    private void govern(UUID tx, UUID authority, UUID basis, Set<UUID> covered, Signer signer, byte[] message) throws Exception {
        govern(tx, authority, basis, covered, signer, message, 1);
    }

    private void govern(UUID tx, UUID authority, UUID basis, Set<UUID> covered, Signer signer, byte[] message, int version) throws Exception {
        service.addGovernanceEvidence(TENANT, tx, authority, version, ResolutionBasisType.FINAL_DISPUTE_RESOLUTION, basis, covered,
            signer.credential, sign(message, signer.key));
    }

    private static byte[] message(String domain, String payload) {
        byte[] d = domain.getBytes(StandardCharsets.US_ASCII), p = payload.getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[d.length + 1 + p.length];
        System.arraycopy(d, 0, out, 0, d.length);
        System.arraycopy(p, 0, out, d.length + 1, p.length);
        return out;
    }

    private static String code(Runnable action) {
        return assertThrows(ProtocolException.class, action::run).code();
    }

    private int balance(UUID account) {
        return jdbc.queryForObject("select coalesce(sum(amount),0) from ostris.journal_entry where account_id=?", Integer.class, account);
    }

    private Signer signer() throws Exception {
        KeyPair pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        UUID controllerId = UUID.randomUUID(), credentialId = UUID.randomUUID();
        byte[] encoded = pair.getPublic().getEncoded();
        String publicKey = Base64.getUrlEncoder().withoutPadding().encodeToString(Arrays.copyOfRange(encoded, encoded.length - 32, encoded.length));
        jdbc.update("insert into ostris.protocol_controller(id,tenant_id,community_id,activation_sequence) values(?,?,?,0)", controllerId, TENANT, COMMUNITY);
        jdbc.update("insert into ostris.credential(id,tenant_id,community_id,public_key_base64url,algorithm,activation_sequence) values(?,?,?,?,'Ed25519',0)",
            credentialId, TENANT, COMMUNITY, publicKey);
        jdbc.update("insert into ostris.controller_credential_binding(credential_id,tenant_id,community_id,controller_id,public_key_base64url,activation_sequence) values(?,?,?,?,?,0)",
            credentialId, TENANT, COMMUNITY, controllerId, publicKey);
        return new Signer(controllerId, credentialId, pair);
    }

    private static String sign(byte[] message, KeyPair pair) throws Exception {
        Signature s = Signature.getInstance("Ed25519");
        s.initSign(pair.getPrivate());
        s.update(message);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(s.sign());
    }

    private record Signer(UUID controller, UUID credential, KeyPair key) {}
}
