package es.idynamicsax.ostris.core;
import java.util.*;
public final class GovernedAuthorizationRules {
 private static final Map<TransactionPurpose,Set<ResolutionBasisType>> ALLOWED=Map.of(
  TransactionPurpose.PENALTY,Set.of(ResolutionBasisType.FINAL_FINDING),
  TransactionPurpose.RESTITUTION,Set.of(ResolutionBasisType.FINAL_FINDING,ResolutionBasisType.FINAL_DISPUTE_RESOLUTION),
  TransactionPurpose.WRITE_OFF,Set.of(ResolutionBasisType.FINAL_DEFAULT_DECISION),
  TransactionPurpose.LOSS_OFFSET,Set.of(ResolutionBasisType.COMMUNITY_GOVERNANCE_DECISION),
  TransactionPurpose.REVERSAL,Set.of(ResolutionBasisType.FINAL_DISPUTE_RESOLUTION),
  TransactionPurpose.SETTLEMENT,Set.of(ResolutionBasisType.FINAL_DISPUTE_RESOLUTION));
 private GovernedAuthorizationRules(){}
 public static void validate(TransactionPurpose purpose,ResolutionBasisType basis,Set<UUID> affected,Set<UUID> covered){if(!ALLOWED.getOrDefault(purpose,Set.of()).contains(basis))throw new ProtocolException("GOVERNED_PURPOSE_BASIS_NOT_ALLOWED","Purpose/basis combination is not permitted");if(covered==null||covered.isEmpty()||!affected.containsAll(covered))throw new ProtocolException("INVALID_COVERED_ACCOUNTS","Covered accounts must be non-empty affected accounts");}
 public static boolean allowed(TransactionPurpose purpose,ResolutionBasisType basis){return ALLOWED.getOrDefault(purpose,Set.of()).contains(basis);}
}
