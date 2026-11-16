package es.idynamicsax.ostris.security;

import es.idynamicsax.idax.security.approval.OperationApprovalException;
import es.idynamicsax.idax.security.approval.OperationApprovalVerifier;
import es.idynamicsax.idax.security.approval.OperationIntentDigest;
import es.idynamicsax.ostris.core.ProtocolException;
import java.sql.Timestamp;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * osTRIS's enforcement point for per-operation approvals on credential management.
 *
 * <p>When {@code ostris.security.credential-approval.required=true}, adding or revoking a credential
 * needs - in addition to controller ownership, never instead of it - an approval the owner obtained
 * with a fresh authenticator code for exactly this change. The intent digest is recomputed here from
 * what is about to be written (tenant, community, controller and the exact public key, or the exact
 * credential to revoke); an approval for a different key, controller, credential, action, tenant or
 * user does not match. It is consumed in the caller's transaction through a primary-key insert, so
 * it is spent once, and only if the change itself commits.
 *
 * <p>There is no bypass branch: not for superusers, not for a missing header from an older client,
 * not for recovery codes (the issuer never accepts them). A stolen session alone can no longer bind
 * a new signing device. The requirement is a deployment decision because it needs two-step
 * verification to be active for the community's users; it is off by default and fails closed at
 * startup if switched on without the means to verify.
 */
@Component
public class OperationApprovalGate {
    public static final String CREDENTIAL_ADD = "OSTRIS_CREDENTIAL_ADD";
    public static final String CREDENTIAL_REVOKE = "OSTRIS_CREDENTIAL_REVOKE";

    private final boolean required;
    private final OperationApprovalVerifier verifier;
    private final JdbcTemplate jdbc;

    public OperationApprovalGate(@Value("${ostris.security.credential-approval.required:false}") boolean required,
                                 ObjectProvider<OperationApprovalVerifier> verifier, JdbcTemplate jdbc) {
        this.required = required;
        this.verifier = verifier.getIfAvailable();
        this.jdbc = jdbc;
        if (required && this.verifier == null)
            throw new IllegalStateException("ostris.security.credential-approval.required=true needs an OperationApprovalVerifier (LOCAL token verification)");
    }

    public boolean required() { return required; }

    public static String credentialAddIntent(UUID tenant, UUID community, UUID controller, String publicKeyBase64url) {
        return OperationIntentDigest.of(CREDENTIAL_ADD, tenant.toString(), community.toString(), controller.toString(), publicKeyBase64url);
    }

    public static String credentialRevokeIntent(UUID tenant, UUID community, UUID credential) {
        return OperationIntentDigest.of(CREDENTIAL_REVOKE, tenant.toString(), community.toString(), credential.toString());
    }

    /** Must be called inside the transaction that performs the change. No-op when not required. */
    public void requireApproval(String approvalToken, UUID userId, UUID tenant, String action, String resource, String intentDigest) {
        if (!required) return;
        try {
            verifier.verifyAndConsume(approvalToken, userId, tenant, action, resource, intentDigest,
                (approvalId, approvedUser, approvedTenant, approvedAction, expiresAt) -> jdbc.update(
                    "insert into ostris.operation_approval_consumption(approval_id,tenant_id,user_id,action,expires_at)"
                        + " values(?,?,?,?,?) on conflict (approval_id) do nothing",
                    approvalId, approvedTenant, approvedUser, approvedAction, Timestamp.from(expiresAt)) == 1);
        } catch (OperationApprovalException e) {
            throw new ProtocolException(e.code(), "This operation needs a fresh approval for exactly this change");
        }
    }
}
