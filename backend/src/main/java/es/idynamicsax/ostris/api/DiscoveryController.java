package es.idynamicsax.ostris.api;

import es.idynamicsax.idax.tenant.TenantContext;
import es.idynamicsax.ostris.service.DiscoveryService;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Public discovery: communities, units, account balances, credential status. */
@RestController @RequestMapping("/api/ostris/communities")
public class DiscoveryController {
    private final DiscoveryService service;
    public DiscoveryController(DiscoveryService service) { this.service = service; }
    private UUID tenant() { return TenantContext.get().getTenantId(); }

    @GetMapping @PreAuthorize("@permissionService.hasPermission('OSTRIS_DISCOVERY_READ')")
    List<DiscoveryService.CommunityView> communities() { return service.communities(tenant()); }

    @GetMapping("/{community}") @PreAuthorize("@permissionService.hasPermission('OSTRIS_DISCOVERY_READ')")
    DiscoveryService.CommunityView community(@PathVariable UUID community) { return service.community(tenant(), community); }

    @GetMapping("/{community}/units") @PreAuthorize("@permissionService.hasPermission('OSTRIS_DISCOVERY_READ')")
    List<DiscoveryService.UnitView> units(@PathVariable UUID community) { return service.units(tenant(), community); }

    @GetMapping("/{community}/units/{unit}") @PreAuthorize("@permissionService.hasPermission('OSTRIS_DISCOVERY_READ')")
    DiscoveryService.UnitView unit(@PathVariable UUID community, @PathVariable UUID unit) { return service.unit(tenant(), community, unit); }

    @GetMapping("/{community}/accounts/{account}") @PreAuthorize("@permissionService.hasPermission('OSTRIS_DISCOVERY_READ')")
    DiscoveryService.AccountView account(@PathVariable UUID community, @PathVariable UUID account) { return service.account(tenant(), community, account); }

    @GetMapping("/{community}/credentials/{credential}") @PreAuthorize("@permissionService.hasPermission('OSTRIS_DISCOVERY_READ')")
    DiscoveryService.CredentialView credential(@PathVariable UUID community, @PathVariable UUID credential) { return service.credential(tenant(), community, credential); }

    @GetMapping("/{community}/controllers/{controller}/credentials") @PreAuthorize("@permissionService.hasPermission('OSTRIS_DISCOVERY_READ')")
    List<DiscoveryService.CredentialView> credentialsForController(@PathVariable UUID community, @PathVariable UUID controller) {
        return service.credentialsForController(tenant(), community, controller);
    }
}
