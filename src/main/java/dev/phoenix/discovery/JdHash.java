package dev.phoenix.discovery;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Content hash of a job description, to notice when a posting's text changes. */
final class JdHash {

    private JdHash() {
    }

    static String of(String jd) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(jd.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", e);
        }
    }
}
