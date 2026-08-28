package es.idynamicsax.ostris.core;

import java.util.UUID;

public record TransactionEntry(UUID accountId, OstrisAmount amount) {
    public TransactionEntry {
        if (accountId == null || amount == null || amount.equals(OstrisAmount.ZERO)) throw new ProtocolException("INVALID_ENTRY", "Entry requires account and non-zero amount");
    }
}
