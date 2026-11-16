package es.idynamicsax.ostris.api;

import es.idynamicsax.idax.security.CurrentUser;
import es.idynamicsax.idax.tenant.TenantContext;
import es.idynamicsax.ostris.service.ProvisioningService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigInteger;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/ostris/communities")
public class ProvisioningController {
    /** Per-operation approval for credential changes (see OperationApprovalGate); never a session. */
    public static final String APPROVAL_HEADER = "X-Operation-Approval";
    private final ProvisioningService service;
    public ProvisioningController(ProvisioningService service) { this.service = service; }
    private UUID tenant() { return TenantContext.get().getTenantId(); }
    // The authenticated end user owns/manages their own controller. A service principal has no
    // personal controller; the service layer rejects a null owner with USER_REQUIRED.
    private static UUID actor(CurrentUser user) { return user == null || user.isService() ? null : user.getUserId(); }

    @PostMapping @PreAuthorize("@permissionService.hasPermission('OSTRIS_COMMUNITY_MANAGE')")
    ResponseEntity<ProvisioningService.CommunityCreated> createCommunity(@Valid @RequestBody CommunityRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createCommunity(tenant(), r.name()));
    }

    @PostMapping("/{community}/units") @PreAuthorize("@permissionService.hasPermission('OSTRIS_COMMUNITY_MANAGE')")
    ResponseEntity<ProvisioningService.UnitCreated> createUnit(@PathVariable UUID community, @Valid @RequestBody UnitRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createUnit(tenant(), community, r.code(), r.scale()));
    }

    @PostMapping("/{community}/participants/activate") @PreAuthorize("@permissionService.hasPermission('OSTRIS_PARTICIPANT_ACTIVATE')")
    ResponseEntity<ProvisioningService.ActivationResult> activate(@PathVariable UUID community, @Valid @RequestBody ActivateRequest r,
            @AuthenticationPrincipal CurrentUser user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
            service.activateParticipant(tenant(), community, r.displayName(), r.unitId(), r.publicKeyBase64url(), r.creditFloor(), actor(user)));
    }

    @PostMapping("/{community}/credentials") @PreAuthorize("@permissionService.hasPermission('OSTRIS_CREDENTIAL_MANAGE')")
    ResponseEntity<ProvisioningService.CredentialAdded> addCredential(@PathVariable UUID community, @Valid @RequestBody AddCredentialRequest r,
            @AuthenticationPrincipal CurrentUser user, @RequestHeader(value = APPROVAL_HEADER, required = false) String approval) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addCredential(tenant(), community, r.controllerId(), r.publicKeyBase64url(), actor(user), approval));
    }

    @PostMapping("/{community}/credentials/{credential}/revoke") @PreAuthorize("@permissionService.hasPermission('OSTRIS_CREDENTIAL_MANAGE')")
    ResponseEntity<Void> revokeCredential(@PathVariable UUID community, @PathVariable UUID credential,
            @AuthenticationPrincipal CurrentUser user, @RequestHeader(value = APPROVAL_HEADER, required = false) String approval) {
        service.revokeCredential(tenant(), community, credential, actor(user), approval);
        return ResponseEntity.noContent().build();
    }

    public record CommunityRequest(@NotBlank @Size(max = 160) String name) {}
    public record UnitRequest(@NotBlank @Size(max = 16) String code, @NotNull @Min(0) @Max(18) Integer scale) {}
    public record ActivateRequest(@NotBlank @Size(max = 160) String displayName, @NotNull UUID unitId,
        @NotBlank String publicKeyBase64url, BigInteger creditFloor) {}
    public record AddCredentialRequest(@NotNull UUID controllerId, @NotBlank String publicKeyBase64url) {}
}
