package dev.phoenix.dedupe;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Layers 2 and 3 similarity: MinHash signatures over 5-word JD shingles. Seeded, so the same text always gives
 * the same signature and stored signatures stay comparable across runs. An empty text has an empty signature;
 * two empty signatures count as identical (nothing to tell them apart), an empty and a non-empty one do not.
 */
public final class MinHash {

    public static final int SHINGLE_WORDS = 5;
    public static final int HASH_FUNCTIONS = 128;
    private static final long SEED = 0x5EED_CAFEL;
    private static final Pattern WORD = Pattern.compile("[a-z0-9]+");

    private final long[] seeds = new long[HASH_FUNCTIONS];

    public MinHash() {
        SplittableRandom random = new SplittableRandom(SEED);
        for (int i = 0; i < seeds.length; i++) {
            seeds[i] = random.nextLong();
        }
    }

    public int[] signature(String text) {
        Set<Long> shingleHashes = shingleHashes(text);
        if (shingleHashes.isEmpty()) {
            return new int[0];
        }
        int[] signature = new int[HASH_FUNCTIONS];
        for (int i = 0; i < HASH_FUNCTIONS; i++) {
            long min = -1L; // all ones: the largest unsigned value
            for (long h : shingleHashes) {
                long mixed = mix(h ^ seeds[i]);
                if (Long.compareUnsigned(mixed, min) < 0) {
                    min = mixed;
                }
            }
            signature[i] = (int) (min >>> 32);
        }
        return signature;
    }

    /** Estimated Jaccard similarity of the two underlying shingle sets. */
    public double similarity(int[] a, int[] b) {
        if (a.length == 0 && b.length == 0) {
            return 1.0;
        }
        if (a.length == 0 || b.length == 0 || a.length != b.length) {
            return 0.0;
        }
        int same = 0;
        for (int i = 0; i < a.length; i++) {
            if (a[i] == b[i]) {
                same++;
            }
        }
        return (double) same / a.length;
    }

    private Set<Long> shingleHashes(String text) {
        Set<Long> hashes = new HashSet<>();
        if (text == null) {
            return hashes;
        }
        List<String> words = new ArrayList<>();
        Matcher m = WORD.matcher(text.toLowerCase(Locale.ROOT));
        while (m.find()) {
            words.add(m.group());
        }
        if (words.isEmpty()) {
            return hashes;
        }
        if (words.size() < SHINGLE_WORDS) {
            hashes.add(fnv1a(String.join(" ", words)));
            return hashes;
        }
        for (int i = 0; i + SHINGLE_WORDS <= words.size(); i++) {
            hashes.add(fnv1a(String.join(" ", words.subList(i, i + SHINGLE_WORDS))));
        }
        return hashes;
    }

    private static long fnv1a(String s) {
        long h = 0xcbf29ce484222325L;
        for (byte b : s.getBytes(StandardCharsets.UTF_8)) {
            h ^= (b & 0xffL);
            h *= 0x100000001b3L;
        }
        return h;
    }

    /** SplitMix64 finaliser: a cheap, well-distributed 64-bit mixer. */
    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }
}
