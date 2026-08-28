package es.idynamicsax.ostris.ledger;

import es.idynamicsax.idax.service.auth.HttpServiceTokenProvider;
import es.idynamicsax.idax.service.auth.ServiceTokenProvider;
import java.net.http.HttpClient;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(OstrisLedgerDeliveryProperties.class)
public class OstrisLedgerDeliveryConfig {
    @Bean
    Clock ostrisLedgerClock() {
        return Clock.systemUTC();
    }

    @Bean
    ServiceTokenProvider ostrisServiceTokenProvider(OstrisLedgerDeliveryProperties properties) {
        RestClient client = restClient(properties.platformBaseUrl(), properties);
        return new HttpServiceTokenProvider(client, properties.clientId(), properties.clientSecret(), properties.tokenSafetyWindow());
    }

    @Bean
    LedgerProofClient ledgerProofClient(OstrisLedgerDeliveryProperties properties) {
        return new HttpLedgerProofClient(restClient(properties.ledgerBaseUrl(), properties));
    }

    private RestClient restClient(String baseUrl, OstrisLedgerDeliveryProperties properties) {
        HttpClient http = HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
        JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory(http);
        requests.setReadTimeout(properties.readTimeout());
        return RestClient.builder().baseUrl(baseUrl).requestFactory(requests).build();
    }
}
