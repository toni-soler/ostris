package es.idynamicsax.ostris.core;

public enum RiskOutcome {
    ALLOW(0), ALLOW_WITH_FLAG(1), REQUIRE_REVIEW(2), BLOCK_NEW_EXPOSURE(3);
    private final int strength;
    RiskOutcome(int strength) { this.strength = strength; }
    public static RiskOutcome strongest(Iterable<RiskOutcome> outcomes) {
        RiskOutcome result = ALLOW;
        for (RiskOutcome outcome : outcomes) if (outcome.strength > result.strength) result = outcome;
        return result;
    }
}
