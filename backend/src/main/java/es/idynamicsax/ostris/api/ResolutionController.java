package es.idynamicsax.ostris.api;

import es.idynamicsax.idax.tenant.TenantContext;
import es.idynamicsax.ostris.core.ResolutionBasisType;
import es.idynamicsax.ostris.service.ResolutionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Resolution authorities and resolution bases for adjudicated transactions. See ResolutionService:
 * registering them never authorizes a transaction by itself; the authority's controllers still sign
 * the governed payload, and the commit gate verifies everything at the commit sequence.
 */
@RestController @RequestMapping("/api/ostris/communities/{community}")
public class ResolutionController {
    private final ResolutionService service;
    public ResolutionController(ResolutionService service) { this.service = service; }
    private UUID tenant() { return TenantContext.get().getTenantId(); }

    @PostMapping("/resolution-authorities") @PreAuthorize("@permissionService.hasPermission('OSTRIS_COMMUNITY_MANAGE')")
    ResponseEntity<ResolutionService.AuthorityView> registerAuthority(@PathVariable UUID community, @Valid @RequestBody AuthorityRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.registerAuthority(tenant(), community, r.controllerIds(), r.threshold()));
    }

    @GetMapping("/resolution-authorities/current") @PreAuthorize("@permissionService.hasPermission('OSTRIS_DISCOVERY_READ')")
    ResolutionService.AuthorityView currentAuthority(@PathVariable UUID community) {
        return service.currentAuthority(tenant(), community);
    }

    @PostMapping("/resolution-bases") @PreAuthorize("@permissionService.hasPermission('OSTRIS_RESOLUTION_RECORD')")
    ResponseEntity<ResolutionService.BasisView> recordBasis(@PathVariable UUID community, @Valid @RequestBody BasisRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.recordBasis(tenant(), community, r.basisId(), r.basisType()));
    }

    @GetMapping("/resolution-bases/{basis}") @PreAuthorize("@permissionService.hasPermission('OSTRIS_DISCOVERY_READ')")
    ResolutionService.BasisView basis(@PathVariable UUID community, @PathVariable UUID basis) {
        return service.basis(tenant(), community, basis);
    }

    @PostMapping("/resolution-bases/{basis}/finality") @PreAuthorize("@permissionService.hasPermission('OSTRIS_RESOLUTION_RECORD')")
    ResolutionService.BasisView finality(@PathVariable UUID community, @PathVariable UUID basis, @Valid @RequestBody FinalityRequest r) {
        return "OVERTURNED".equals(r.status()) ? service.overturnBasis(tenant(), community, basis) : service.finalizeBasis(tenant(), community, basis);
    }

    public record AuthorityRequest(@NotEmpty @Size(max = 64) List<@NotNull UUID> controllerIds, @Min(1) int threshold) {}
    public record BasisRequest(@NotNull UUID basisId, @NotNull ResolutionBasisType basisType) {}
    public record FinalityRequest(@NotNull @Pattern(regexp = "FINAL|OVERTURNED") String status) {}
}
