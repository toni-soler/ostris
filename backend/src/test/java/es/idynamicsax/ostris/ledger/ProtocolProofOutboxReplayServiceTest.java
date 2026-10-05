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

    @Test void delegatesToTheStoreWithTheControllerResolvedTenantAndTheOperatorsUsername() {
        // tenantId is deliberately NOT read from the operator (see the service's own comment on
        // why: OstrisJwtAuthFilter never puts the X-Tenant-resolved tenant a superuser acted on
        // onto the CurrentUser principal itself, only onto TenantContext) - the caller (here, the
        // controller) resolves it and passes it in explicitly.
        UUID operatorsOwnTenantId = UUID.randomUUID();
        UUID theTenantTheRequestIsActuallyFor = UUID.randomUUID();
        UUID outboxId = UUID.randomUUID();
        CurrentUser operator = new CurrentUser(UUID.randomUUID(), "ana@stir.test", operatorsOwnTenantId, true, Set.of("ROLE_SUPERUSER"));

        service.replay(operator, theTenantTheRequestIsActuallyFor, outboxId, "shell fix deployed");

        verify(outbox).replayFailedProof(outboxId, theTenantTheRequestIsActuallyFor, "ana@stir.test", "shell fix deployed");
    }

    @Test void refusesAServicePrincipalCallerEvenIfItHoldsThePermission() {
        // Mirrors how JwtAuthFilter actually marks a token as a service principal (principalType),
        // not something this test can fabricate through the public CurrentUser constructor used
        // above for the human case - using the real classification method is the point.
        CurrentUser serviceOperator = mock(CurrentUser.class);
        when(serviceOperator.isService()).thenReturn(true);

        var rejection = assertThrows(ProtocolProofOutboxStore.ReplayNotAllowedException.class,
                () -> service.replay(serviceOperator, UUID.randomUUID(), UUID.randomUUID(), "automated retry"));
        assertEquals("OPERATOR_IDENTITY_REQUIRED", rejection.code);
        verifyNoInteractions(outbox);
    }

    @Test void refusesANullOperator() {
        var rejection = assertThrows(ProtocolProofOutboxStore.ReplayNotAllowedException.class,
                () -> service.replay(null, UUID.randomUUID(), UUID.randomUUID(), "reason"));
        assertEquals("OPERATOR_IDENTITY_REQUIRED", rejection.code);
        verifyNoInteractions(outbox);
    }
}
