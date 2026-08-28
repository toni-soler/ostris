package es.idynamicsax.ostris;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import es.idynamicsax.idax.config.TokenValidatorConfig;
import es.idynamicsax.idax.security.DualTokenValidator;
import es.idynamicsax.idax.security.KeycloakTokenValidator;
import es.idynamicsax.idax.security.LocalTokenValidator;
import es.idynamicsax.idax.security.TokenValidator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;

class OstrisTokenValidatorModeCompositionTest {

    private final ApplicationContextRunner contexts = new ApplicationContextRunner()
            .withUserConfiguration(
                    TokenValidatorConfig.class,
                    LocalTokenValidator.class,
                    KeycloakTokenValidator.class,
                    DualTokenValidator.class,
                    DecoderStubs.class);

    @Test
    void localModeSelectsOnlyLocalUserTokenValidator() {
        contexts.withPropertyValues("idax.auth.mode=LOCAL", "idax.auth.token-validator=local")
                .run(context -> {
                    assertTrue(context.containsBean("local"));
                    assertFalse(context.containsBean("keycloak"));
                    assertFalse(context.containsBean("dual"));
                    assertSame(context.getBean("local"), context.getBean(TokenValidator.class));
                });
    }

    @Test
    void keycloakModeSelectsOnlyKeycloakUserTokenValidator() {
        contexts.withPropertyValues("idax.auth.mode=KEYCLOAK", "idax.auth.token-validator=keycloak")
                .run(context -> {
                    assertFalse(context.containsBean("local"));
                    assertTrue(context.containsBean("keycloak"));
                    assertFalse(context.containsBean("dual"));
                    assertSame(context.getBean("keycloak"), context.getBean(TokenValidator.class));
                });
    }

    @Test
    void dualModeSelectsDualUserTokenValidator() {
        contexts.withPropertyValues("idax.auth.mode=DUAL", "idax.auth.token-validator=dual")
                .run(context -> {
                    assertTrue(context.containsBean("local"));
                    assertTrue(context.containsBean("keycloak"));
                    assertTrue(context.containsBean("dual"));
                    assertSame(context.getBean("dual"), context.getBean(TokenValidator.class));
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class DecoderStubs {
        @Bean("localJwtDecoder")
        JwtDecoder localJwtDecoder() {
            return token -> { throw new IllegalArgumentException("unused decoder stub"); };
        }

        @Bean("keycloakJwtDecoder")
        JwtDecoder keycloakJwtDecoder() {
            return token -> { throw new IllegalArgumentException("unused decoder stub"); };
        }
    }
}
