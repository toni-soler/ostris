package es.idynamicsax.ostris.core;

import java.math.BigInteger;

public record BasisPoints(BigInteger value) {
    public BasisPoints {
        if (value == null || value.signum() < 0) throw new ProtocolException("INVALID_BASIS_POINTS", "Basis points must be non-negative");
    }
    public static BasisPoints ratio(BigInteger numerator, BigInteger denominator) {
        if (numerator.signum() < 0 || denominator.signum() < 0) throw new ProtocolException("INVALID_RATIO", "Ratio inputs must be non-negative");
        return denominator.signum() == 0 ? new BasisPoints(BigInteger.ZERO) : new BasisPoints(numerator.multiply(BigInteger.valueOf(10_000)).divide(denominator));
    }
    public String wire() { return value.toString(); }
}
