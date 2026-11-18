package es.idynamicsax.ostris.service;

import es.idynamicsax.ostris.core.ProtocolException;
import es.idynamicsax.ostris.core.ResolutionBasisType;
import es.idynamicsax.ostris.core.UuidV7;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application surface for the two governance inputs that adjudicated transactions already require:
 * a community resolution authority (a fixed set of controllers and a threshold, versioned) and a
 * resolution basis (an identifier whose finality the commit gate checks).
 *
 * <p>osTRIS does not decide cases. A caller records that a basis exists and when it became final
 * or was overturned; the adjudicated transaction still needs threshold signatures from the
 * authority's own controllers over the exact governed payload, and the commit gate re-checks the
 * authority, the basis finality and every signature at the commit sequence. Recording a basis is
 * therefore an assertion by an authorized caller, not a verifiable proof of an external process.
 */
@Service
public class ResolutionService {
    private final JdbcTemplate jdbc;

    public ResolutionService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional
    public AuthorityView registerAuthority(UUID tenant, UUID community, List<UUID> controllerIds, int threshold) {
        long next = lockedNextSequence(tenant, community);
        LinkedHashSet<UUID> controllers = new LinkedHashSet<>(controllerIds == null ? List.of() : controllerIds);
        if (controllers.isEmpty() || controllers.size() != controllerIds.size())
            throw new ProtocolException("INVALID_RESOLUTION_AUTHORITY", "Controllers must be a non-empty set without repetitions");
        if (threshold < 1 || threshold > controllers.size())
            throw new ProtocolException("INVALID_RESOLUTION_AUTHORITY", "Threshold must be between 1 and the number of controllers");
        for (UUID controller : controllers) {
            Integer active = jdbc.queryForObject("select count(*) from ostris.protocol_controller where tenant_id=? and community_id=? and id=? and revocation_sequence is null",
                Integer.class, tenant, community, controller);
            if (active == null || active != 1) throw new ProtocolException("UNKNOWN_CONTROLLER", "Controller not found or revoked: " + controller);
        }
        // Effective from the next committed sequence, like a credential activation.
        long sequence = next - 1;
        jdbc.update("update ostris.community_resolution_authority set revocation_sequence=? where tenant_id=? and community_id=? and revocation_sequence is null",
            sequence, tenant, community);
        Integer previous = jdbc.queryForObject("select coalesce(max(authority_version),0) from ostris.community_resolution_authority where tenant_id=? and community_id=?",
            Integer.class, tenant, community);
        int version = (previous == null ? 0 : previous) + 1;
        UUID id = UuidV7.generate();
        jdbc.update("insert into ostris.community_resolution_authority(id,tenant_id,community_id,authority_version,threshold,activation_sequence) values(?,?,?,?,?,?)",
            id, tenant, community, version, threshold, sequence);
        for (UUID controller : controllers)
            jdbc.update("insert into ostris.community_resolution_controller(tenant_id,authority_id,controller_id) values(?,?,?)", tenant, id, controller);
        return new AuthorityView(id, version, threshold, new ArrayList<>(controllers), sequence, null);
    }

    @Transactional(readOnly = true)
    public AuthorityView currentAuthority(UUID tenant, UUID community) {
        requireCommunity(tenant, community);
        return jdbc.query("select id,authority_version,threshold,activation_sequence,revocation_sequence from ostris.community_resolution_authority"
                + " where tenant_id=? and community_id=? and revocation_sequence is null order by authority_version desc limit 1",
            (rs, n) -> new AuthorityView(rs.getObject(1, UUID.class), rs.getInt(2), rs.getInt(3), controllers(tenant, rs.getObject(1, UUID.class)),
                rs.getLong(4), null), tenant, community).stream().findFirst()
            .orElseThrow(() -> new ProtocolException("NO_RESOLUTION_AUTHORITY", "The community has no effective resolution authority"));
    }

    /** Registers a basis as PENDING. Idempotent for the same id and type. */
    @Transactional
    public BasisView recordBasis(UUID tenant, UUID community, UUID basisId, ResolutionBasisType type) {
        requireCommunity(tenant, community);
        if (basisId == null || type == null) throw new ProtocolException("INVALID_RESOLUTION_BASIS", "Basis id and type are required");
        BasisView existing = find(tenant, basisId);
        if (existing != null) {
            if (!existing.communityId().equals(community) || existing.basisType() != type)
                throw new ProtocolException("RESOLUTION_BASIS_CONFLICT", "Basis id already exists with a different scope or type");
            return existing;
        }
        jdbc.update("insert into ostris.resolution_basis(id,tenant_id,community_id,basis_type,status) values(?,?,?,?,'PENDING')",
            basisId, tenant, community, type.name());
        return find(tenant, basisId);
    }

    /** PENDING -> FINAL, effective from the next committed sequence. Idempotent once FINAL. */
    @Transactional
    public BasisView finalizeBasis(UUID tenant, UUID community, UUID basisId) {
        long next = lockedNextSequence(tenant, community);
        BasisView basis = require(tenant, community, basisId);
        if ("FINAL".equals(basis.status())) return basis;
        if (!"PENDING".equals(basis.status())) throw new ProtocolException("RESOLUTION_BASIS_NOT_PENDING", "Only a PENDING basis can become FINAL");
        jdbc.update("update ostris.resolution_basis set status='FINAL',finality_sequence=? where id=? and tenant_id=?", next - 1, basisId, tenant);
        return find(tenant, basisId);
    }

    /** PENDING or FINAL -> OVERTURNED. Already committed transactions stay committed. */
    @Transactional
    public BasisView overturnBasis(UUID tenant, UUID community, UUID basisId) {
        lockedNextSequence(tenant, community);
        BasisView basis = require(tenant, community, basisId);
        if ("OVERTURNED".equals(basis.status())) return basis;
        jdbc.update("update ostris.resolution_basis set status='OVERTURNED' where id=? and tenant_id=?", basisId, tenant);
        return find(tenant, basisId);
    }

    @Transactional(readOnly = true)
    public BasisView basis(UUID tenant, UUID community, UUID basisId) { return require(tenant, community, basisId); }

    private BasisView require(UUID tenant, UUID community, UUID basisId) {
        BasisView basis = find(tenant, basisId);
        if (basis == null || !basis.communityId().equals(community))
            throw new ProtocolException("UNKNOWN_RESOLUTION_BASIS", "Resolution basis not found");
        return basis;
    }

    private BasisView find(UUID tenant, UUID basisId) {
        return jdbc.query("select id,community_id,basis_type,status,finality_sequence from ostris.resolution_basis where id=? and tenant_id=?",
            (rs, n) -> new BasisView(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), ResolutionBasisType.valueOf(rs.getString(3)),
                rs.getString(4), (Long) rs.getObject(5)), basisId, tenant).stream().findFirst().orElse(null);
    }

    private List<UUID> controllers(UUID tenant, UUID authority) {
        return jdbc.queryForList("select controller_id from ostris.community_resolution_controller where tenant_id=? and authority_id=? order by controller_id",
            UUID.class, tenant, authority);
    }

    private void requireCommunity(UUID tenant, UUID community) {
        Integer count = jdbc.queryForObject("select count(*) from ostris.community where tenant_id=? and id=?", Integer.class, tenant, community);
        if (count == null || count != 1) throw new ProtocolException("UNKNOWN_COMMUNITY", "Community not found");
    }

    /** Serializes with commits, which lock the same community row while they read the sequence. */
    private long lockedNextSequence(UUID tenant, UUID community) {
        return jdbc.query("select next_sequence from ostris.community where tenant_id=? and id=? for update",
                (rs, n) -> rs.getLong(1), tenant, community).stream().findFirst()
            .orElseThrow(() -> new ProtocolException("UNKNOWN_COMMUNITY", "Community not found"));
    }

    public record AuthorityView(UUID authorityId, int authorityVersion, int threshold, List<UUID> controllerIds,
                                long activationSequence, Long revocationSequence) {}
    public record BasisView(UUID basisId, UUID communityId, ResolutionBasisType basisType, String status, Long finalitySequence) {}
}
