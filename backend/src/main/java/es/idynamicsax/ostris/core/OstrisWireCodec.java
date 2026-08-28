package es.idynamicsax.ostris.core;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.erdtman.jcs.JsonCanonicalizer;

public final class OstrisWireCodec {
    public static final String AUTHORIZATION_DOMAIN = "OSTRIS:TX:AUTH:V1";
    public static final String GOVERNANCE_AUTHORIZATION_DOMAIN = "OSTRIS:TX:GOVAUTH:V1";
    private final ObjectMapper mapper = JsonMapper.builder()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    public JsonNode parse(String json) {
        try { JsonNode node = mapper.readTree(json); validate(node); return node; }
        catch (ProtocolException ex) { throw ex; }
        catch (Exception ex) { throw new ProtocolException("INVALID_I_JSON", ex.getMessage()); }
    }

    public byte[] canonicalize(JsonNode node) {
        validate(node);
        try { return new JsonCanonicalizer(node.toString()).getEncodedUTF8(); }
        catch (Exception ex) { throw new ProtocolException("INVALID_I_JSON", ex.getMessage()); }
    }

    public byte[] authorizationMessage(JsonNode payload) {
        return domainMessage(AUTHORIZATION_DOMAIN, payload);
    }

    public byte[] governanceAuthorizationMessage(JsonNode payload) {
        return domainMessage(GOVERNANCE_AUTHORIZATION_DOMAIN, payload);
    }

    private byte[] domainMessage(String domainName, JsonNode payload) {
        byte[] domain = domainName.getBytes(StandardCharsets.US_ASCII);
        byte[] canonical = canonicalize(payload);
        byte[] message = new byte[domain.length + 1 + canonical.length];
        System.arraycopy(domain, 0, message, 0, domain.length);
        System.arraycopy(canonical, 0, message, domain.length + 1, canonical.length);
        return message;
    }

    public static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (Exception ex) { throw new IllegalStateException(ex); }
    }

    private void validate(JsonNode node) {
        if (node == null || node.isMissingNode()) throw new ProtocolException("INVALID_I_JSON", "Missing JSON value");
        if (node.isTextual()) {
            String value = node.textValue();
            for (int i = 0; i < value.length(); i++) {
                char current = value.charAt(i);
                if (Character.isHighSurrogate(current)) {
                    if (i + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(++i))) throw new ProtocolException("INVALID_I_JSON", "Invalid Unicode surrogate");
                } else if (Character.isLowSurrogate(current)) throw new ProtocolException("INVALID_I_JSON", "Invalid Unicode surrogate");
            }
        }
        node.elements().forEachRemaining(this::validate);
    }
}
