package es.idynamicsax.ostris.service;
import es.idynamicsax.ostris.core.TransactionPurpose;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
public record CommitTransactionCommand(@NotNull UUID tenantId,@NotNull UUID communityId,@NotNull UUID unitId,@NotNull UUID transactionId,@NotNull TransactionPurpose purpose,@NotEmpty List<@Valid Entry> entries,Map<String,String> references,String contractualMetadataDigest,es.idynamicsax.ostris.core.ResolutionBasisType resolutionBasisType,UUID resolutionBasisId){
 public CommitTransactionCommand{references=references==null?Map.of():Map.copyOf(references);}
 public CommitTransactionCommand(UUID tenantId,UUID communityId,UUID unitId,UUID transactionId,TransactionPurpose purpose,List<Entry> entries,Map<String,String> references,String contractualMetadataDigest){this(tenantId,communityId,unitId,transactionId,purpose,entries,references,contractualMetadataDigest,null,null);}
 public record Entry(@NotNull UUID accountId,@NotBlank @Pattern(regexp="-?(0|[1-9][0-9]*)") String amount){}
}
