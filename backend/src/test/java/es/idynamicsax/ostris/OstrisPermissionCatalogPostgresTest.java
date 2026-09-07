package es.idynamicsax.ostris;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import es.idynamicsax.idax.repository.IdaxPermissionRepository;
import es.idynamicsax.idax.security.CurrentUser;
import es.idynamicsax.idax.service.permission.ModulePermissionCatalogDescriptor;
import es.idynamicsax.idax.service.permission.ModulePermissionCatalogParser;
import es.idynamicsax.idax.service.permission.ModulePermissionCatalogRegistrar;
import es.idynamicsax.idax.service.permission.PermissionService;
import es.idynamicsax.idax.tenant.TenantContext;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.sql.DriverManager;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = OstrisApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OstrisPermissionCatalogPostgresTest {
    private static final String CATALOG = "generated/ostris/permission-catalog.generated.json";
    private static final List<String> CANONICAL_CODES = List.of(
            "OSTRIS_IDENTITY_CONTINUITY_MANAGE",
            "OSTRIS_IDENTITY_CONTINUITY_READ_PRIVATE",
            "OSTRIS_READ",
            "OSTRIS_TRANSACTION_AUTHORIZE",
            "OSTRIS_TRANSACTION_COMMIT",
            "OSTRIS_TRANSACTION_CREATE");
    private static final UUID TENANT_A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID TENANT_B = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID USER = UUID.fromString("018f6f9a-7b1c-7a2b-8c3d-4e5f60719341");
    private static final UUID ROLE = UUID.fromString("018f6f9a-7b1c-7a2b-8c3d-4e5f60719342");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("ostris_catalog")
            .withUsername("postgres")
            .withPassword("test")
            .withInitScript("postgres-init.sql");

    static final Path PUBLIC_KEY = createPublicKey();
    static int permissionsBeforeLoad;
    static int roleAssignmentsBeforeLoad;

    @Autowired JdbcTemplate jdbc;
    @Autowired IdaxPermissionRepository repository;
    @Autowired ModulePermissionCatalogParser parser;
    @Autowired ModulePermissionCatalogRegistrar registrar;
    @Autowired ModulePermissionCatalogDescriptor descriptor;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        try (var connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var statement = connection.createStatement()) {
            try (var result = statement.executeQuery(
                    "SELECT count(*) FROM idax_core.idax_permission WHERE module_key = 'ostris'")) {
                result.next();
                permissionsBeforeLoad = result.getInt(1);
            }
            try (var result = statement.executeQuery("SELECT count(*) FROM idax_core.idax_role_permission")) {
                result.next();
                roleAssignmentsBeforeLoad = result.getInt(1);
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot inspect clean PostgreSQL baseline", exception);
        }
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("idax.auth.mode", () -> "LOCAL");
        registry.add("idax.auth.token-validator", () -> "local");
        registry.add("idax.auth.local.public-key-location", () -> PUBLIC_KEY.toUri().toString());
        registry.add("idax.ostris.ledger.enabled", () -> "false");
        registry.add("idax.ostris.proof.enabled", () -> "false");
    }

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    @Order(1)
    void cleanLifecycleInstallsExactCatalogAndMetadata() throws Exception {
        assertEquals(0, permissionsBeforeLoad);
        assertEquals("ostris", descriptor.moduleKey());
        assertEquals(CATALOG, descriptor.classpathResource());

        byte[] bytes = new ClassPathResource(CATALOG).getContentAsByteArray();
        var catalog = parser.parse(bytes);
        assertEquals(1, catalog.schemaVersion());
        assertEquals("ostris", catalog.moduleKey());
        assertEquals("IDAX_MODULE", catalog.sourceType());
        assertEquals(CANONICAL_CODES, catalog.permissions().stream().map(p -> p.code()).toList());

        var installed = repository.findByModuleKeyAndSourceTypeOrderByCodeAsc("ostris", "IDAX_MODULE");
        assertEquals(CANONICAL_CODES, installed.stream().map(p -> p.getCode()).toList());
        assertEquals(catalog.permissions().size(), installed.size());
        for (int index = 0; index < installed.size(); index++) {
            var expected = catalog.permissions().get(index);
            var actual = installed.get(index);
            assertEquals(expected.resourceKey(), actual.getResourceKey());
            assertEquals(expected.fieldKey(), actual.getFieldKey());
            assertEquals(expected.actionKey(), actual.getActionKey());
            assertEquals(expected.labelKey(), actual.getLabelKey());
            assertEquals(expected.apiPath(), actual.getApiPath());
            assertEquals(expected.description(), actual.getDescription());
            assertTrue(actual.isEnabled());
        }
        assertFalse(repository.existsById("OSTRIS_POLICY_ADMIN"));
        assertFalse(repository.existsById("OSTRIS_RISK_REVIEW"));
    }

    @Test
    @Order(2)
    void reinstallIsIdempotentAndCreatesNoDuplicates() throws Exception {
        byte[] bytes = new ClassPathResource(CATALOG).getContentAsByteArray();
        var result = registrar.register("ostris", bytes);
        assertEquals(0, result.created());
        assertEquals(0, result.updated());
        assertEquals(6, result.unchanged());
        assertEquals(0, result.stale());
        assertEquals(6, jdbc.queryForObject(
                "SELECT count(*) FROM idax_core.idax_permission WHERE module_key = 'ostris'", Integer.class));
        assertEquals(0, jdbc.queryForObject("""
                SELECT count(*) FROM (
                    SELECT permission_code FROM idax_core.idax_permission
                    WHERE module_key = 'ostris' GROUP BY permission_code HAVING count(*) > 1
                ) duplicates
                """, Integer.class));
    }

    @Test
    @Order(3)
    void registrationDoesNotAssignRoles() {
        assertEquals(roleAssignmentsBeforeLoad,
                jdbc.queryForObject("SELECT count(*) FROM idax_core.idax_role_permission", Integer.class));
    }

    @Test
    @Order(4)
    void normalHumanPermissionIsTenantScopedAndUnassignedPermissionIsDenied() {
        jdbc.update("INSERT INTO idax_core.tenant(tenant_id) VALUES (?), (?)", TENANT_A, TENANT_B);
        jdbc.update("INSERT INTO idax_core.tenant_user(tenant_id,user_id,role) VALUES (?,?,'user'),(?,?,'user')",
                TENANT_A, USER, TENANT_B, USER);
        jdbc.update("INSERT INTO idax_core.idax_role(role_id,tenant_id,role_key,name,enabled,system_role) "
                + "VALUES (?,?, 'ostris-reader','osTRIS reader',true,false)", ROLE, TENANT_A);
        jdbc.update("INSERT INTO idax_core.idax_role_permission(role_id,permission_code) VALUES (?,?)",
                ROLE, "OSTRIS_READ");
        jdbc.update("INSERT INTO idax_core.idax_user_role(tenant_id,user_id,role_id) VALUES (?,?,?)",
                TENANT_A, USER, ROLE);

        PermissionService permissionService = new PermissionService(jdbc, repository);
        CurrentUser user = new CurrentUser(USER, "normal-user", TENANT_A, false, Set.of());
        TenantContext.set(new TenantContext(TENANT_A, "A", USER, "normal-user", TenantContext.DbRole.IDAX_APP));
        assertTrue(permissionService.hasPermission(user, "OSTRIS_READ"));
        assertFalse(permissionService.hasPermission(user, "OSTRIS_TRANSACTION_COMMIT"));

        TenantContext.set(new TenantContext(TENANT_B, "B", USER, "normal-user", TenantContext.DbRole.IDAX_APP));
        assertFalse(permissionService.hasPermission(user, "OSTRIS_READ"));
    }

    @Test
    @Order(5)
    void servicePermissionsRemainJwtAuthorities() {
        PermissionService permissionService = new PermissionService(jdbc, repository);
        CurrentUser service = new CurrentUser(UUID.randomUUID(), "service-client", TENANT_A, Set.of("OSTRIS_READ"));
        assertTrue(permissionService.hasPermission(service, "OSTRIS_READ"));
        assertFalse(permissionService.hasPermission(service, "OSTRIS_TRANSACTION_COMMIT"));
    }

    private static Path createPublicKey() {
        try {
            KeyPair pair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
            String encoded = Base64.getMimeEncoder(64, new byte[]{'\n'})
                    .encodeToString(pair.getPublic().getEncoded());
            Path path = Files.createTempFile("ostris-catalog-public-", ".pem");
            Files.writeString(path,
                    "-----BEGIN PUBLIC KEY-----\n" + encoded + "\n-----END PUBLIC KEY-----\n",
                    StandardCharsets.US_ASCII);
            path.toFile().deleteOnExit();
            return path;
        } catch (Exception exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }
}
