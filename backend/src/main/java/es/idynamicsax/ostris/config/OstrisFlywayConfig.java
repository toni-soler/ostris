package es.idynamicsax.ostris.config;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OstrisFlywayConfig {
    @Bean
    Flyway flywayOstris(DataSource dataSource) {
        Flyway flyway = Flyway.configure().dataSource(dataSource).schemas("ostris")
                .locations("classpath:db/migration-ostris").baselineOnMigrate(true).load();
        flyway.migrate();
        return flyway;
    }
}
