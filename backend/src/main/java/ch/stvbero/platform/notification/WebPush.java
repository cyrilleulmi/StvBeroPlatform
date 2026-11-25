package ch.stvbero.platform.notification;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;

/**
 * Web Push with the means of the JDK (F-19, criterion 4): message encryption (RFC 8291, «aes128gcm» from RFC 8188)
 * and the VAPID signature (RFC 8292). Keys on P-256, written as base64url without padding, as browsers and the
 * usual tools (e.g. {@code npx web-push generate-vapid-keys}) use them.
 */
public final class WebPush {

    /** Record size in the header; one record is enough for the short notifications. */
    private static final int RECORD_SIZE = 4096;
    /** A push may carry at most this much plaintext (4096 minus header, padding delimiter and tag). */
    static final int MAX_PLAINTEXT = 3993;
    /** Validity of a VAPID token; RFC 8292 allows at most 24 hours. */
    static final Duration VAPID_VALIDITY = Duration.ofHours(12);

    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();
    private static final ECParameterSpec P256 = p256();

    private WebPush() {
    }

    // ------------------------------------------------------------------ Keys

    public static KeyPair newKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"), new SecureRandom());
            return generator.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Key pair from the base64url values: public key uncompressed (65 bytes), private key raw (32 bytes). */
    public static KeyPair keyPair(String publicKey, String privateKey) {
        try {
            ECPublicKey pub = publicKey(decode(publicKey));
            byte[] d = decode(privateKey);
            if (d.length != 32) {
                throw new IllegalArgumentException("The private key must have 32 bytes");
            }
            ECPrivateKey priv = (ECPrivateKey) KeyFactory.getInstance("EC")
                    .generatePrivate(new ECPrivateKeySpec(new BigInteger(1, d), P256));
            return new KeyPair(pub, priv);
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("Invalid VAPID key", e);
        }
    }

    /** Public key from the uncompressed form {@code 04 || x || y}. Throws for anything else. */
    public static ECPublicKey publicKey(byte[] raw) {
        if (raw.length != 65 || raw[0] != 0x04) {
            throw new IllegalArgumentException("Not an uncompressed P-256 key");
        }
        try {
            ECPoint point = new ECPoint(new BigInteger(1, Arrays.copyOfRange(raw, 1, 33)),
                    new BigInteger(1, Arrays.copyOfRange(raw, 33, 65)));
            return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(point, P256));
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("Not a point on P-256", e);
        }
    }

    /** Uncompressed form {@code 04 || x || y} (65 bytes). */
    public static byte[] raw(ECPublicKey key) {
        byte[] out = new byte[65];
        out[0] = 0x04;
        unsigned(key.getW().getAffineX(), out, 1);
        unsigned(key.getW().getAffineY(), out, 33);
        return out;
    }

    public static String encode(byte[] bytes) {
        return B64.encodeToString(bytes);
    }

    public static byte[] decode(String value) {
        return B64D.decode(value.trim().replace('+', '-').replace('/', '_').replace("=", ""));
    }

    // ------------------------------------------------------------------ Encryption (RFC 8291)

    /** Encrypts a push message for a browser with a fresh server key and salt. */
    public static byte[] encrypt(byte[] plaintext, byte[] browserPublicKey, byte[] authSecret) {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return encrypt(plaintext, browserPublicKey, authSecret, newKeyPair(), salt);
    }

    /** As {@link #encrypt(byte[], byte[], byte[])}, with a given server key and salt (for the test vector). */
    static byte[] encrypt(byte[] plaintext, byte[] browserPublicKey, byte[] authSecret, KeyPair serverKeys,
                          byte[] salt) {
        if (plaintext.length > MAX_PLAINTEXT) {
            throw new IllegalArgumentException("Push message too long");
        }
        try {
            byte[] serverPublic = raw((ECPublicKey) serverKeys.getPublic());
            byte[] ecdh = ecdh((ECPrivateKey) serverKeys.getPrivate(), publicKey(browserPublicKey));
            Keys keys = keys(ecdh, authSecret, browserPublicKey, serverPublic, salt);

            byte[] record = Arrays.copyOf(plaintext, plaintext.length + 1);
            record[plaintext.length] = 0x02; // delimiter of the last record
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(keys.cek(), "AES"), new GCMParameterSpec(128, keys.nonce()));
            byte[] ciphertext = cipher.doFinal(record);

            ByteBuffer body = ByteBuffer.allocate(16 + 4 + 1 + serverPublic.length + ciphertext.length);
            body.put(salt).putInt(RECORD_SIZE).put((byte) serverPublic.length).put(serverPublic).put(ciphertext);
            return body.array();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Decrypts what {@link #encrypt} produced, as the browser does (only needed by the tests). */
    static byte[] decrypt(byte[] body, KeyPair browserKeys, byte[] authSecret) throws GeneralSecurityException {
        ByteBuffer in = ByteBuffer.wrap(body);
        byte[] salt = new byte[16];
        in.get(salt);
        in.getInt();
        byte[] serverPublic = new byte[in.get() & 0xff];
        in.get(serverPublic);
        byte[] ciphertext = new byte[in.remaining()];
        in.get(ciphertext);
        byte[] browserPublic = raw((ECPublicKey) browserKeys.getPublic());
        byte[] ecdh = ecdh((ECPrivateKey) browserKeys.getPrivate(), publicKey(serverPublic));
        Keys keys = keys(ecdh, authSecret, browserPublic, serverPublic, salt);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(keys.cek(), "AES"), new GCMParameterSpec(128, keys.nonce()));
        byte[] record = cipher.doFinal(ciphertext);
        int end = record.length - 1;
        while (end >= 0 && record[end] == 0) {
            end--;
        }
        if (end < 0 || record[end] != 0x02) {
            throw new GeneralSecurityException("Missing record delimiter");
        }
        return Arrays.copyOf(record, end);
    }

    private record Keys(byte[] cek, byte[] nonce) {
    }

    private static Keys keys(byte[] ecdh, byte[] authSecret, byte[] browserPublic, byte[] serverPublic, byte[] salt)
            throws GeneralSecurityException {
        byte[] keyInfo = concat("WebPush: info\0".getBytes(StandardCharsets.US_ASCII), browserPublic, serverPublic);
        byte[] ikm = hkdf(authSecret, ecdh, keyInfo, 32);
        byte[] cek = hkdf(salt, ikm, "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), 16);
        byte[] nonce = hkdf(salt, ikm, "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), 12);
        return new Keys(cek, nonce);
    }

    /** HKDF with SHA-256 (RFC 5869) for outputs up to 32 bytes. */
    private static byte[] hkdf(byte[] salt, byte[] ikm, byte[] info, int length) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(salt, "HmacSHA256"));
        byte[] prk = mac.doFinal(ikm);
        mac.init(new SecretKeySpec(prk, "HmacSHA256"));
        mac.update(info);
        mac.update((byte) 1);
        return Arrays.copyOf(mac.doFinal(), length);
    }

    private static byte[] ecdh(ECPrivateKey own, ECPublicKey other) throws GeneralSecurityException {
        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(own);
        agreement.doPhase(other, true);
        return agreement.generateSecret();
    }

    // ------------------------------------------------------------------ VAPID (RFC 8292)

    /**
     * Value of the {@code Authorization} header: a JWT for the origin of the endpoint, signed with ES256, and the
     * public key.
     *
     * @param subject contact for the push service, {@code mailto:…} or {@code https://…}
     */
    public static String vapidAuthorization(String endpoint, KeyPair vapidKeys, String subject, Instant now) {
        URI uri = URI.create(endpoint);
        String audience = uri.getScheme() + "://" + uri.getRawAuthority();
        String header = encode("{\"typ\":\"JWT\",\"alg\":\"ES256\"}".getBytes(StandardCharsets.UTF_8));
        String claims = encode(("{\"aud\":" + DeliveryRules.json(audience) + ",\"exp\":"
                + now.plus(VAPID_VALIDITY).getEpochSecond() + ",\"sub\":" + DeliveryRules.json(subject) + "}")
                .getBytes(StandardCharsets.UTF_8));
        String unsigned = header + "." + claims;
        try {
            Signature signature = Signature.getInstance("SHA256withECDSAinP1363Format");
            signature.initSign(vapidKeys.getPrivate());
            signature.update(unsigned.getBytes(StandardCharsets.US_ASCII));
            String token = unsigned + "." + encode(signature.sign());
            return "vapid t=" + token + ", k=" + encode(raw((ECPublicKey) vapidKeys.getPublic()));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    // ------------------------------------------------------------------ Helpers

    private static ECParameterSpec p256() {
        try {
            AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
            parameters.init(new ECGenParameterSpec("secp256r1"));
            return parameters.getParameterSpec(ECParameterSpec.class);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void unsigned(BigInteger value, byte[] out, int offset) {
        byte[] bytes = value.toByteArray();
        int length = Math.min(bytes.length, 32);
        System.arraycopy(bytes, bytes.length - length, out, offset + 32 - length, length);
    }

    private static byte[] concat(byte[]... parts) {
        int length = 0;
        for (byte[] part : parts) {
            length += part.length;
        }
        ByteBuffer out = ByteBuffer.allocate(length);
        for (byte[] part : parts) {
            out.put(part);
        }
        return out.array();
    }
}
