package es.idynamicsax.ostris.core;
import static org.junit.jupiter.api.Assertions.*; import java.util.*; import org.junit.jupiter.api.Test;
class GovernedAuthorizationTest {
 @Test void purposeBasisMatrixFailsClosed(){assertTrue(GovernedAuthorizationRules.allowed(TransactionPurpose.PENALTY,ResolutionBasisType.FINAL_FINDING));assertFalse(GovernedAuthorizationRules.allowed(TransactionPurpose.EXCHANGE,ResolutionBasisType.FINAL_FINDING));assertFalse(GovernedAuthorizationRules.allowed(TransactionPurpose.WRITE_OFF,ResolutionBasisType.FINAL_FINDING));}
 @Test void duplicateSignerDoesNotCountTwice(){UUID a=UUID.randomUUID(),b=UUID.randomUUID();assertThrows(ProtocolException.class,()->ThresholdAuthorizationEvaluator.require(2,List.of(a,b),List.of(a,a)));assertDoesNotThrow(()->ThresholdAuthorizationEvaluator.require(2,List.of(a,b),List.of(a,b)));}
 @Test void coverageCannotNameUnaffectedAccount(){UUID a=UUID.randomUUID(),b=UUID.randomUUID();assertThrows(ProtocolException.class,()->GovernedAuthorizationRules.validate(TransactionPurpose.PENALTY,ResolutionBasisType.FINAL_FINDING,Set.of(a),Set.of(a,b)));}
}
