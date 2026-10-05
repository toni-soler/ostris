package es.idynamicsax.ostris.ledger;

import es.idynamicsax.idax.security.CurrentUser;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * The one reviewed path back from a terminal FAILED_PERMANENT ledger delivery - deliberately
 * narrow (replay exactly one identified row, nothing else) rather than a generic outbox-edit
 * capability. See ProtocolProofOutboxStore.replayFailedProof for what it actually does and why.
 */
@Service
public class ProtocolProofOutboxReplayService {
    private final ProtocolProofOutboxStore outbox;

    public ProtocolProofOutboxReplayService(ProtocolProofOutboxStore outbox) {
        this.outbox = outbox;
    }

    public void replay(CurrentUser operator, UUID tenantId, UUID outboxId, String reason) {
        // A service principal (e.g. the ledger delivery worker itself) authenticating with its
        // own client credentials is not "an explicit operator action" - this is a human-reviewed
        // decision, so it requires a real user identity, never a machine one, regardless of
        // which permissions happen to be granted to it.
        if (operator == null || operator.isService()) {
            throw new ProtocolProofOutboxStore.ReplayNotAllowedException("OPERATOR_IDENTITY_REQUIRED");
        }
        // tenantId is the controller's resolved TenantContext.get().getTenantId(), not
        // operator.getTenantId(): OstrisJwtAuthFilter only ever puts the token's own (often null,
        // for a superuser) tenant claim on the CurrentUser principal itself - the X-Tenant-resolved
        // tenant a superuser actually asked to act on only ever reaches TenantContext, exactly the
        // same split every other ostris controller (TransactionController,
        // IdentityContinuityController) already works around by reading TenantContext, never
        // CurrentUser, for "which tenant is this request for".
        outbox.replayFailedProof(outboxId, tenantId, operator.getUsername(), reason);
    }
}
