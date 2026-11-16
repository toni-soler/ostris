package es.idynamicsax.ostris.service;

import es.idynamicsax.ostris.core.OstrisWireCodec;
import es.idynamicsax.ostris.core.ProtocolException;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Minimal public read/discovery surface for external applications (e.g. STIR): communities,
 * units of account, account balances and credential/control status, plus transaction
 * status/receipt and the canonical bytes a client needs to produce an AccountAuthorization
 * signature. Never exposes RiskSubject/IdentityAssurance/Finding internals or private keys.
 */
@Service
public class DiscoveryService {
    private final JdbcTemplate jdbc;

    public DiscoveryService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(readOnly = true)
    public List<CommunityView> communities(UUID tenant) {
        return jdbc.query("select id,name,next_sequence from ostris.community where tenant_id=? order by name",
            (rs, n) -> new CommunityView(rs.getObject(1, UUID.class), rs.getString(2), rs.getLong(3)), tenant);
    }

    @Transactional(readOnly = true)
    public CommunityView community(UUID tenant, UUID community) {
        return jdbc.query("select id,name,next_sequence from ostris.community where tenant_id=? and id=?",
            (rs, n) -> new CommunityView(rs.getObject(1, UUID.class), rs.getString(2), rs.getLong(3)), tenant, community)
            .stream().findFirst().orElseThrow(() -> new ProtocolException("UNKNOWN_COMMUNITY", "Community not found"));
    }

    @Transactional(readOnly = true)
    public List<UnitView> units(UUID tenant, UUID community) {
        return jdbc.query("select id,code,scale from ostris.unit_of_account where tenant_id=? and community_id=? order by code",
            (rs, n) -> new UnitView(rs.getObject(1, UUID.class), rs.getString(2), rs.getInt(3)), tenant, community);
    }

    @Transactional(readOnly = true)
    public UnitView unit(UUID tenant, UUID community, UUID unit) {
        return jdbc.query("select id,code,scale from ostris.unit_of_account where tenant_id=? and community_id=? and id=?",
            (rs, n) -> new UnitView(rs.getObject(1, UUID.class), rs.getString(2), rs.getInt(3)), tenant, community, unit)
            .stream().findFirst().orElseThrow(() -> new ProtocolException("UNKNOWN_UNIT", "Unit not found"));
    }

    @Transactional(readOnly = true)
    public AccountView account(UUID tenant, UUID community, UUID account) {
        return jdbc.query("select id,unit_id,participant_id,account_type,name,credit_floor,balance_projection,risk_state"
            + " from ostris.account where tenant_id=? and community_id=? and id=?",
            (rs, n) -> new AccountView(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getObject(3, UUID.class),
                rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8)),
            tenant, community, account).stream().findFirst()
            .orElseThrow(() -> new ProtocolException("UNKNOWN_ACCOUNT", "Account not found"));
    }

    @Transactional(readOnly = true)
    public CredentialView credential(UUID tenant, UUID community, UUID credential) {
        return jdbc.query("select c.id,c.public_key_base64url,c.algorithm,c.activation_sequence,c.revocation_sequence,b.controller_id"
            + " from ostris.credential c left join ostris.controller_credential_binding b on b.credential_id=c.id and b.tenant_id=c.tenant_id"
            + " where c.tenant_id=? and c.community_id=? and c.id=?",
            (rs, n) -> new CredentialView(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                rs.getLong(4), (Long) rs.getObject(5), (UUID) rs.getObject(6, UUID.class)),
            tenant, community, credential).stream().findFirst()
            .orElseThrow(() -> new ProtocolException("UNKNOWN_CREDENTIAL", "Credential not found"));
    }

    @Transactional(readOnly = true)
    public List<CredentialView> credentialsForController(UUID tenant, UUID community, UUID controller) {
        return jdbc.query("select c.id,c.public_key_base64url,c.algorithm,c.activation_sequence,c.revocation_sequence,b.controller_id"
            + " from ostris.credential c join ostris.controller_credential_binding b on b.credential_id=c.id and b.tenant_id=c.tenant_id"
            + " where c.tenant_id=? and c.community_id=? and b.controller_id=? order by c.activation_sequence",
            (rs, n) -> new CredentialView(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                rs.getLong(4), (Long) rs.getObject(5), (UUID) rs.getObject(6, UUID.class)),
            tenant, community, controller);
    }

    @Transactional(readOnly = true)
    public TransactionStatusView transaction(UUID tenant, UUID id) {
        var row = jdbc.query("select id,community_id,unit_id,purpose,authorization_payload,authorization_digest,status,committed_sequence"
            + " from ostris.transaction_proposal where tenant_id=? and id=?",
            (rs, n) -> new Object[]{rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getObject(3, UUID.class),
                rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7), rs.getObject(8)},
            tenant, id).stream().findFirst().orElseThrow(() -> new ProtocolException("UNKNOWN_PROPOSAL", "Proposal not found"));
        List<UUID> authorized = jdbc.queryForList(
            "select account_id from ostris.authorization_evidence where tenant_id=? and proposal_id=? order by created_at",
            UUID.class, tenant, id);
        var journal = jdbc.query("select protocol_digest,committed_at from ostris.journal_transaction where tenant_id=? and id=?",
            (rs, n) -> new Object[]{rs.getString(1), rs.getTimestamp(2).toInstant()}, tenant, id).stream().findFirst();
        String protocolDigest = journal.map(j -> (String) j[0]).orElse(null);
        java.time.Instant committedAt = journal.map(j -> (java.time.Instant) j[1]).orElse(null);
        return new TransactionStatusView((UUID) row[0], (UUID) row[1], (UUID) row[2], (String) row[3],
            OstrisWireCodec.CANONICALIZATION_SCHEME, "0.1", (String) row[4], (String) row[5],
            (String) row[6], (Long) row[7], protocolDigest, committedAt, authorized);
    }

    public record CommunityView(UUID communityId, String name, long nextSequence) {}
    public record UnitView(UUID unitId, String code, int scale) {}
    public record AccountView(UUID accountId, UUID unitId, UUID participantId, String accountType, String name,
        String creditFloor, String balanceProjection, String riskState) {}
    public record CredentialView(UUID credentialId, String publicKeyBase64url, String algorithm,
        long activationSequence, Long revocationSequence, UUID controllerId) {}
    public record TransactionStatusView(UUID transactionId, UUID communityId, UUID unitId, String purpose,
        String wireFormat, String ostrisCoreVersion, String authorizationPayload, String authorizationDigest,
        String status, Long committedSequence, String protocolDigest, java.time.Instant committedAt, List<UUID> authorizedAccountIds) {}
}
