package es.idynamicsax.ostris.ledger;

import static org.junit.jupiter.api.Assertions.*;

import com.nimbusds.jwt.SignedJWT;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OstrisServiceTokenProviderPlatformIT {
    private static final UUID TENANT_ID = UUID.fromString("cd02b56d-f7ad-44a6-8d6e-24589f1cbf2a");
    private static final Set<String> PERMISSIONS = Set.of(
            "LEDGER_PROOF_CREATE", "LEDGER_READ", "LEDGER_PROOF_VERIFY");

    @Test
    void productionProviderObtainsScopedServiceTokenFromRealPlatform() throws Exception {
        String platformUrl = requiredEnvironment("OSTRIS_IT_PLATFORM_URL");
        String clientSecret = requiredEnvironment("OSTRIS_IT_CLIENT_SECRET");
        var properties = new OstrisLedgerDeliveryProperties(false, "http://127.0.0.1:1",
                platformUrl, "ostris-ledger-delivery", clientSecret, "idax-ledger",
                Duration.ofSeconds(2), Duration.ofSeconds(10), Duration.ofSeconds(45),
                Duration.ofSeconds(5), 10, Duration.ofMinutes(2), Duration.ofSeconds(10),
                Duration.ofMinutes(15));

        var response = new OstrisLedgerDeliveryConfig().ostrisServiceTokenProvider(properties)
                .token(TENANT_ID, "idax-ledger", PERMISSIONS);
        var claims = SignedJWT.parse(response.accessToken()).getJWTClaimsSet();

        assertEquals("Bearer", response.tokenType());
        assertEquals("SERVICE", claims.getStringClaim("principal_type"));
        assertEquals("ostris-ledger-delivery", claims.getStringClaim("client_id"));
        assertEquals(TENANT_ID.toString(), claims.getStringClaim("tenant_id"));
        assertEquals(Set.of("idax-ledger"), new HashSet<>(claims.getAudience()));
        assertEquals(PERMISSIONS, new HashSet<>(claims.getStringListClaim("permissions")));
    }

    private String requiredEnvironment(String name) {
        String value = System.getenv(name);
        assertNotNull(value, name + " must be configured for this integration test");
        assertFalse(value.isBlank(), name + " must not be blank");
        return value;
    }
}
