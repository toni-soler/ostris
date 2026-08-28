package es.idynamicsax.ostris.core;
import static org.junit.jupiter.api.Assertions.*; import java.util.*; import org.junit.jupiter.api.Test;
class PolicyInvariantTest {
 @Test void thresholdAndEffectiveSequenceAreDeterministic(){UUID a=UUID.randomUUID(),b=UUID.randomUUID();var p=new AccountControlPolicy(2,2,10L,20L,Set.of(a,b));assertFalse(p.effectiveAt(9));assertTrue(p.effectiveAt(10));assertFalse(p.effectiveAt(20));assertFalse(p.authorizes(Set.of(a)));assertTrue(p.authorizes(Set.of(a,b)));}
 @Test void selectsHighestEffectivePolicyVersion(){String d="a".repeat(64);var p1=new VersionedPolicy(UUID.randomUUID(),"CREDIT",1,1L,10L,d);var p2=new VersionedPolicy(UUID.randomUUID(),"CREDIT",2,10L,null,d);assertEquals(1,VersionedPolicy.select(List.of(p1,p2),"CREDIT",9).version());assertEquals(2,VersionedPolicy.select(List.of(p1,p2),"CREDIT",10).version());}
}
