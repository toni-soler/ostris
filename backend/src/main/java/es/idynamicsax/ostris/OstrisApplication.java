package es.idynamicsax.ostris;

import es.idynamicsax.idax.config.ResourceServerJwtDecoderConfig;
import es.idynamicsax.idax.config.TokenValidatorConfig;
import es.idynamicsax.idax.config.TransactionConfig;
import es.idynamicsax.idax.security.DualTokenValidator;
import es.idynamicsax.idax.security.KeycloakTokenValidator;
import es.idynamicsax.idax.security.LocalTokenValidator;
import es.idynamicsax.idax.tenant.AppUserResolver;
import es.idynamicsax.idax.tenant.DbSessionContextService;
import es.idynamicsax.idax.tenant.RlsTransactionAspect;
import es.idynamicsax.idax.tenant.TenantResolver;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EntityScan("es.idynamicsax.ostris")
@EnableJpaRepositories("es.idynamicsax.ostris")
@Import({
        ResourceServerJwtDecoderConfig.class,
        TokenValidatorConfig.class,
        LocalTokenValidator.class,
        KeycloakTokenValidator.class,
        DualTokenValidator.class,
        TenantResolver.class,
        AppUserResolver.class,
        DbSessionContextService.class,
        RlsTransactionAspect.class,
        TransactionConfig.class
})
@EnableMethodSecurity
@EnableScheduling
public class OstrisApplication {
    public static void main(String[] args) {
        SpringApplication.run(OstrisApplication.class, args);
    }
}
