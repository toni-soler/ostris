package es.idynamicsax.ostris.ledger;

import es.idynamicsax.idax.service.auth.HttpServiceTokenProvider;
import es.idynamicsax.idax.service.auth.ServiceTokenProvider;
import java.net.http.HttpClient;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
        RestClient client = restClientBuilder(properties.platformBaseUrl(), properties)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
        return new HttpServiceTokenProvider(client, properties.clientId(), properties.clientSecret(), properties.tokenSafetyWindow());
    }

    @Bean
    LedgerProofClient ledgerProofClient(OstrisLedgerDeliveryProperties properties) {
        return new HttpLedgerProofClient(restClientBuilder(properties.ledgerBaseUrl(), properties).build());
    }

    private RestClient.Builder restClientBuilder(String baseUrl, OstrisLedgerDeliveryProperties properties) {
        HttpClient http = HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
        JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory(http);
        requests.setReadTimeout(properties.readTimeout());
        return RestClient.builder().baseUrl(baseUrl).requestFactory(requests);
    }
}
