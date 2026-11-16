package es.idynamicsax.ostris.service;

import es.idynamicsax.ostris.core.ProtocolException;
import es.idynamicsax.ostris.core.UuidV7;
import es.idynamicsax.ostris.security.OperationApprovalGate;
import java.math.BigInteger;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Minimal public provisioning for an external application's economic setup: a Community/Unit
 * (rare, tenant-level) and a Participant+Account+Credential+Controller+1-of-1 AccountControlPolicy
 * for one caller (routine, per user). Every object this creates already exists as a normative
 * osTRIS concept (see V1__ostris_core.sql / CORE_WIRE_AND_DECISION_SEMANTICS_V0_1.md); this
 * service only exposes their creation, using UuidV7 identifiers and activation_sequence=0 (active
 * before any transaction commits), exactly as the existing test fixtures already do by hand.
 * addCredential/revokeCredential support the device-lifecycle case (add/revoke one credential for
 * an already-active controller, e.g. "authorize this new browser"); RiskSubject/IdentityAssurance
 * and governed multi-controller policies remain intentionally out of scope here - a caller needing
 * those provisions them directly against the normative tables/future richer endpoints, not through
 * this MVP convenience surface.
 *
 * <p><b>Controller ownership (authoritative boundary).</b> A controller belongs to the IDAX user
 * who activated it. That owner is recorded here, in the same transaction that creates the
 * controller, and every later credential mutation (add/revoke) is checked against it HERE - in the
 * authoritative economic service, not in STIR or the HTTP proxy, which cannot be the last word for
 * a caller that reaches osTRIS directly. The owner check is independent of the
 * OSTRIS_CREDENTIAL_MANAGE permission and of superuser status: a tenant-wide capability, or being
 * a platform superuser, never confers authority over another person's personal controller. See
 * ostris-doc CONTROLLER_OWNERSHIP.md and V6__controller_ownership.sql.
 */
@Service
public class ProvisioningService {
    private static final Pattern UNIT_CODE = Pattern.compile("^[A-Z][A-Z0-9_-]{0,15}$");
    private static final Pattern PUBLIC_KEY = Pattern.compile("^[A-Za-z0-9_-]{43}$");
    private final JdbcTemplate jdbc;
    private final OperationApprovalGate approvals;

    public ProvisioningService(JdbcTemplate jdbc, OperationApprovalGate approvals) { this.jdbc = jdbc; this.approvals = approvals; }

    @Transactional
    public CommunityCreated createCommunity(UUID tenant, String name) {
        if (name == null || name.isBlank()) throw new ProtocolException("INVALID_REQUEST", "Community name is required");
        UUID id = UuidV7.generate();
        jdbc.update("insert into ostris.community(id,tenant_id,name) values(?,?,?)", id, tenant, name.trim());
        return new CommunityCreated(id);
    }

    @Transactional
    public UnitCreated createUnit(UUID tenant, UUID community, String code, int scale) {
        requireCommunity(tenant, community);
        if (code == null || !UNIT_CODE.matcher(code).matches()) throw new ProtocolException("INVALID_UNIT_CODE", "Unit code must match [A-Z][A-Z0-9_-]{0,15}");
        if (scale < 0 || scale > 18) throw new ProtocolException("INVALID_UNIT_SCALE", "Unit scale must be 0..18");
        UUID id = UuidV7.generate();
        try {
            jdbc.update("insert into ostris.unit_of_account(id,tenant_id,community_id,code,scale) values(?,?,?,?,?)", id, tenant, community, code, scale);
        } catch (DataIntegrityViolationException ex) {
            throw new ProtocolException("UNIT_CODE_TAKEN", "Unit code already exists in this community");
        }
        return new UnitCreated(id, code, scale);
    }

    /**
     * Activates one participant for economic exchange: Participant + Account (given unit) +
     * Credential (the caller-supplied Ed25519 public key, already generated and held client-side -
     * this service never sees or generates a private key) + Controller + the binding between them
     * + a 1-of-1 AccountControlPolicy naming that controller as the account's sole authorizer.
     *
     * <p>The new controller is owned by {@code ownerUserId} (the authenticated caller). That
     * ownership is the single fact every later credential mutation is checked against.
     */
    @Transactional
    public ActivationResult activateParticipant(UUID tenant, UUID community, String displayName, UUID unit,
            String publicKeyBase64url, BigInteger creditFloor, UUID ownerUserId) {
        requireCommunity(tenant, community);
        requireUnit(tenant, community, unit);
        UUID owner = requireOwnerUser(ownerUserId);
        if (displayName == null || displayName.isBlank()) throw new ProtocolException("INVALID_REQUEST", "Display name is required");
        if (publicKeyBase64url == null || !PUBLIC_KEY.matcher(publicKeyBase64url).matches())
            throw new ProtocolException("INVALID_BASE64URL", "Public key must be a canonical 32-byte base64url value");
        BigInteger floor = creditFloor == null ? new BigInteger("-100000") : creditFloor;
        if (floor.signum() > 0) throw new ProtocolException("INVALID_CREDIT_FLOOR", "Credit floor must be zero or negative");

        UUID participant = UuidV7.generate();
        jdbc.update("insert into ostris.participant(id,tenant_id,community_id,display_name) values(?,?,?,?)",
            participant, tenant, community, displayName.trim());

        UUID account = UuidV7.generate();
        jdbc.update("insert into ostris.account(id,tenant_id,community_id,unit_id,participant_id,account_type,name,credit_floor)"
            + " values(?,?,?,?,?,'INDIVIDUAL',?,?)", account, tenant, community, unit, participant, displayName.trim(), floor);

        UUID credential = UuidV7.generate();
        try {
            jdbc.update("insert into ostris.credential(id,tenant_id,community_id,public_key_base64url,algorithm,activation_sequence)"
                + " values(?,?,?,?,'Ed25519',0)", credential, tenant, community, publicKeyBase64url);
        } catch (DataIntegrityViolationException ex) {
            throw new ProtocolException("CREDENTIAL_KEY_TAKEN", "This public key is already registered in this community");
        }

        UUID controller = UuidV7.generate();
        jdbc.update("insert into ostris.protocol_controller(id,tenant_id,community_id,activation_sequence,"
            + "owner_user_id,owner_established_by,owner_established_sequence) values(?,?,?,0,?,'ACTIVATION',0)",
            controller, tenant, community, owner);
        jdbc.update("insert into ostris.controller_credential_binding(credential_id,tenant_id,community_id,controller_id,public_key_base64url,activation_sequence)"
            + " values(?,?,?,?,?,0)", credential, tenant, community, controller, publicKeyBase64url);

        UUID policy = UuidV7.generate();
        jdbc.update("insert into ostris.account_control_policy(id,tenant_id,community_id,account_id,policy_version,threshold,activation_sequence)"
            + " values(?,?,?,?,1,1,0)", policy, tenant, community, account);
        jdbc.update("insert into ostris.account_controller(tenant_id,control_policy_id,controller_id) values(?,?,?)", tenant, policy, controller);

        return new ActivationResult(participant, account, controller, credential, unit, floor.toString(), "0");
    }

    /**
     * Binds an ADDITIONAL credential to an ALREADY-ACTIVE controller (device lifecycle: "add this
     * browser/device as a signer"). Never creates a new controller or touches AccountControlPolicy -
     * multiple active credentials for one controller already count once at threshold evaluation
     * (CORE_WIRE_AND_DECISION_SEMANTICS_V0_1.md s.103), so this is enough for N devices to satisfy
     * an existing 1-of-1 (or any) policy without loosening it. activation_sequence is set to
     * (community's current next_sequence - 1) - the same "already active" convention genesis
     * credentials use with activation_sequence=0 (next_sequence starts at 1) - so the device is
     * eligible starting with the VERY NEXT commit, not one cycle later: eligibility requires
     * activation_sequence < S for the commit's own sequence S, and S == next_sequence at the moment
     * that commit is evaluated, so activation_sequence must be strictly below the CURRENT
     * next_sequence, not equal to it.
     *
     * <p>Only the controller's owner may add a credential to it. The check is on recorded ownership,
     * not on the OSTRIS_CREDENTIAL_MANAGE permission or superuser status. Where the deployment
     * requires it, the owner must also present a per-operation approval for exactly this controller
     * and this public key ({@link OperationApprovalGate}); it is consumed in this transaction.
     */
    @Transactional
    public CredentialAdded addCredential(UUID tenant, UUID community, UUID controller, String publicKeyBase64url, UUID actorUserId, String approvalToken) {
        requireCommunity(tenant, community);
        requireActiveController(tenant, community, controller);
        UUID owner = requireOwnerUser(actorUserId);
        requireControllerOwner(tenant, community, controller, owner);
        if (publicKeyBase64url == null || !PUBLIC_KEY.matcher(publicKeyBase64url).matches())
            throw new ProtocolException("INVALID_BASE64URL", "Public key must be a canonical 32-byte base64url value");
        approvals.requireApproval(approvalToken, owner, tenant, OperationApprovalGate.CREDENTIAL_ADD, controller.toString(),
            OperationApprovalGate.credentialAddIntent(tenant, community, controller, publicKeyBase64url));
        long sequence = currentSequence(tenant, community) - 1;
        UUID credential = UuidV7.generate();
        try {
            jdbc.update("insert into ostris.credential(id,tenant_id,community_id,public_key_base64url,algorithm,activation_sequence)"
                + " values(?,?,?,?,'Ed25519',?)", credential, tenant, community, publicKeyBase64url, sequence);
        } catch (DataIntegrityViolationException ex) {
            throw new ProtocolException("CREDENTIAL_KEY_TAKEN", "This public key is already registered in this community");
        }
        jdbc.update("insert into ostris.controller_credential_binding(credential_id,tenant_id,community_id,controller_id,public_key_base64url,activation_sequence)"
            + " values(?,?,?,?,?,?)", credential, tenant, community, controller, publicKeyBase64url, sequence);
        return new CredentialAdded(credential, controller, sequence);
    }

    /**
     * Revokes one credential (never the controller itself - other credentials/devices keep working).
     * Only the owner of the controller the credential is bound to may revoke it; a credential that
     * is bound to no controller cannot be revoked through this surface (there is no owner to check).
     */
    @Transactional
    public void revokeCredential(UUID tenant, UUID community, UUID credential, UUID actorUserId, String approvalToken) {
        requireCommunity(tenant, community);
        UUID owner = requireOwnerUser(actorUserId);
        UUID controller = jdbc.query(
            "select controller_id from ostris.controller_credential_binding where credential_id=? and tenant_id=? and community_id=?",
            (rs, n) -> rs.getObject(1, UUID.class), credential, tenant, community).stream().findFirst().orElse(null);
        if (controller == null) {
            Integer exists = jdbc.queryForObject("select count(*) from ostris.credential where id=? and tenant_id=? and community_id=?", Integer.class, credential, tenant, community);
            if (exists == null || exists == 0) throw new ProtocolException("UNKNOWN_CREDENTIAL", "Credential not found");
            // A credential with no controller binding has no owner to authorize its revocation.
            throw new ProtocolException("CONTROLLER_OWNERSHIP_UNASSIGNED", "Credential is not bound to an owned controller");
        }
        requireControllerOwner(tenant, community, controller, owner);
        approvals.requireApproval(approvalToken, owner, tenant, OperationApprovalGate.CREDENTIAL_REVOKE, credential.toString(),
            OperationApprovalGate.credentialRevokeIntent(tenant, community, credential));
        long sequence = currentSequence(tenant, community);
        int updated = jdbc.update("update ostris.credential set revocation_sequence=? where id=? and tenant_id=? and community_id=? and revocation_sequence is null",
            sequence, credential, tenant, community);
        if (updated == 0) throw new ProtocolException("CREDENTIAL_ALREADY_REVOKED", "Credential is already revoked");
    }

    private void requireCommunity(UUID tenant, UUID community) {
        Integer count = jdbc.queryForObject("select count(*) from ostris.community where tenant_id=? and id=?", Integer.class, tenant, community);
        if (count == null || count != 1) throw new ProtocolException("UNKNOWN_COMMUNITY", "Community not found");
    }
    private void requireUnit(UUID tenant, UUID community, UUID unit) {
        Integer count = jdbc.queryForObject("select count(*) from ostris.unit_of_account where tenant_id=? and community_id=? and id=?", Integer.class, tenant, community, unit);
        if (count == null || count != 1) throw new ProtocolException("UNKNOWN_UNIT", "Unit not found in this community");
    }
    private void requireActiveController(UUID tenant, UUID community, UUID controller) {
        Integer count = jdbc.queryForObject("select count(*) from ostris.protocol_controller where tenant_id=? and community_id=? and id=? and revocation_sequence is null",
            Integer.class, tenant, community, controller);
        if (count == null || count != 1) throw new ProtocolException("UNKNOWN_CONTROLLER", "Controller not found or already revoked");
    }
    private static UUID requireOwnerUser(UUID userId) {
        // Credential provisioning is a personal action: it needs a real authenticated end user, not
        // an anonymous or service principal. The caller resolves this from the authenticated token.
        if (userId == null) throw new ProtocolException("USER_REQUIRED", "An authenticated user is required for this operation");
        return userId;
    }
    /**
     * The authoritative ownership gate. A controller is a personal object; only the user who owns it
     * may manage its credentials. An unowned (pre-ownership) controller is fail-closed. This never
     * consults the permission layer or superuser flag on purpose - neither is authority over someone
     * else's controller.
     */
    private void requireControllerOwner(UUID tenant, UUID community, UUID controller, UUID actorUserId) {
        UUID owner = jdbc.query(
            "select owner_user_id from ostris.protocol_controller where tenant_id=? and community_id=? and id=?",
            (rs, n) -> rs.getObject(1, UUID.class), tenant, community, controller).stream().findFirst()
            .orElseThrow(() -> new ProtocolException("UNKNOWN_CONTROLLER", "Controller not found"));
        if (owner == null)
            throw new ProtocolException("CONTROLLER_OWNERSHIP_UNASSIGNED",
                "This controller predates ownership and cannot be managed until ownership is re-established out of band");
        if (!owner.equals(actorUserId))
            throw new ProtocolException("CONTROLLER_NOT_OWNED", "This controller belongs to another user");
    }
    private long currentSequence(UUID tenant, UUID community) {
        Long sequence = jdbc.queryForObject("select next_sequence from ostris.community where tenant_id=? and id=?", Long.class, tenant, community);
        if (sequence == null) throw new ProtocolException("UNKNOWN_COMMUNITY", "Community not found");
        return sequence;
    }

    public record CommunityCreated(UUID communityId) {}
    public record UnitCreated(UUID unitId, String code, int scale) {}
    public record ActivationResult(UUID participantId, UUID accountId, UUID controllerId, UUID credentialId,
        UUID unitId, String creditFloor, String balanceProjection) {}
    public record CredentialAdded(UUID credentialId, UUID controllerId, long activationSequence) {}
}
