package es.idynamicsax.ostris.ledger;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@ConditionalOnExpression("${idax.ostris.ledger.enabled:false} && ${idax.ostris.ledger.worker-enabled:true}")
public final class ProtocolEventProofDeliveryWorker {
    private final ProtocolEventProofDeliveryService delivery;
    private final OstrisLedgerDeliveryProperties properties;
    private final AtomicBoolean running = new AtomicBoolean();

    public ProtocolEventProofDeliveryWorker(
            ProtocolEventProofDeliveryService delivery,
            OstrisLedgerDeliveryProperties properties) {
        this.delivery = delivery;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${idax.ostris.ledger.poll-interval:5s}")
    public void deliverAvailableProofs() {
        if (!running.compareAndSet(false, true)) return;
        try {
            for (int delivered = 0; delivered < properties.maxBatchSize(); delivered++) {
                if (!delivery.deliverNext()) return;
            }
        } finally {
            running.set(false);
        }
    }
}
