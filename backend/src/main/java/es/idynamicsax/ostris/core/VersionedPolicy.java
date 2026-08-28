package es.idynamicsax.ostris.core;
import java.util.*;
public record VersionedPolicy(UUID id,String type,int version,long activationSequence,Long supersededSequence,String configDigest){
 public VersionedPolicy{if(id==null||type==null||version<1||activationSequence<1||configDigest==null||!configDigest.matches("[0-9a-f]{64}"))throw new ProtocolException("INVALID_POLICY","Invalid policy version");}
 public boolean effectiveAt(long sequence){return sequence>=activationSequence&&(supersededSequence==null||sequence<supersededSequence);}
 public static VersionedPolicy select(List<VersionedPolicy> policies,String type,long sequence){return policies.stream().filter(p->p.type.equals(type)&&p.effectiveAt(sequence)).max(Comparator.comparingInt(VersionedPolicy::version)).orElseThrow(()->new ProtocolException("MISSING_POLICY","No effective "+type+" policy"));}
}
