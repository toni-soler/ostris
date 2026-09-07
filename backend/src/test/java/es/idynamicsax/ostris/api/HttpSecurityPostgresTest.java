package es.idynamicsax.ostris.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import es.idynamicsax.ostris.OstrisApplication;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = OstrisApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HttpSecurityPostgresTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("ostris").withUsername("postgres").withPassword("test")
            .withInitScript("postgres-init.sql");
    static final UUID TENANT_A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    static final UUID TENANT_B = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    static final UUID USER = UUID.fromString("018f6f9a-7b1c-7a2b-8c3d-4e5f60719201");
    static final UUID ROLE_A = UUID.fromString("018f6f9a-7b1c-7a2b-8c3d-4e5f60719207");
    static final UUID COMMUNITY_A = UUID.fromString("018f6f9a-7b1c-7a2b-8c3d-4e5f60719202");
    static final UUID COMMUNITY_B = UUID.fromString("018f6f9a-7b1c-7a2b-8c3d-4e5f60719203");
    static final UUID PARTICIPANT_A = UUID.fromString("018f6f9a-7b1c-7a2b-8c3d-4e5f60719204");
    static final UUID PARTICIPANT_B = UUID.fromString("018f6f9a-7b1c-7a2b-8c3d-4e5f60719205");
    static final KeyPair KEYS;
    static final Path PUBLIC_KEY_FILE;

    static {
        try {
            KEYS = KeyPairGenerator.getInstance("RSA").generateKeyPair();
            String encoded = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(KEYS.getPublic().getEncoded());
            PUBLIC_KEY_FILE = Files.createTempFile("ostris-http-public-", ".pem");
            Files.writeString(PUBLIC_KEY_FILE, "-----BEGIN PUBLIC KEY-----\n" + encoded + "\n-----END PUBLIC KEY-----\n", StandardCharsets.US_ASCII);
            PUBLIC_KEY_FILE.toFile().deleteOnExit();
        } catch (Exception exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    @LocalServerPort int port;
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    static boolean initialized;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("idax.auth.mode", () -> "LOCAL");
        registry.add("idax.auth.token-validator", () -> "local");
        registry.add("idax.auth.local.public-key-location", () -> PUBLIC_KEY_FILE.toUri().toString());
        registry.add("idax.ostris.ledger.enabled", () -> "true");
        registry.add("idax.ostris.ledger.worker-enabled", () -> "false");
        registry.add("idax.ostris.proof.enabled", () -> "false");
        registry.add("idax.ostris.ledger.platform-base-url", () -> "http://127.0.0.1:1");
        registry.add("idax.ostris.ledger.ledger-base-url", () -> "http://127.0.0.1:1");
    }

    @BeforeEach
    void fixture() {
        if (initialized) return;
        jdbc.update("alter table idax_core.tenant add column if not exists code varchar(32)");
        jdbc.execute("create table if not exists idax_core.app_user(user_id uuid primary key default gen_random_uuid(),external_subject varchar(255) unique,email varchar(255),display_name varchar(255),is_superuser boolean default false,updated_at timestamptz default now())");
        jdbc.execute("create or replace function idax_core.set_tenant(uuid) returns void language sql as 'select set_config(''app.tenant_id'', $1::text, true)'");
        jdbc.execute("grant usage on schema idax_core to idax_app,idax_admin");
        jdbc.execute("grant execute on function idax_core.set_tenant(uuid) to idax_app,idax_admin");
        jdbc.execute("grant select,insert,update on idax_core.app_user to idax_admin");
        jdbc.execute("grant select on idax_core.tenant_user,idax_core.idax_user_role,idax_core.idax_role,idax_core.idax_role_permission,idax_core.idax_permission to idax_app");
        jdbc.update("insert into idax_core.tenant(tenant_id,code) values(?,?),(?,?)", TENANT_A, "A", TENANT_B, "B");
        jdbc.update("insert into idax_core.app_user(user_id,external_subject,is_superuser) values(?,?,false)", USER, "phase7c-human");
        jdbc.update("insert into idax_core.tenant_user(tenant_id,user_id,role) values(?,?,?)", TENANT_A, USER, "user");
        jdbc.update("insert into idax_core.idax_role(role_id,tenant_id,role_key,name) values(?,?,?,?)", ROLE_A, TENANT_A, "ostris-reader", "osTRIS reader");
        jdbc.update("insert into idax_core.idax_user_role(tenant_id,user_id,role_id) values(?,?,?)", TENANT_A, USER, ROLE_A);
        jdbc.update("insert into idax_core.idax_role_permission(role_id,permission_code) values(?,?)", ROLE_A, "OSTRIS_READ");
        jdbc.update("insert into ostris.community(id,tenant_id,name) values(?,?,?),(?,?,?)", COMMUNITY_A, TENANT_A, "A", COMMUNITY_B, TENANT_B, "B");
        jdbc.update("insert into ostris.participant(id,tenant_id,community_id,display_name) values(?,?,?,?),(?,?,?,?)", PARTICIPANT_A, TENANT_A, COMMUNITY_A, "Public A", PARTICIPANT_B, TENANT_B, COMMUNITY_B, "Private B");
        assertEquals(6, jdbc.queryForObject("select count(*) from idax_core.idax_permission where module_key='ostris' and source_type='IDAX_MODULE'", Integer.class));
        initialized = true;
    }

    @Test
    void normalHumanUsesDatabasePermission() {
        String path = participantPath(COMMUNITY_A, PARTICIPANT_A);
        ResponseEntity<String> allowed = get(path, token(KEYS, TENANT_A, false, Instant.now().plusSeconds(300)));
        assertEquals(200, allowed.getStatusCode().value());
        assertFalse(allowed.getBody().contains("riskSubject"));
    }

    @Test
    void unauthenticatedAndInvalidTokensFailClosed() {
        String path = participantPath(COMMUNITY_A, PARTICIPANT_A);
        assertEquals(401, http.getForEntity(url(path), String.class).getStatusCode().value());
        assertEquals(401, get(path, token(otherKeys(), TENANT_A, false, Instant.now().plusSeconds(300))).getStatusCode().value());
        assertEquals(401, get(path, token(KEYS, TENANT_A, false, Instant.now().minusSeconds(60))).getStatusCode().value());
    }

    @Test
    void missingPermissionTenantIsolationAndNoTenantFailClosed() {
        String humanA = token(KEYS, TENANT_A, false, Instant.now().plusSeconds(300));
        assertEquals(403, http.exchange(url("/api/ostris/transactions/018f6f9a-7b1c-7a2b-8c3d-4e5f60719221/commit"), HttpMethod.POST, new HttpEntity<>(headers(humanA)), String.class).getStatusCode().value());
        assertEquals(403, get(participantPath(COMMUNITY_B, PARTICIPANT_B), token(KEYS, TENANT_B, false, Instant.now().plusSeconds(300))).getStatusCode().value());
        assertEquals(401, get(participantPath(COMMUNITY_A, PARTICIPANT_A), token(KEYS, null, false, Instant.now().plusSeconds(300))).getStatusCode().value());
    }

    @Test
    void superuserRetainsGlobalAuthorization() {
        assertEquals(200, get(participantPath(COMMUNITY_A, PARTICIPANT_A), token(KEYS, TENANT_A, true, Instant.now().plusSeconds(300))).getStatusCode().value());
    }

    private String participantPath(UUID community, UUID participant) { return "/api/ostris/participants/" + community + "/" + participant; }
    private ResponseEntity<String> get(String path, String token) { return http.exchange(url(path), HttpMethod.GET, new HttpEntity<>(headers(token)), String.class); }
    private HttpHeaders headers(String token) { HttpHeaders headers = new HttpHeaders(); headers.setBearerAuth(token); headers.setContentType(MediaType.APPLICATION_JSON); return headers; }
    private String url(String path) { return "http://localhost:" + port + path; }

    static String token(KeyPair pair, UUID tenant, boolean superuser, Instant expiry) {
        RSAKey jwk = new RSAKey.Builder((RSAPublicKey) pair.getPublic()).privateKey((RSAPrivateKey) pair.getPrivate()).keyID("test").build();
        JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));
        Instant issued = expiry.isBefore(Instant.now()) ? expiry.minusSeconds(300) : Instant.now().minusSeconds(5);
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder().issuer("idax-local").subject("phase7c-human").issuedAt(issued).expiresAt(expiry)
                .claim("userId", USER.toString()).claim("roles", Set.of()).claim("superuser", superuser);
        if (tenant != null) claims.claim("tenantId", tenant.toString());
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).keyId("test").build(), claims.build())).getTokenValue();
    }

    static KeyPair otherKeys() {
        try { return KeyPairGenerator.getInstance("RSA").generateKeyPair(); }
        catch (Exception exception) { throw new RuntimeException(exception); }
    }
}
