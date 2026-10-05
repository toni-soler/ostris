package es.idynamicsax.ostris.api;

import es.idynamicsax.idax.security.CurrentUser;
import es.idynamicsax.idax.tenant.TenantContext;
import es.idynamicsax.ostris.ledger.ProtocolProofOutboxReplayService;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Reviewed replay of a terminal FAILED_PERMANENT ledger delivery - a narrow, single-purpose
 * command (see ProtocolProofOutboxReplayService/ProtocolProofOutboxStore.replayFailedProof), not
 * a generic outbox-edit tool. Deliberately gated by its own permission
 * (OSTRIS_LEDGER_OUTBOX_REPLAY), separate from every proof-delivery permission a service
 * principal might hold, and the service itself additionally refuses a service-principal caller
 * regardless of permissions: this is a human operator's reviewed decision.
 */
@RestController
@RequestMapping("/api/ostris/admin/ledger-outbox")
public class LedgerOutboxAdminController {
    private final ProtocolProofOutboxReplayService replay;

    public LedgerOutboxAdminController(ProtocolProofOutboxReplayService replay) {
        this.replay = replay;
    }

    @PostMapping("/{id}/replay")
    @PreAuthorize("@permissionService.hasPermission('OSTRIS_LEDGER_OUTBOX_REPLAY')")
    public ResponseEntity<Void> replay(@PathVariable UUID id, @RequestBody(required = false) ReplayRequest request,
            @AuthenticationPrincipal CurrentUser operator) {
        replay.replay(operator, TenantContext.get().getTenantId(), id, request == null ? null : request.reason());
        return ResponseEntity.noContent().build();
    }

    public record ReplayRequest(String reason) {}
}
