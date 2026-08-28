package es.idynamicsax.ostris.api;

import es.idynamicsax.idax.tenant.TenantContext;
import es.idynamicsax.ostris.service.IdentityContinuityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/ostris") public class IdentityContinuityController {
 private final IdentityContinuityService service;public IdentityContinuityController(IdentityContinuityService service){this.service=service;}
 @PostMapping("/identity/continuity-decisions") @PreAuthorize("@permissionService.hasPermission('OSTRIS_IDENTITY_CONTINUITY_MANAGE')") ResponseEntity<IdentityContinuityService.DecisionView> decide(@Valid @RequestBody DecisionRequest r){return ResponseEntity.status(HttpStatus.CREATED).body(service.decide(TenantContext.get().getTenantId(),new IdentityContinuityService.DecisionCommand(r.decisionId(),r.communityId(),r.participantId(),r.riskSubjectId(),r.status(),r.evidenceRefs(),r.decisionAuthority(),r.reason(),r.requiredAssuranceClaimId())));}
 @GetMapping("/identity/communities/{community}/participants/{participant}/continuity") @PreAuthorize("@permissionService.hasPermission('OSTRIS_IDENTITY_CONTINUITY_READ_PRIVATE')") IdentityContinuityService.DecisionView privateContinuity(@PathVariable UUID community,@PathVariable UUID participant){return service.resolvePrivate(TenantContext.get().getTenantId(),community,participant);}
 @GetMapping("/participants/{community}/{participant}") @PreAuthorize("@permissionService.hasPermission('OSTRIS_READ')") IdentityContinuityService.PublicParticipantView participant(@PathVariable UUID community,@PathVariable UUID participant){return service.publicParticipant(TenantContext.get().getTenantId(),community,participant);}
 public record DecisionRequest(@NotNull UUID decisionId,@NotNull UUID communityId,@NotNull UUID participantId,@NotNull UUID riskSubjectId,@NotNull IdentityContinuityService.Status status,List<String> evidenceRefs,@NotBlank String decisionAuthority,String reason,UUID requiredAssuranceClaimId){}
}
