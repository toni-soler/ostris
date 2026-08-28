package es.idynamicsax.ostris.core;
import java.util.*;
public final class ThresholdAuthorizationEvaluator {
 private ThresholdAuthorizationEvaluator(){}
 public static void require(int threshold,Collection<UUID> eligibleControllers,Collection<UUID> validSigners){if(threshold<1)throw new ProtocolException("INVALID_CONTROL_POLICY","Threshold must be positive");Set<UUID> eligible=Set.copyOf(eligibleControllers),distinct=new HashSet<>(validSigners);distinct.retainAll(eligible);if(distinct.size()<threshold)throw new ProtocolException("CONTROL_POLICY_NOT_SATISFIED","Insufficient distinct authorized signers");}
}
