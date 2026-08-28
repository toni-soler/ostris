package es.idynamicsax.ostris.api;

import static org.junit.jupiter.api.Assertions.*;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import es.idynamicsax.idax.security.CurrentUser;
import es.idynamicsax.idax.security.LocalTokenValidator;
import es.idynamicsax.idax.security.TokenValidator;
import es.idynamicsax.idax.tenant.AppUserResolver;
import es.idynamicsax.idax.tenant.DbSessionContextService;
import es.idynamicsax.idax.tenant.RlsTransactionAspect;
import es.idynamicsax.idax.tenant.TenantResolver;
import es.idynamicsax.ostris.config.OstrisFlywayConfig;
import es.idynamicsax.ostris.config.OstrisSecurityConfig;
import es.idynamicsax.ostris.security.OstrisJwtAuthFilter;
import es.idynamicsax.ostris.service.IdentityContinuityService;
import es.idynamicsax.ostris.service.ProposalAuthorizationService;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.core.Ordered;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker=true) @SpringBootTest(classes=HttpSecurityPostgresTest.TestApp.class,webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class HttpSecurityPostgresTest {
 @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:17").withDatabaseName("ostris").withUsername("postgres").withPassword("test").withInitScript("postgres-init.sql");
 static final UUID TENANT_A=UUID.fromString("00000000-0000-0000-0000-00000000000a"),TENANT_B=UUID.fromString("00000000-0000-0000-0000-00000000000b"),USER=UUID.fromString("018f6f9a-7b1c-7a2b-8c3d-4e5f60719201"),COMMUNITY_A=UUID.fromString("018f6f9a-7b1c-7a2b-8c3d-4e5f60719202"),COMMUNITY_B=UUID.fromString("018f6f9a-7b1c-7a2b-8c3d-4e5f60719203"),PARTICIPANT_A=UUID.fromString("018f6f9a-7b1c-7a2b-8c3d-4e5f60719204"),PARTICIPANT_B=UUID.fromString("018f6f9a-7b1c-7a2b-8c3d-4e5f60719205"),RISK_A=UUID.fromString("018f6f9a-7b1c-7a2b-8c3d-4e5f60719206");
 static KeyPair keys;@LocalServerPort int port;@Autowired TestRestTemplate http;@Autowired JdbcTemplate jdbc;@Autowired JwtEncoder encoder;
 @DynamicPropertySource static void props(DynamicPropertyRegistry r){r.add("spring.datasource.url",POSTGRES::getJdbcUrl);r.add("spring.datasource.username",POSTGRES::getUsername);r.add("spring.datasource.password",POSTGRES::getPassword);r.add("spring.jpa.hibernate.ddl-auto",()->"none");r.add("spring.flyway.enabled",()->"false");}
 static boolean initialized;@BeforeEach void fixture(){if(initialized)return;jdbc.update("alter table idax_core.tenant add column if not exists code varchar(32)");jdbc.execute("create table if not exists idax_core.app_user(user_id uuid primary key default gen_random_uuid(),external_subject varchar(255) unique,email varchar(255),display_name varchar(255),is_superuser boolean default false,updated_at timestamptz default now())");jdbc.execute("create or replace function idax_core.set_tenant(uuid) returns void language sql as 'select set_config(''app.tenant_id'', $1::text, true)'");jdbc.execute("grant usage on schema idax_core to idax_app,idax_admin; grant execute on function idax_core.set_tenant(uuid) to idax_app,idax_admin; grant select,insert,update on idax_core.app_user to idax_admin");jdbc.update("insert into idax_core.tenant(tenant_id,code) values(?,?),(?,?)",TENANT_A,"A",TENANT_B,"B");jdbc.update("insert into ostris.community(id,tenant_id,name) values(?,?,?),(?,?,?)",COMMUNITY_A,TENANT_A,"A",COMMUNITY_B,TENANT_B,"B");jdbc.update("insert into ostris.risk_subject(id,tenant_id,community_id) values(?,?,?)",RISK_A,TENANT_A,COMMUNITY_A);jdbc.update("insert into ostris.participant(id,tenant_id,community_id,display_name) values(?,?,?,?),(?,?,?,?)",PARTICIPANT_A,TENANT_A,COMMUNITY_A,"Public A",PARTICIPANT_B,TENANT_B,COMMUNITY_B,"Private B");initialized=true;}
 String url(String path){return "http://localhost:"+port+path;}
 HttpHeaders auth(UUID tenant,Set<String> permissions,Instant expiry){HttpHeaders h=new HttpHeaders();h.setBearerAuth(token(keys,tenant,permissions,expiry));h.setContentType(MediaType.APPLICATION_JSON);return h;}
 @Test void jwtValidationMatrix(){String path="/api/ostris/participants/"+COMMUNITY_A+"/"+PARTICIPANT_A;assertEquals(401,http.getForEntity(url(path),String.class).getStatusCode().value());for(String invalid:List.of("malformed",token(otherKeys(),TENANT_A,Set.of("OSTRIS_READ"),Instant.now().plusSeconds(300)),token(keys,TENANT_A,Set.of("OSTRIS_READ"),Instant.now().minusSeconds(60)))){HttpHeaders h=new HttpHeaders();h.setBearerAuth(invalid);assertEquals(401,http.exchange(url(path),HttpMethod.GET,new HttpEntity<>(h),String.class).getStatusCode().value());}assertEquals(200,http.exchange(url(path),HttpMethod.GET,new HttpEntity<>(auth(TENANT_A,Set.of("OSTRIS_READ"),Instant.now().plusSeconds(300))),String.class).getStatusCode().value());}
 @Test void tenantClaimCannotBeOverriddenAndPrivateIdsDoNotLeak(){HttpHeaders h=auth(TENANT_A,Set.of("OSTRIS_READ"),Instant.now().plusSeconds(300));h.set("X-Tenant",TENANT_B.toString());ResponseEntity<String> own=http.exchange(url("/api/ostris/participants/"+COMMUNITY_A+"/"+PARTICIPANT_A),HttpMethod.GET,new HttpEntity<>(h),String.class);assertEquals(200,own.getStatusCode().value());assertFalse(own.getBody().contains("riskSubject"));ResponseEntity<String> cross=http.exchange(url("/api/ostris/participants/"+COMMUNITY_B+"/"+PARTICIPANT_B),HttpMethod.GET,new HttpEntity<>(h),String.class);assertEquals(422,cross.getStatusCode().value());assertFalse(cross.getBody().contains("Private B"));}
 @Test void sensitiveWritesRequirePermissionAndMalformedInputIsControlled(){String identity="/api/ostris/identity/continuity-decisions";String body="{\"decisionId\":\"018f6f9a-7b1c-7a2b-8c3d-4e5f60719211\",\"communityId\":\""+COMMUNITY_A+"\",\"participantId\":\""+PARTICIPANT_A+"\",\"riskSubjectId\":\""+RISK_A+"\",\"status\":\"CONFIRMED\",\"decisionAuthority\":\"review\"}";assertEquals(403,http.exchange(url(identity),HttpMethod.POST,new HttpEntity<>(body,auth(TENANT_A,Set.of("OSTRIS_READ"),Instant.now().plusSeconds(300))),String.class).getStatusCode().value());ResponseEntity<String> created=http.exchange(url(identity),HttpMethod.POST,new HttpEntity<>(body,auth(TENANT_A,Set.of("OSTRIS_IDENTITY_CONTINUITY_MANAGE"),Instant.now().plusSeconds(300))),String.class);assertEquals(201,created.getStatusCode().value());assertFalse(created.getBody().contains("stackTrace"));String malformed="{\"decisionId\":\"not-a-uuid\"}";ResponseEntity<String> bad=http.exchange(url(identity),HttpMethod.POST,new HttpEntity<>(malformed,auth(TENANT_A,Set.of("OSTRIS_IDENTITY_CONTINUITY_MANAGE"),Instant.now().plusSeconds(300))),String.class);assertEquals(400,bad.getStatusCode().value());assertFalse(bad.getBody().toLowerCase().contains("sql"));}
 @Test void transactionEndpointsRejectAuthenticatedCallerWithoutCapabilities(){HttpHeaders h=auth(TENANT_A,Set.of("OSTRIS_READ"),Instant.now().plusSeconds(300));String proposal="{\"communityId\":\""+COMMUNITY_A+"\",\"unitId\":\"018f6f9a-7b1c-7a2b-8c3d-4e5f60719222\",\"transactionId\":\"018f6f9a-7b1c-7a2b-8c3d-4e5f60719221\",\"purpose\":\"EXCHANGE\",\"entries\":[{\"accountId\":\""+PARTICIPANT_A+"\",\"amount\":\"1\"}]}";String evidence="{\"accountId\":\""+PARTICIPANT_A+"\",\"credentialId\":\"018f6f9a-7b1c-7a2b-8c3d-4e5f60719223\",\"signatureBase64url\":\"x\"}";assertEquals(403,http.exchange(url("/api/ostris/transactions/proposals"),HttpMethod.POST,new HttpEntity<>(proposal,h),String.class).getStatusCode().value());assertEquals(403,http.exchange(url("/api/ostris/transactions/018f6f9a-7b1c-7a2b-8c3d-4e5f60719221/authorizations"),HttpMethod.POST,new HttpEntity<>(evidence,h),String.class).getStatusCode().value());assertEquals(403,http.exchange(url("/api/ostris/transactions/018f6f9a-7b1c-7a2b-8c3d-4e5f60719221/commit"),HttpMethod.POST,new HttpEntity<>(h),String.class).getStatusCode().value());}
 static String token(KeyPair pair,UUID tenant,Set<String> permissions,Instant expiry){try{RSAKey jwk=new RSAKey.Builder((RSAPublicKey)pair.getPublic()).privateKey((RSAPrivateKey)pair.getPrivate()).keyID("test").build();JwtEncoder e=new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));Instant issued=expiry.isBefore(Instant.now())?expiry.minusSeconds(300):Instant.now().minusSeconds(5);JwtClaimsSet claims=JwtClaimsSet.builder().subject("phase6-user").issuedAt(issued).expiresAt(expiry).claim("userId",USER.toString()).claim("tenantId",tenant.toString()).claim("roles",permissions.stream().toList()).build();return e.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).keyId("test").build(),claims)).getTokenValue();}catch(Exception ex){throw new RuntimeException(ex);}}
 static KeyPair otherKeys(){try{return KeyPairGenerator.getInstance("RSA").generateKeyPair();}catch(Exception e){throw new RuntimeException(e);}}
 @SpringBootConfiguration @EnableAutoConfiguration @EnableMethodSecurity @EnableTransactionManagement(order=Ordered.HIGHEST_PRECEDENCE) @Import({OstrisFlywayConfig.class,OstrisSecurityConfig.class,OstrisJwtAuthFilter.class,TenantResolver.class,AppUserResolver.class,DbSessionContextService.class,RlsTransactionAspect.class,IdentityContinuityService.class,IdentityContinuityController.class,TransactionController.class,ProtocolExceptionHandler.class}) static class TestApp {
  static{try{keys=KeyPairGenerator.getInstance("RSA").generateKeyPair();}catch(Exception e){throw new ExceptionInInitializerError(e);}}
  @Bean JwtDecoder decoder(){return NimbusJwtDecoder.withPublicKey((RSAPublicKey)keys.getPublic()).build();}
  @Bean JwtEncoder encoder(){RSAKey jwk=new RSAKey.Builder((RSAPublicKey)keys.getPublic()).privateKey((RSAPrivateKey)keys.getPrivate()).keyID("test").build();return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));}
  @Bean TokenValidator tokenValidator(JwtDecoder decoder){return new LocalTokenValidator(decoder);}
  @Bean("permissionService") PermissionGate permissionService(){return new PermissionGate();}
  @Bean ProposalAuthorizationService proposals(){return org.mockito.Mockito.mock(ProposalAuthorizationService.class);}
 }
 public static class PermissionGate {public boolean hasPermission(String code){var auth=SecurityContextHolder.getContext().getAuthentication();return auth!=null&&auth.getPrincipal() instanceof CurrentUser user&&user.getRoles().contains(code);}}
}
