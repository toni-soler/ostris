package es.idynamicsax.ostris.ledger;

import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

public final class HttpLedgerProofClient implements LedgerProofClient {
    private final RestClient client;

    public HttpLedgerProofClient(RestClient client) {
        this.client = client;
    }

    @Override
    public CreatedProof get(String bearerToken, UUID proofId) {
        CreatedProof result = client.get().uri("/api/ledger/proofs/{id}", proofId)
                .headers(headers -> headers.setBearerAuth(bearerToken))
                .retrieve().body(CreatedProof.class);
        if (result == null || result.id() == null) throw new IllegalStateException("Ledger returned no Proof");
        return result;
    }

    @Override
    public CreatedProof create(String bearerToken, String idempotencyKey, LedgerProofRequest request) {
        CreatedProof result = client.post().uri("/api/ledger/proofs")
                .headers(headers -> headers.setBearerAuth(bearerToken))
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve().body(CreatedProof.class);
        if (result == null || result.id() == null) throw new IllegalStateException("Ledger returned no Proof id");
        return result;
    }

    @Override
    public Verification verify(String bearerToken, UUID proofId, LedgerProofRequest request) {
        Verification result = client.post().uri("/api/ledger/proofs/{id}/verify", proofId)
                .headers(headers -> headers.setBearerAuth(bearerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve().body(Verification.class);
        if (result == null || result.ledger() == null) throw new IllegalStateException("Ledger returned no verification result");
        return result;
    }
}
