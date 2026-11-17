package dev.phoenix.legacy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Finds a job id inside free text or a URL, and invents a stable one when a row has none. */
final class JobIds {

    private static final String UUID = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";
    /** In order of specificity: uuid, letter prefix + digits (R-123456, JR123456), digits-digits, plain digits. */
    private static final Pattern IN_TEXT = Pattern.compile(
            UUID + "|\\b[A-Za-z]{1,3}-?\\d{4,}\\b|\\b\\d{3,}-\\d{2,}\\b|\\b\\d{4,}\\b");
    private static final Pattern IN_URL = Pattern.compile(
            "gh_jid=(\\d+)|/jobs?/(\\d+)|/(" + UUID + ")|/(\\d{5,})(?:[/?#-]|$)");
    private static final String SYNTHETIC_PREFIX = "legacy-";
    private static final int SYNTHETIC_HASH_CHARS = 12;

    private JobIds() {
    }

    static Optional<String> fromText(String text) {
        Matcher m = IN_TEXT.matcher(text == null ? "" : text);
        return m.find() ? Optional.of(m.group()) : Optional.empty();
    }

    static Optional<String> fromUrl(String url) {
        Matcher m = IN_URL.matcher(url == null ? "" : url);
        if (!m.find()) {
            return Optional.empty();
        }
        for (int i = 1; i <= m.groupCount(); i++) {
            if (m.group(i) != null) {
                return Optional.of(m.group(i));
            }
        }
        return Optional.empty();
    }

    /** Deterministic, so re-importing the same row finds the job it created before. */
    static String synthetic(String company, String rawRow) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((company + "|" + rawRow).getBytes(StandardCharsets.UTF_8));
            return SYNTHETIC_PREFIX + HexFormat.of().formatHex(digest).substring(0, SYNTHETIC_HASH_CHARS);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", e);
        }
    }
}
