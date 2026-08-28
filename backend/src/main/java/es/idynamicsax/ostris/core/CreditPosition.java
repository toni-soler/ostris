package es.idynamicsax.ostris.core;

import java.math.BigInteger;

public record CreditPosition(OstrisAmount balance, OstrisAmount creditFloor) {
    public CreditPosition {
        if (balance == null || creditFloor == null || creditFloor.minorUnits().signum() > 0) throw new ProtocolException("INVALID_CREDIT_POSITION", "Credit floor must be non-positive");
    }
    public OstrisAmount availableNegativeExposure() { return new OstrisAmount(balance.minorUnits().subtract(creditFloor.minorUnits()).max(BigInteger.ZERO)); }
    public OstrisAmount enforcedLiability() { return new OstrisAmount(creditFloor.minorUnits().subtract(balance.minorUnits()).max(BigInteger.ZERO)); }
    public boolean permits(OstrisAmount delta, boolean finalEnforced) {
        BigInteger projected = balance.minorUnits().add(delta.minorUnits());
        return projected.compareTo(creditFloor.minorUnits()) >= 0 || finalEnforced || delta.minorUnits().signum() >= 0;
    }
}
