package es.idynamicsax.ostris.core;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Set;

public final class GovernedEconomicPolicyEvaluator {
    private static final Set<String> SANCTION_FIELDS=Set.of("schema","penaltyAllowed","restitutionFromFinalFindingAllowed","restitutionFromFinalDisputeResolutionAllowed");
    private static final Set<String> DEFAULT_FIELDS=Set.of("schema","writeOffAllowed");
    private GovernedEconomicPolicyEvaluator() {}

    public static void require(TransactionPurpose purpose,ResolutionBasisType basis,JsonNode config){
        if(purpose==TransactionPurpose.PENALTY||purpose==TransactionPurpose.RESTITUTION){
            requireClosed(config,"OSTRIS-SANCTION-POLICY-1",SANCTION_FIELDS);
            String flag=purpose==TransactionPurpose.PENALTY?"penaltyAllowed":basis==ResolutionBasisType.FINAL_FINDING?"restitutionFromFinalFindingAllowed":"restitutionFromFinalDisputeResolutionAllowed";
            if(!config.path(flag).asBoolean())throw new ProtocolException("GOVERNED_ECONOMIC_POLICY_DENIED","Effective SanctionPolicy denies governed purpose/basis");
        }else if(purpose==TransactionPurpose.WRITE_OFF){
            requireClosed(config,"OSTRIS-DEFAULT-POLICY-1",DEFAULT_FIELDS);
            if(!config.path("writeOffAllowed").asBoolean())throw new ProtocolException("GOVERNED_ECONOMIC_POLICY_DENIED","Effective DefaultPolicy denies WRITE_OFF");
        }
    }

    private static void requireClosed(JsonNode config,String schema,Set<String> expected){
        if(config==null||!config.isObject())throw new ProtocolException("INVALID_GOVERNED_ECONOMIC_POLICY","Policy config must be a closed JSON object");
        java.util.Set<String> actual=new java.util.HashSet<>();config.fieldNames().forEachRemaining(actual::add);
        if(!actual.equals(expected)||!schema.equals(config.path("schema").asText()))throw new ProtocolException("INVALID_GOVERNED_ECONOMIC_POLICY","Policy schema or fields are invalid");
        for(String field:expected)if(!field.equals("schema")&&!config.path(field).isBoolean())throw new ProtocolException("INVALID_GOVERNED_ECONOMIC_POLICY","Policy flags must be Boolean");
    }
}
