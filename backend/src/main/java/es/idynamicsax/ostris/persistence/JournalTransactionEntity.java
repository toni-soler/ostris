package es.idynamicsax.ostris.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="journal_transaction", schema="ostris")
public class JournalTransactionEntity {
    @Id private UUID id;
    @Column(name="tenant_id",nullable=false) private UUID tenantId;
    @Column(name="community_id",nullable=false) private UUID communityId;
    @Column(name="unit_id",nullable=false) private UUID unitId;
    @Column(name="community_sequence",nullable=false) private long communitySequence;
    @Column(nullable=false) private String purpose;
    @Column(name="protocol_digest",nullable=false) private String protocolDigest;
    @Column(name="committed_at",nullable=false) private Instant committedAt;
    protected JournalTransactionEntity() {}
    public UUID getId(){return id;} public long getCommunitySequence(){return communitySequence;} public String getProtocolDigest(){return protocolDigest;}
}
