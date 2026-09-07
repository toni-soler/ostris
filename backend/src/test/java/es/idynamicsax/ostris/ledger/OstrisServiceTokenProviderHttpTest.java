package es.idynamicsax.ostris.ledger;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import es.idynamicsax.idax.service.auth.ServiceTokenProvider;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpClientErrorException;

class OstrisServiceTokenProviderHttpTest {
    private static final UUID TENANT_ID = UUID.fromString("cd02b56d-f7ad-44a6-8d6e-24589f1cbf2a");
    private static final Set<String> PERMISSIONS = Set.of(
            "LEDGER_PROOF_CREATE", "LEDGER_READ", "LEDGER_PROOF_VERIFY");
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void productionProviderPostsTheSupportedJsonContract() throws Exception {
        AtomicReference<String> method = new AtomicReference<>();
        AtomicReference<String> path = new AtomicReference<>();
        AtomicReference<String> contentType = new AtomicReference<>();
        AtomicReference<String> accept = new AtomicReference<>();
        AtomicReference<JsonNode> request = new AtomicReference<>();
        HttpServer server = server(exchange -> {
            method.set(exchange.getRequestMethod());
            path.set(exchange.getRequestURI().getPath());
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            accept.set(exchange.getRequestHeaders().getFirst("Accept"));
            request.set(json.readTree(exchange.getRequestBody()));
            byte[] response = ("{\"accessToken\":\"test-token\",\"tokenType\":\"Bearer\","+
                    "\"expiresAt\":\"" + Instant.now().plusSeconds(300) + "\",\"expiresIn\":300}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
        });
        try {
            ServiceTokenProvider provider = provider(server, "test-secret");
            assertEquals("test-token", provider.token(TENANT_ID, "idax-ledger", PERMISSIONS).accessToken());

            assertEquals("POST", method.get());
            assertEquals("/api/service-auth/token", path.get());
            assertNotNull(contentType.get());
            assertTrue(contentType.get().startsWith("application/json"));
            assertEquals("application/json", accept.get());
            assertEquals(5, request.get().size());
            for (String field : Set.of("clientId", "clientSecret", "tenantId", "audience", "permissions")) {
                assertTrue(request.get().has(field), field);
            }
            assertEquals("ostris-ledger-delivery", request.get().path("clientId").textValue());
            assertEquals(TENANT_ID.toString(), request.get().path("tenantId").textValue());
            assertEquals("idax-ledger", request.get().path("audience").textValue());
            assertEquals(PERMISSIONS, json.convertValue(request.get().path("permissions"),
                    json.getTypeFactory().constructCollectionType(Set.class, String.class)));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void productionProviderPreservesTokenEndpointErrors() throws Exception {
        AtomicReference<String> contentType = new AtomicReference<>();
        HttpServer server = server(exchange -> {
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            exchange.sendResponseHeaders(401, -1);
        });
        try {
            ServiceTokenProvider provider = provider(server, "never-logged-secret");
            assertThrows(HttpClientErrorException.Unauthorized.class,
                    () -> provider.token(TENANT_ID, "idax-ledger", PERMISSIONS));
            assertNotNull(contentType.get());
            assertTrue(contentType.get().startsWith("application/json"));
        } finally {
            server.stop(0);
        }
    }

    private ServiceTokenProvider provider(HttpServer server, String secret) {
        var properties = new OstrisLedgerDeliveryProperties(false,
                "http://127.0.0.1:1", "http://127.0.0.1:" + server.getAddress().getPort(),
                "ostris-ledger-delivery", secret, "idax-ledger", Duration.ofSeconds(1),
                Duration.ofSeconds(2), Duration.ofSeconds(45), Duration.ofSeconds(5), 10,
                Duration.ofMinutes(2), Duration.ofSeconds(10), Duration.ofMinutes(15));
        return new OstrisLedgerDeliveryConfig().ostrisServiceTokenProvider(properties);
    }

    private HttpServer server(com.sun.net.httpserver.HttpHandler handler) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/service-auth/token", exchange -> {
            try {
                handler.handle(exchange);
            } finally {
                exchange.close();
            }
        });
        server.start();
        return server;
    }
}
