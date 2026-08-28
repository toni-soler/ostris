package es.idynamicsax.ostris.service;

import es.idynamicsax.ostris.core.ProtocolException;
import es.idynamicsax.ostris.core.UuidV7;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdentityContinuityService {
 private final JdbcTemplate jdbc;
 public IdentityContinuityService(JdbcTemplate jdbc){this.jdbc=jdbc;}

 @Transactional public DecisionView decide(UUID tenant,DecisionCommand c){
  UuidV7.parse(c.decisionId().toString());UuidV7.parse(c.communityId().toString());UuidV7.parse(c.participantId().toString());UuidV7.parse(c.riskSubjectId().toString());
  Integer participant=count("select count(*) from ostris.participant where id=? and tenant_id=? and community_id=?",c.participantId(),tenant,c.communityId());
  Integer subject=count("select count(*) from ostris.risk_subject where id=? and tenant_id=? and community_id=?",c.riskSubjectId(),tenant,c.communityId());
  if(participant!=1)throw new ProtocolException("PARTICIPANT_NOT_IN_SCOPE","Participant not found in tenant/community");
  if(subject!=1)throw new ProtocolException("RISK_SUBJECT_NOT_IN_SCOPE","RiskSubject not found in tenant/community");
  Long sequence=jdbc.queryForObject("select next_sequence from ostris.community where id=? and tenant_id=? for update",Long.class,c.communityId(),tenant);
  if(sequence==null)throw new ProtocolException("UNKNOWN_COMMUNITY","Community not found");
  if(c.requiredAssuranceClaimId()!=null){Integer assurance=count("select count(*) from ostris.identity_assurance_claim where id=? and tenant_id=? and community_id=? and risk_subject_id=? and status='ACTIVE' and (revocation_sequence is null or revocation_sequence>?)",c.requiredAssuranceClaimId(),tenant,c.communityId(),c.riskSubjectId(),sequence);if(assurance!=1)throw new ProtocolException("ASSURANCE_NOT_EFFECTIVE","Required assurance is not effective");}
  jdbc.update("update ostris.community set next_sequence=next_sequence+1 where id=? and tenant_id=?",c.communityId(),tenant);
  jdbc.update("insert into ostris.identity_continuity_decision(id,tenant_id,community_id,risk_subject_id,participant_id,status,evidence_refs_json,decision_authority,reason,community_sequence) values(?,?,?,?,?,?,?::jsonb,?,?,?)",c.decisionId(),tenant,c.communityId(),c.riskSubjectId(),c.participantId(),c.status().name(),json(c.evidenceRefs()),c.decisionAuthority(),c.reason(),sequence);
  jdbc.update("update ostris.participant set risk_subject_id=? where id=? and tenant_id=? and community_id=?",c.status()==Status.CONFIRMED?c.riskSubjectId():null,c.participantId(),tenant,c.communityId());
  return new DecisionView(c.decisionId(),c.participantId(),c.status(),sequence,c.status()==Status.CONFIRMED?c.riskSubjectId():null,c.status()==Status.CONTESTED?"REQUIRE_REVIEW":"NONE");
 }

 @Transactional(readOnly=true) public DecisionView resolvePrivate(UUID tenant,UUID community,UUID participant){
  List<DecisionView> rows=jdbc.query("select id,participant_id,status,community_sequence,risk_subject_id from ostris.identity_continuity_decision where tenant_id=? and community_id=? and participant_id=? and community_sequence<(select next_sequence from ostris.community where tenant_id=? and id=?) order by community_sequence desc limit 1",(rs,n)->{Status status=Status.valueOf(rs.getString(3));return new DecisionView(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),status,rs.getLong(4),status==Status.CONFIRMED?rs.getObject(5,UUID.class):null,status==Status.CONTESTED?"REQUIRE_REVIEW":"NONE");},tenant,community,participant,tenant,community);
  if(rows.isEmpty())throw new ProtocolException("CONTINUITY_NOT_FOUND","No effective continuity decision");return rows.getFirst();
 }

 @Transactional(readOnly=true) public PublicParticipantView publicParticipant(UUID tenant,UUID community,UUID participant){return jdbc.query("select id,display_name,profile_status from ostris.participant where tenant_id=? and community_id=? and id=?",(rs,n)->new PublicParticipantView(rs.getObject(1,UUID.class),rs.getString(2),rs.getString(3)),tenant,community,participant).stream().findFirst().orElseThrow(()->new ProtocolException("PARTICIPANT_NOT_IN_SCOPE","Participant not found"));}
 private Integer count(String sql,Object...args){return jdbc.queryForObject(sql,Integer.class,args);}
 private String json(List<String> refs){try{return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(refs==null?List.of():refs);}catch(Exception e){throw new IllegalArgumentException("Invalid evidence references");}}
 public enum Status{CONFIRMED,CONTESTED,REJECTED}
 public record DecisionCommand(UUID decisionId,UUID communityId,UUID participantId,UUID riskSubjectId,Status status,List<String> evidenceRefs,String decisionAuthority,String reason,UUID requiredAssuranceClaimId){}
 public record DecisionView(UUID decisionId,UUID participantId,Status status,long communitySequence,UUID riskSubjectId,String outcome){}
 public record PublicParticipantView(UUID participantId,String displayName,String profileStatus){}
}
