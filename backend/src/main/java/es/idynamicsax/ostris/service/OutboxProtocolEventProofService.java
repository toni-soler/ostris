package es.idynamicsax.ostris.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import es.idynamicsax.ostris.core.OstrisWireCodec;
import es.idynamicsax.ostris.core.ProtocolException;
import es.idynamicsax.ostris.proof.CommittedJournalTransaction;
import es.idynamicsax.ostris.proof.ProtocolEventProofBuilder;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class OutboxProtocolEventProofService implements ProtocolEventProofService {
    private final JdbcTemplate jdbc;
    private final ProtocolEventProofBuilder builder;

    public OutboxProtocolEventProofService(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.builder = new ProtocolEventProofBuilder(mapper, new OstrisWireCodec());
    }

    @Override
    public void record(UUID tenantId, UUID communityId, UUID transactionId, String protocolDigest) {
        CommittedJournalTransaction source = jdbc.queryForObject("""
                SELECT tenant_id, community_id, unit_id, id, community_sequence,
                       status, canonical_json, protocol_digest
                FROM ostris.journal_transaction
                WHERE tenant_id = ? AND community_id = ? AND id = ?
                """, (rs, row) -> new CommittedJournalTransaction(
                rs.getObject("tenant_id", UUID.class),
                rs.getObject("community_id", UUID.class),
                rs.getObject("unit_id", UUID.class),
                rs.getObject("id", UUID.class),
                rs.getLong("community_sequence"),
                rs.getString("status"),
                rs.getString("canonical_json"),
                rs.getString("protocol_digest")), tenantId, communityId, transactionId);
        if (source == null || !protocolDigest.equals(source.persistedProtocolDigest())) {
            throw new ProtocolException("PROTOCOL_DIGEST_MISMATCH", "Journal and outbox protocolDigest differ");
        }
        var proof = builder.build(source);
        jdbc.update("""
                INSERT INTO ostris.protocol_proof_outbox(
                    id, tenant_id, community_id, transaction_id, protocol_digest, status,
                    proof_digest, external_reference, canonicalization_profile)
                VALUES (?, ?, ?, ?, ?, 'PENDING', ?, ?, ?)
                """, UUID.randomUUID(), tenantId, communityId, transactionId, protocolDigest,
                proof.proofDigest(), proof.externalReference(), proof.canonicalizationProfile());
    }
}
