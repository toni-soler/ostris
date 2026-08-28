package es.idynamicsax.ostris.core;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

public final class UuidV7 {
    private static final Pattern CANONICAL = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");
    private static final SecureRandom RANDOM = new SecureRandom();
    private UuidV7() {}

    public static UUID parse(String value) {
        if (value == null || !CANONICAL.matcher(value).matches()) throw new ProtocolException("INVALID_UUID_V7", "Identifier must be lowercase canonical UUIDv7");
        return UUID.fromString(value);
    }

    public static UUID generate() {
        long millis = Instant.now().toEpochMilli() & 0xFFFFFFFFFFFFL;
        long randomA = RANDOM.nextInt(1 << 12);
        long msb = (millis << 16) | 0x7000L | randomA;
        long lsb = RANDOM.nextLong();
        lsb = (lsb & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L;
        return new UUID(msb, lsb);
    }

    public static String wire(UUID value) { return parse(value.toString()).toString(); }
}
