package es.idynamicsax.ostris;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import es.idynamicsax.idax.security.TokenValidationResult;
import es.idynamicsax.idax.security.TokenValidator;
import es.idynamicsax.idax.security.mfa.MfaChallengeTokenService;
import es.idynamicsax.idax.service.auth.ServiceTokenIssuer;
import es.idynamicsax.idax.service.auth.ServiceTokenProvider;
import es.idynamicsax.idax.tenant.AppUserResolver;
import es.idynamicsax.idax.tenant.DbSessionContextService;
import es.idynamicsax.idax.tenant.RlsTransactionAspect;
import es.idynamicsax.idax.tenant.TenantResolver;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
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
class OstrisApplicationCompositionPostgresTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("ostris")
            .withUsername("postgres")
            .withPassword("test")
            .withInitScript("postgres-init.sql");

    static final UUID USER_ID = UUID.fromString("018f6f9a-7b1c-7a2b-8c3d-4e5f60719301");
    static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    static KeyPair signingKeys;
    static Path publicKeyFile;

    static {
        try {
            signingKeys = KeyPairGenerator.getInstance("RSA").generateKeyPair();
            String encoded = Base64.getMimeEncoder(64, new byte[]{'\n'})
                    .encodeToString(signingKeys.getPublic().getEncoded());
            publicKeyFile = Files.createTempFile("ostris-composition-public-", ".pem");
            Files.writeString(publicKeyFile,
                    "-----BEGIN PUBLIC KEY-----\n" + encoded + "\n-----END PUBLIC KEY-----\n",
                    StandardCharsets.US_ASCII);
            publicKeyFile.toFile().deleteOnExit();
        } catch (Exception exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    @Autowired ApplicationContext context;
    @Autowired TokenValidator tokenValidator;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("idax.auth.mode", () -> "LOCAL");
        registry.add("idax.auth.token-validator", () -> "local");
        registry.add("idax.auth.local.public-key-location", () -> publicKeyFile.toUri().toString());
        registry.add("idax.ostris.ledger.enabled", () -> "true");
        registry.add("idax.ostris.proof.enabled", () -> "false");
        registry.add("idax.ostris.ledger.platform-base-url", () -> "http://127.0.0.1:1");
        registry.add("idax.ostris.ledger.ledger-base-url", () -> "http://127.0.0.1:1");
    }

    @Test
    void actualApplicationUsesVerificationOnlySecurityComposition() {
        assertNotNull(context.getBean("localJwtDecoder", JwtDecoder.class));
        assertNotNull(context.getBean(TokenValidator.class));
        assertNotNull(context.getBean(TenantResolver.class));
        assertNotNull(context.getBean(AppUserResolver.class));
        assertNotNull(context.getBean(DbSessionContextService.class));
        assertNotNull(context.getBean(RlsTransactionAspect.class));
        assertNotNull(context.getBean(ServiceTokenProvider.class));

        assertTrue(context.getBeansOfType(JwtEncoder.class).isEmpty());
        assertTrue(context.getBeansOfType(ServiceTokenIssuer.class).isEmpty());
        assertTrue(context.getBeansOfType(MfaChallengeTokenService.class).isEmpty());
        assertFalse(hasBeanClassNameContaining("LocalAuthService"));
        assertFalse(hasBeanNameContaining("login"));
    }

    @Test
    void localJwtAcceptsValidSignatureAndRejectsWrongOrExpiredTokens() throws Exception {
        TokenValidationResult valid = tokenValidator.validateAndExtract(
                token(signingKeys, Instant.now().plusSeconds(300)));
        assertTrue(valid.isValid());
        assertEquals(USER_ID, valid.getCurrentUser().getUserId());
        assertEquals(TENANT_ID, valid.getCurrentUser().getTenantId());

        assertFalse(tokenValidator.validateAndExtract(
                token(KeyPairGenerator.getInstance("RSA").generateKeyPair(), Instant.now().plusSeconds(300)))
                .isValid());
        assertFalse(tokenValidator.validateAndExtract(
                token(signingKeys, Instant.now().minusSeconds(60)))
                .isValid());
    }

    private boolean hasBeanClassNameContaining(String fragment) {
        for (String name : context.getBeanDefinitionNames()) {
            Class<?> type = context.getType(name);
            if (type != null && type.getName().contains(fragment)) return true;
        }
        return false;
    }

    private boolean hasBeanNameContaining(String fragment) {
        for (String name : context.getBeanDefinitionNames()) {
            if (name.toLowerCase().contains(fragment.toLowerCase())) return true;
        }
        return false;
    }

    private static String token(KeyPair pair, Instant expiry) {
        RSAKey jwk = new RSAKey.Builder((RSAPublicKey) pair.getPublic())
                .privateKey((RSAPrivateKey) pair.getPrivate()).keyID("test").build();
        JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));
        Instant issuedAt = expiry.isBefore(Instant.now()) ? expiry.minusSeconds(300) : Instant.now().minusSeconds(5);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("idax-local")
                .subject("ostris-composition-test")
                .issuedAt(issuedAt)
                .expiresAt(expiry)
                .claim("userId", USER_ID.toString())
                .claim("tenantId", TENANT_ID.toString())
                .claim("roles", Set.of("OSTRIS_READ"))
                .build();
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).keyId("test").build(), claims)).getTokenValue();
    }
}
