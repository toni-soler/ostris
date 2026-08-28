package es.idynamicsax.ostris.ledger;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("idax.ostris.ledger")
public record OstrisLedgerDeliveryProperties(
        boolean enabled,
        String ledgerBaseUrl,
        String platformBaseUrl,
        String clientId,
        String clientSecret,
        String audience,
        Duration connectTimeout,
        Duration readTimeout,
        Duration tokenSafetyWindow,
        Duration pollInterval,
        int maxBatchSize,
        Duration processingLease,
        Duration initialRetryDelay,
        Duration maximumRetryDelay) {

    public OstrisLedgerDeliveryProperties {
        ledgerBaseUrl = defaultValue(ledgerBaseUrl, "http://localhost:8086");
        platformBaseUrl = defaultValue(platformBaseUrl, "http://localhost:8080");
        clientId = defaultValue(clientId, "ostris-ledger-delivery");
        clientSecret = clientSecret == null ? "" : clientSecret;
        audience = defaultValue(audience, "idax-ledger");
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(3) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofSeconds(15) : readTimeout;
        tokenSafetyWindow = tokenSafetyWindow == null ? Duration.ofSeconds(45) : tokenSafetyWindow;
        pollInterval = pollInterval == null ? Duration.ofSeconds(5) : pollInterval;
        maxBatchSize = maxBatchSize < 1 ? 10 : maxBatchSize;
        processingLease = processingLease == null ? Duration.ofMinutes(2) : processingLease;
        initialRetryDelay = initialRetryDelay == null ? Duration.ofSeconds(10) : initialRetryDelay;
        maximumRetryDelay = maximumRetryDelay == null ? Duration.ofMinutes(15) : maximumRetryDelay;
        if (processingLease.isNegative() || processingLease.isZero()) throw new IllegalArgumentException("processingLease must be positive");
        if (initialRetryDelay.isNegative() || initialRetryDelay.isZero()) throw new IllegalArgumentException("initialRetryDelay must be positive");
        if (maximumRetryDelay.compareTo(initialRetryDelay) < 0) throw new IllegalArgumentException("maximumRetryDelay must not be shorter than initialRetryDelay");
    }

    private static String defaultValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
