package es.idynamicsax.ostris.ledger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import es.idynamicsax.idax.security.CurrentUser;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProtocolProofOutboxReplayServiceTest {
    private final ProtocolProofOutboxStore outbox = mock(ProtocolProofOutboxStore.class);
    private final ProtocolProofOutboxReplayService service = new ProtocolProofOutboxReplayService(outbox);

    @Test void delegatesToTheStoreWithTheOperatorsOwnTenantAndUsername() {
        UUID tenantId = UUID.randomUUID();
        UUID outboxId = UUID.randomUUID();
        CurrentUser operator = new CurrentUser(UUID.randomUUID(), "ana@stir.test", tenantId, false, Set.of("ROLE_ADMIN"));

        service.replay(operator, outboxId, "shell fix deployed");

        verify(outbox).replayFailedProof(outboxId, tenantId, "ana@stir.test", "shell fix deployed");
    }

    @Test void refusesAServicePrincipalCallerEvenIfItHoldsThePermission() {
        CurrentUser servicePrincipal = new CurrentUser(null, "ostris-ledger-delivery", UUID.randomUUID(), false,
                Set.of("LEDGER_PROOF_CREATE"));
        // Mirrors how JwtAuthFilter actually marks a token as a service principal (principalType),
        // not something this test can fabricate through the public CurrentUser constructor used
        // above for the human case - using the real classification method is the point.
        CurrentUser serviceOperator = mock(CurrentUser.class);
        when(serviceOperator.isService()).thenReturn(true);

        var rejection = assertThrows(ProtocolProofOutboxStore.ReplayNotAllowedException.class,
                () -> service.replay(serviceOperator, UUID.randomUUID(), "automated retry"));
        assertEquals("OPERATOR_IDENTITY_REQUIRED", rejection.code);
        verifyNoInteractions(outbox);
    }

    @Test void refusesANullOperator() {
        var rejection = assertThrows(ProtocolProofOutboxStore.ReplayNotAllowedException.class,
                () -> service.replay(null, UUID.randomUUID(), "reason"));
        assertEquals("OPERATOR_IDENTITY_REQUIRED", rejection.code);
        verifyNoInteractions(outbox);
    }
}
