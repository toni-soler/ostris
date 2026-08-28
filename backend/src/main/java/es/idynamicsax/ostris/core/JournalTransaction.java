package es.idynamicsax.ostris.core;

import java.util.List;
import java.util.UUID;

public record JournalTransaction(long communitySequence, TransactionPurpose purpose, List<TransactionEntry> entries) {
    public JournalTransaction {
        if (communitySequence <= 0 || purpose == null || entries == null) throw new ProtocolException("INVALID_JOURNAL_TRANSACTION", "Invalid journal transaction");
    }
}
