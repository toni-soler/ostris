package es.idynamicsax.ostris.core;

public enum TransactionPurpose {
    EXCHANGE, REVERSAL, SETTLEMENT, WRITE_OFF, RESTITUTION, PENALTY, LOSS_OFFSET;

    public static TransactionPurpose parse(String value) {
        try { return valueOf(value); }
        catch (RuntimeException ex) { throw new ProtocolException("UNKNOWN_TRANSACTION_PURPOSE", "Unknown purpose: " + value); }
    }
}
