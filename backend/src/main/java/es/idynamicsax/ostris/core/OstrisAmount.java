package es.idynamicsax.ostris.core;

import java.math.BigInteger;
import java.util.Objects;
import java.util.regex.Pattern;

public record OstrisAmount(BigInteger minorUnits) implements Comparable<OstrisAmount> {
    private static final Pattern CANONICAL = Pattern.compile("(?:0|-?[1-9][0-9]{0,77})");
    public static final OstrisAmount ZERO = new OstrisAmount(BigInteger.ZERO);

    public OstrisAmount {
        Objects.requireNonNull(minorUnits, "minorUnits");
        if (minorUnits.abs().toString().length() > 78) {
            throw new ProtocolException("AMOUNT_TOO_LARGE", "Amount exceeds 78 digits");
        }
    }

    public static OstrisAmount parse(Object wireValue) {
        if (!(wireValue instanceof String value) || !CANONICAL.matcher(value).matches()) {
            throw new ProtocolException("INVALID_AMOUNT", "Amount must be a canonical signed integer string");
        }
        return new OstrisAmount(new BigInteger(value));
    }

    public OstrisAmount add(OstrisAmount other) { return new OstrisAmount(minorUnits.add(other.minorUnits)); }
    public OstrisAmount negate() { return new OstrisAmount(minorUnits.negate()); }
    public OstrisAmount abs() { return new OstrisAmount(minorUnits.abs()); }
    public boolean isNegative() { return minorUnits.signum() < 0; }
    public boolean isPositive() { return minorUnits.signum() > 0; }
    public String wire() { return minorUnits.toString(); }
    @Override public int compareTo(OstrisAmount other) { return minorUnits.compareTo(other.minorUnits); }
}
