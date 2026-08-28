package es.idynamicsax.ostris.persistence;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "community", schema = "ostris")
public class CommunityEntity {
    @Id private UUID id;
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(nullable = false) private String name;
    @Column(name = "next_sequence", nullable = false) private long nextSequence;
    @Version private long version;
    protected CommunityEntity() {}
    public CommunityEntity(UUID id, UUID tenantId, String name) { this.id=id; this.tenantId=tenantId; this.name=name; this.nextSequence=1; }
    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public long getNextSequence() { return nextSequence; }
    public long allocateSequence() { return nextSequence++; }
}
