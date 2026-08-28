package es.idynamicsax.ostris.core;

import java.util.HashSet;
import java.util.List;

public final class TransactionValidator {
    private TransactionValidator() {}
    public static void validateBalanced(List<TransactionEntry> entries) {
        if (entries == null || entries.size() < 2) throw new ProtocolException("TOO_FEW_ENTRIES", "At least two entries required");
        var accounts = new HashSet<>();
        OstrisAmount sum = OstrisAmount.ZERO;
        for (TransactionEntry entry : entries) {
            if (!accounts.add(entry.accountId())) throw new ProtocolException("DUPLICATE_ACCOUNT_ENTRY", "One entry per account required");
            sum = sum.add(entry.amount());
        }
        if (!sum.equals(OstrisAmount.ZERO)) throw new ProtocolException("NON_ZERO_SUM", "Transaction entries must sum to zero");
    }

    public static void validateFullReversal(JournalTransaction original, List<TransactionEntry> reversal, long existingReversals) {
        if (original == null) throw new ProtocolException("UNKNOWN_ORIGINAL", "Original transaction not found");
        if (original.purpose() == TransactionPurpose.REVERSAL) throw new ProtocolException("REVERSAL_OF_REVERSAL", "Cannot reverse a reversal");
        if (existingReversals != 0) throw new ProtocolException("SECOND_REVERSAL", "Original already reversed");
        if (original.entries().size() != reversal.size()) throw new ProtocolException("PARTIAL_REVERSAL", "Only full reversal is supported");
        for (int index = 0; index < reversal.size(); index++) {
            TransactionEntry expected = original.entries().get(index);
            TransactionEntry actual = reversal.get(index);
            if (!expected.accountId().equals(actual.accountId()) || !expected.amount().negate().equals(actual.amount())) throw new ProtocolException("PARTIAL_REVERSAL", "Reversal must negate original entry order");
        }
    }
}
