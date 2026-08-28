package es.idynamicsax.ostris.core;
import java.util.*;
public record AccountControlPolicy(int version,int threshold,long activationSequence,Long supersededSequence,Set<UUID> controllers){
 public AccountControlPolicy{controllers=Set.copyOf(controllers);if(version<1||threshold<1||threshold>controllers.size()||activationSequence<1)throw new ProtocolException("INVALID_CONTROL_POLICY","Invalid threshold policy");}
 public boolean effectiveAt(long sequence){return sequence>=activationSequence&&(supersededSequence==null||sequence<supersededSequence);}
 public boolean authorizes(Set<UUID> validSigners){return validSigners.stream().filter(controllers::contains).distinct().count()>=threshold;}
}
