package es.idynamicsax.ostris.ledger;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

class HttpLedgerProofClientTest {
    private static final String SUITE_DIGEST = "c06a044828c466506611195299d2f49a8d6f5db97ce29922dcb7e435525473c9";

    @Test
    void sendsOnlyTheMinimalPrecomputedProofContract() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> idempotency = new AtomicReference<>();
        UUID proofId = UUID.randomUUID();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/ledger/proofs", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            idempotency.set(exchange.getRequestHeaders().getFirst("Idempotency-Key"));
            byte[] response = ("{\"id\":\"" + proofId + "\",\"externalId\":\"ref\","
                    + "\"contentHash\":\"digest\",\"canonicalizationProfile\":\"profile\",\"status\":\"CREATED\"}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(201, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                    HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build());
            factory.setReadTimeout(Duration.ofSeconds(2));
            var client = new HttpLedgerProofClient(RestClient.builder()
                    .baseUrl("http://127.0.0.1:" + server.getAddress().getPort())
                    .requestFactory(factory).build());
            String externalReference = "ostris:v1:" + "a".repeat(64);
            String proofDigest = "b".repeat(64);
            UUID transactionId = UUID.randomUUID();
            var created = client.create("service-token", externalReference,
                    new LedgerProofClient.LedgerProofRequest(externalReference,
                            ProtocolEventProofDeliveryService.PROOF_TYPE, "SHA-256", proofDigest,
                            "EXTERNAL:OSTRIS-PROTOCOL-EVENT-PROOF-V1", Map.of()));

            assertEquals(proofId, created.id());
            assertEquals("Bearer service-token", authorization.get());
            assertEquals(externalReference, idempotency.get());
            assertTrue(body.get().contains("\"externalId\":\"" + externalReference + "\""));
            assertTrue(body.get().contains("\"hash\":\"" + proofDigest + "\""));
            assertTrue(body.get().contains("\"metadata\":{}"));
            for (String forbidden : new String[] {transactionId.toString(), "transactionId", "entries", "amount",
                    "balance", "participant", "riskSubject", "finding", "resolution", "policy", "canonicalJson",
                    SUITE_DIGEST, "X-Tenant"}) {
                assertFalse(body.get().toLowerCase().contains(forbidden.toLowerCase()), forbidden);
            }
        } finally {
            server.stop(0);
        }
    }
}
