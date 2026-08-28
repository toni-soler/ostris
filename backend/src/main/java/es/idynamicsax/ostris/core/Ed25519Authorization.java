package es.idynamicsax.ostris.core;

import java.security.KeyFactory;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public final class Ed25519Authorization {
    private static final byte[] X509_PREFIX = java.util.HexFormat.of().parseHex("302a300506032b6570032100");
    private Ed25519Authorization() {}

    public static boolean verify(String publicKeyBase64Url, String signatureBase64Url, byte[] message) {
        try {
            byte[] publicRaw = decodeCanonical(publicKeyBase64Url, 32);
            byte[] signatureRaw = decodeCanonical(signatureBase64Url, 64);
            byte[] encoded = new byte[X509_PREFIX.length + publicRaw.length];
            System.arraycopy(X509_PREFIX, 0, encoded, 0, X509_PREFIX.length);
            System.arraycopy(publicRaw, 0, encoded, X509_PREFIX.length, publicRaw.length);
            var publicKey = KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(encoded));
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(publicKey); verifier.update(message);
            return verifier.verify(signatureRaw);
        } catch (ProtocolException ex) { throw ex; }
        catch (Exception ex) { throw new ProtocolException("INVALID_SIGNATURE", ex.getMessage()); }
    }

    private static byte[] decodeCanonical(String value, int length) {
        if (value == null || value.contains("=") || !value.matches("[A-Za-z0-9_-]+")) throw new ProtocolException("INVALID_BASE64URL", "Non-canonical base64url");
        byte[] decoded;
        try { decoded = Base64.getUrlDecoder().decode(value); }
        catch (IllegalArgumentException ex) { throw new ProtocolException("INVALID_BASE64URL", "Invalid base64url"); }
        if (decoded.length != length || !Base64.getUrlEncoder().withoutPadding().encodeToString(decoded).equals(value)) throw new ProtocolException("INVALID_BASE64URL", "Wrong or non-canonical encoding");
        return decoded;
    }
}
