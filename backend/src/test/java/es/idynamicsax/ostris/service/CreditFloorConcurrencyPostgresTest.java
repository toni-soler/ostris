package es.idynamicsax.ostris.service;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import es.idynamicsax.ostris.config.OstrisFlywayConfig;
import es.idynamicsax.ostris.core.OstrisWireCodec;
import es.idynamicsax.ostris.core.ProtocolException;
import es.idynamicsax.ostris.core.ResolutionBasisType;
import es.idynamicsax.ostris.core.TransactionPurpose;
import es.idynamicsax.ostris.service.CommitTransactionCommand;
import es.idynamicsax.ostris.service.JournalCommitService;
import es.idynamicsax.ostris.service.OutboxProtocolEventProofService;
import es.idynamicsax.ostris.service.ProposalAuthorizationService;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
@Testcontainers(disabledWithoutDocker=true) @TestInstance(value=TestInstance.Lifecycle.PER_CLASS) class CreditFloorConcurrencyPostgresTest {@Container static final PostgreSQLContainer<?> POSTGRES = (PostgreSQLContainer)new PostgreSQLContainer("postgres:17").withDatabaseName("ostris").withUsername("postgres").withPassword("test").withInitScript("postgres-init.sql");
ProposalAuthorizationService service;
JdbcTemplate jdbc;
ConfigurableApplicationContext context;
UUID tenant;
UUID community;
UUID unit;
UUID a;
UUID b;
UUID controller;
UUID credential;
KeyPair key;
CreditFloorConcurrencyPostgresTest() {}
@BeforeAll void start() {this.restartContext();}
@AfterAll void stop() {if (this.context != null) {this.context.close();}}
private void restartContext() {this.context = new SpringApplicationBuilder(new Class[]{TestApp.class}).web(WebApplicationType.NONE).run(new String[]{"--spring.datasource.url=" + POSTGRES.getJdbcUrl(), "--spring.datasource.username=" + POSTGRES.getUsername(), "--spring.datasource.password=" + POSTGRES.getPassword(), "--spring.jpa.hibernate.ddl-auto=none", "--spring.flyway.enabled=false"}); this.service = (ProposalAuthorizationService)this.context.getBean(ProposalAuthorizationService.class); this.jdbc = (JdbcTemplate)this.context.getBean(JdbcTemplate.class);}
@BeforeEach void fixture() throws Exception {this.tenant = UUID.randomUUID(); this.community = UUID.randomUUID(); this.unit = UUID.randomUUID(); this.a = UUID.randomUUID(); this.b = UUID.randomUUID(); this.controller = UUID.randomUUID(); this.credential = UUID.randomUUID(); this.key = KeyPairGenerator.getInstance("Ed25519").generateKeyPair(); String publicKey = CreditFloorConcurrencyPostgresTest.rawPublic(this.key); this.jdbc.update("insert into idax_core.tenant values(?)", new Object[]{this.tenant}); this.jdbc.update("insert into ostris.community(id,tenant_id,name) values(?,?,?)", new Object[]{this.community, this.tenant, "CONCURRENCY"}); this.jdbc.update("insert into ostris.unit_of_account(id,tenant_id,community_id,code,scale) values(?,?,?,?,2)", new Object[]{this.unit, this.tenant, this.community, "TEST"}); this.jdbc.update("insert into ostris.account(id,tenant_id,community_id,unit_id,account_type,name,credit_floor) values(?,?,?,?,?,'A',-100),(?,?,?,?,?,'B',-10000)", new Object[]{this.a, this.tenant, this.community, this.unit, "INDIVIDUAL", this.b, this.tenant, this.community, this.unit, "INDIVIDUAL"}); this.jdbc.update("insert into ostris.protocol_controller(id,tenant_id,community_id,activation_sequence) values(?,?,?,0)", new Object[]{this.controller, this.tenant, this.community}); this.jdbc.update("insert into ostris.credential(id,tenant_id,community_id,public_key_base64url,algorithm,activation_sequence) values(?,?,?,?, 'Ed25519',0)", new Object[]{this.credential, this.tenant, this.community, publicKey}); this.jdbc.update("insert into ostris.controller_credential_binding(credential_id,tenant_id,community_id,controller_id,public_key_base64url,activation_sequence) values(?,?,?,?,?,0)", new Object[]{this.credential, this.tenant, this.community, this.controller, publicKey}); for (UUID account : List.of(this.a, this.b)) {UUID policy = UUID.randomUUID(); this.jdbc.update("insert into ostris.account_control_policy(id,tenant_id,community_id,account_id,policy_version,threshold,activation_sequence) values(?,?,?,?,1,1,0)", new Object[]{policy, this.tenant, this.community, account}); this.jdbc.update("insert into ostris.account_controller(tenant_id,control_policy_id,controller_id) values(?,?,?)", new Object[]{this.tenant, policy, this.controller});}}
@Test void twoRealConcurrentCommitsCannotCrossFloor() throws Exception {UUID seed = this.ready(-80, 80, false); this.service.commit(this.tenant, seed); UUID first = this.ready(-15, 15, false); UUID second = this.ready(-15, 15, true); ExecutorService pool = Executors.newFixedThreadPool(2); CountDownLatch start = new CountDownLatch(1); Callable<String> commitFirst = () -> this.commitAfter(start, first); Callable<String> commitSecond = () -> this.commitAfter(start, second); Future<String> one = pool.submit(commitFirst); Future<String> two = pool.submit(commitSecond); start.countDown(); List<String> results = List.of(one.get(20L, TimeUnit.SECONDS), two.get(20L, TimeUnit.SECONDS)); pool.shutdownNow(); Assertions.assertEquals((long)1L, (long)results.stream().filter("COMMITTED"::equals).count()); Assertions.assertEquals((long)1L, (long)results.stream().filter("CREDIT_FLOOR_EXCEEDED"::equals).count()); Assertions.assertEquals((int)-95, (Integer)((Integer)this.jdbc.queryForObject("select sum(amount) from ostris.journal_entry where account_id=?", Integer.class, new Object[]{this.a}))); Assertions.assertEquals((int)1, (Integer)((Integer)this.jdbc.queryForObject("select count(*) from ostris.journal_transaction where id in (?,?)", Integer.class, new Object[]{first, second}))); Assertions.assertEquals((int)0, (Integer)((Integer)this.jdbc.queryForObject("select sum(amount) from ostris.journal_entry", Integer.class))); UUID penalty = this.commitGovernedPenalty(); Assertions.assertEquals((int)-105, (Integer)((Integer)this.jdbc.queryForObject("select sum(amount) from ostris.journal_entry where account_id=?", Integer.class, new Object[]{this.a}))); this.context.close(); this.context = null; this.restartContext(); Assertions.assertEquals((int)-105, (Integer)((Integer)this.jdbc.queryForObject("select sum(amount) from ostris.journal_entry where account_id=?", Integer.class, new Object[]{this.a}))); Assertions.assertEquals((int)95, (Integer)((Integer)this.jdbc.queryForObject("select sum(amount) from ostris.journal_entry where account_id=?", Integer.class, new Object[]{this.b}))); Assertions.assertEquals((int)10, (Integer)((Integer)this.jdbc.queryForObject("select sum(amount) from ostris.journal_entry where account_id=?", Integer.class, new Object[]{penalty}))); Assertions.assertEquals((int)0, (Integer)((Integer)this.jdbc.queryForObject("select sum(amount) from ostris.journal_entry", Integer.class)));}
@Test void concurrentSameTransactionIdProducesOneEconomicCommit() throws Exception {UUID tx = this.ready(-1, 1, false); ExecutorService pool = Executors.newFixedThreadPool(2); CountDownLatch start = new CountDownLatch(1); Future<String> one = pool.submit(() -> this.commitAfter(start, tx)); Future<String> two = pool.submit(() -> this.commitAfter(start, tx)); start.countDown(); Assertions.assertEquals(List.of("COMMITTED", "COMMITTED"), new ArrayList<String>(List.of(one.get(20L, TimeUnit.SECONDS), two.get(20L, TimeUnit.SECONDS))).stream().sorted().toList()); pool.shutdownNow(); Assertions.assertEquals((int)1, (Integer)((Integer)this.jdbc.queryForObject("select count(*) from ostris.journal_transaction where id=?", Integer.class, new Object[]{tx}))); Assertions.assertEquals((int)2, (Integer)((Integer)this.jdbc.queryForObject("select count(*) from ostris.journal_entry where transaction_id=?", Integer.class, new Object[]{tx}))); Assertions.assertEquals((int)1, (Integer)((Integer)this.jdbc.queryForObject("select count(*) from ostris.protocol_proof_outbox where transaction_id=?", Integer.class, new Object[]{tx})));}
private String commitAfter(CountDownLatch start, UUID tx) throws Exception {start.await(); try {this.service.commit(this.tenant, tx); return "COMMITTED";} catch (ProtocolException e) {return e.code();}}
private UUID commitGovernedPenalty() throws Exception {Signer second = this.createSigner(); Signer third = this.createSigner(); UUID authority = UUID.randomUUID(); UUID penalty = UUID.randomUUID(); UUID finding = UUID.fromString("018f6f9a-7b1c-7a2b-8c3d-4e5f60718201"); this.jdbc.update("insert into ostris.account(id,tenant_id,community_id,unit_id,account_type,name,credit_floor) values(?,?,?,?,'COMMUNITY_PENALTY','Penalty',-10000)", new Object[]{penalty, this.tenant, this.community, this.unit}); this.jdbc.update("insert into ostris.policy_version(id,tenant_id,community_id,policy_type,policy_version,activation_sequence,config_json,config_digest) values(?,?,?,'SANCTION',1,0,?::jsonb,?)", new Object[]{UUID.randomUUID(), this.tenant, this.community, "{\"schema\":\"OSTRIS-SANCTION-POLICY-1\",\"penaltyAllowed\":true,\"restitutionFromFinalFindingAllowed\":true,\"restitutionFromFinalDisputeResolutionAllowed\":true}", "0".repeat(64)}); this.jdbc.update("insert into ostris.finding(id,tenant_id,community_id,case_id,status,finality_sequence) values(?,?,?,?,'FINAL',0)", new Object[]{finding, this.tenant, this.community, UUID.randomUUID()}); this.jdbc.update("insert into ostris.resolution_basis(id,tenant_id,community_id,basis_type,status,finality_sequence) values(?,?,?,'FINAL_FINDING','FINAL',0)", new Object[]{finding, this.tenant, this.community}); this.jdbc.update("insert into ostris.community_resolution_authority(id,tenant_id,community_id,authority_version,threshold,activation_sequence) values(?,?,?,?,2,0)", new Object[]{authority, this.tenant, this.community, 1}); this.jdbc.update("insert into ostris.community_resolution_controller(tenant_id,authority_id,controller_id) values(?,?,?),(?,?,?),(?,?,?)", new Object[]{this.tenant, authority, this.controller, this.tenant, authority, second.controller, this.tenant, authority, third.controller}); UUID tx = UUID.randomUUID(); this.service.propose(new CommitTransactionCommand(this.tenant, this.community, this.unit, tx, TransactionPurpose.PENALTY, List.of(new CommitTransactionCommand.Entry(this.a, "-10"), new CommitTransactionCommand.Entry(penalty, "10")), Map.of("finalFindingId", finding.toString()), null)); Set<UUID> covered = Set.of(this.a, penalty); this.service.addGovernanceEvidence(this.tenant, tx, authority, 1, ResolutionBasisType.FINAL_FINDING, finding, covered, this.credential, this.governanceSignature(tx, authority, finding, covered, this.key)); this.service.addGovernanceEvidence(this.tenant, tx, authority, 1, ResolutionBasisType.FINAL_FINDING, finding, covered, second.credential, this.governanceSignature(tx, authority, finding, covered, second.key)); this.service.commit(this.tenant, tx); return penalty;}
private Signer createSigner() throws Exception {KeyPair pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair(); UUID controllerId = UUID.randomUUID(); UUID credentialId = UUID.randomUUID(); String publicKey = CreditFloorConcurrencyPostgresTest.rawPublic(pair); this.jdbc.update("insert into ostris.protocol_controller(id,tenant_id,community_id,activation_sequence) values(?,?,?,0)", new Object[]{controllerId, this.tenant, this.community}); this.jdbc.update("insert into ostris.credential(id,tenant_id,community_id,public_key_base64url,algorithm,activation_sequence) values(?,?,?,?, 'Ed25519',0)", new Object[]{credentialId, this.tenant, this.community, publicKey}); this.jdbc.update("insert into ostris.controller_credential_binding(credential_id,tenant_id,community_id,controller_id,public_key_base64url,activation_sequence) values(?,?,?,?,?,0)", new Object[]{credentialId, this.tenant, this.community, controllerId, publicKey}); return new Signer(controllerId, credentialId, pair);}
private String governanceSignature(UUID tx, UUID authority, UUID basis, Set<UUID> covered, KeyPair signer) throws Exception {String payload = (String)this.jdbc.queryForObject("select authorization_payload from ostris.transaction_proposal where id=?", String.class, new Object[]{tx}); ObjectNode n = (ObjectNode)new OstrisWireCodec().parse(payload); n.put("authorityId", authority.toString()); n.put("authorityPolicyVersion", 1); ArrayNode ca = n.putArray("coveredAccounts"); covered.stream().sorted().forEach(id -> ca.add(id.toString())); ObjectNode rb = n.putObject("resolutionBasis"); rb.put("id", basis.toString()); rb.put("type", "FINAL_FINDING"); return CreditFloorConcurrencyPostgresTest.sign(new OstrisWireCodec().governanceAuthorizationMessage((JsonNode)n), signer);}
private UUID ready(int debit, int credit, boolean reverseOrder) throws Exception {UUID tx = UUID.randomUUID(); ArrayList<CommitTransactionCommand.Entry> entries = new ArrayList<CommitTransactionCommand.Entry>(List.of(new CommitTransactionCommand.Entry(this.a, Integer.toString(debit)), new CommitTransactionCommand.Entry(this.b, Integer.toString(credit)))); if (reverseOrder) {Collections.reverse(entries);} this.service.propose(new CommitTransactionCommand(this.tenant, this.community, this.unit, tx, TransactionPurpose.EXCHANGE, entries, Map.of(), null)); String payload = (String)this.jdbc.queryForObject("select authorization_payload from ostris.transaction_proposal where id=?", String.class, new Object[]{tx}); byte[] message = new OstrisWireCodec().authorizationMessage(new OstrisWireCodec().parse(payload)); String signature = CreditFloorConcurrencyPostgresTest.sign(message, this.key); this.service.addAccountEvidence(this.tenant, tx, this.a, this.credential, signature); this.service.addAccountEvidence(this.tenant, tx, this.b, this.credential, signature); return tx;}
private static String rawPublic(KeyPair pair) {byte[] encoded = pair.getPublic().getEncoded(); return Base64.getUrlEncoder().withoutPadding().encodeToString(Arrays.copyOfRange(encoded, encoded.length - 32, encoded.length));}
private static String sign(byte[] message, KeyPair pair) throws Exception {Signature s = Signature.getInstance("Ed25519"); s.initSign(pair.getPrivate()); s.update(message); return Base64.getUrlEncoder().withoutPadding().encodeToString(s.sign());}
@SpringBootConfiguration @EnableAutoConfiguration @EntityScan(value={"es.idynamicsax.ostris.persistence"}
) @EnableJpaRepositories(value={"es.idynamicsax.ostris.persistence"}
) @Import(value={OstrisFlywayConfig.class, ProposalAuthorizationService.class, JournalCommitService.class, OutboxProtocolEventProofService.class}
) static class TestApp {TestApp() {} @Bean ObjectMapper objectMapper() {return new ObjectMapper();}}
private record Signer(UUID controller, UUID credential, KeyPair key) {}
}
