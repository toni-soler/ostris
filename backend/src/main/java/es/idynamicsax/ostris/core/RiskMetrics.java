package es.idynamicsax.ostris.core;

import java.math.BigInteger;
import java.util.*;

public final class RiskMetrics {
    private RiskMetrics() {}

    public static Map<RiskMetric, BigInteger> calculate(UUID subject, UUID reciprocalCounterparty, long prospectiveSequence, long windowSize, OstrisAmount floor, OstrisAmount projected, List<JournalTransaction> journal) {
        if (windowSize < 1 || windowSize > 1_000_000) throw new ProtocolException("INVALID_RISK_WINDOW", "Window must be 1..1000000 sequences");
        long lower = Math.max(0, prospectiveSequence - windowSize);
        Map<UUID, BigInteger> inflows = new HashMap<>();
        Set<UUID> counterparties = new HashSet<>();
        BigInteger recent = BigInteger.ZERO, inbound = BigInteger.ZERO, outbound = BigInteger.ZERO;
        for (JournalTransaction transaction : journal) {
            if (transaction.purpose() != TransactionPurpose.EXCHANGE || transaction.communitySequence() < lower || transaction.communitySequence() >= prospectiveSequence) continue;
            TransactionEntry mine = transaction.entries().stream().filter(e -> e.accountId().equals(subject)).findFirst().orElse(null);
            if (mine == null) continue;
            recent = recent.add(mine.amount().minorUnits().abs());
            transaction.entries().stream().filter(e -> !e.accountId().equals(subject)).forEach(e -> counterparties.add(e.accountId()));
            if (transaction.entries().size() != 2) continue;
            TransactionEntry other = transaction.entries().stream().filter(e -> !e.accountId().equals(subject)).findFirst().orElseThrow();
            if (mine.amount().isPositive() && other.amount().isNegative()) {
                inflows.merge(other.accountId(), mine.amount().minorUnits(), BigInteger::add);
                if (other.accountId().equals(reciprocalCounterparty)) inbound = inbound.add(mine.amount().minorUnits());
            } else if (mine.amount().isNegative() && other.amount().isPositive() && other.accountId().equals(reciprocalCounterparty)) {
                outbound = outbound.add(mine.amount().minorUnits().negate());
            }
        }
        BigInteger total = inflows.values().stream().reduce(BigInteger.ZERO, BigInteger::add);
        BigInteger maximum = inflows.values().stream().max(BigInteger::compareTo).orElse(BigInteger.ZERO);
        BigInteger utilization = projected.minorUnits().signum() >= 0 ? BigInteger.ZERO : BasisPoints.ratio(projected.minorUnits().abs(), floor.minorUnits().abs()).value();
        BigInteger concentration = BasisPoints.ratio(maximum, total).value();
        BigInteger reciprocal = inbound.signum() == 0 && outbound.signum() == 0 ? BigInteger.ZERO : BasisPoints.ratio(inbound.min(outbound), inbound.max(outbound)).value();
        EnumMap<RiskMetric, BigInteger> result = new EnumMap<>(RiskMetric.class);
        result.put(RiskMetric.FLOOR_UTILIZATION, utilization);
        result.put(RiskMetric.COUNTERPARTY_CONCENTRATION, concentration);
        result.put(RiskMetric.COUNTERPARTY_COUNT, BigInteger.valueOf(counterparties.size()));
        result.put(RiskMetric.RECENT_VOLUME, recent);
        result.put(RiskMetric.RECIPROCAL_EXPOSURE, reciprocal);
        return Collections.unmodifiableMap(result);
    }
}
