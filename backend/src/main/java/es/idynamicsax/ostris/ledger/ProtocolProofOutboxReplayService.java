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

    public void replay(CurrentUser operator, UUID outboxId, String reason) {
        // A service principal (e.g. the ledger delivery worker itself) authenticating with its
        // own client credentials is not "an explicit operator action" - this is a human-reviewed
        // decision, so it requires a real user identity, never a machine one, regardless of
        // which permissions happen to be granted to it.
        if (operator == null || operator.isService()) {
            throw new ProtocolProofOutboxStore.ReplayNotAllowedException("OPERATOR_IDENTITY_REQUIRED");
        }
        outbox.replayFailedProof(outboxId, operator.getTenantId(), operator.getUsername(), reason);
    }
}
