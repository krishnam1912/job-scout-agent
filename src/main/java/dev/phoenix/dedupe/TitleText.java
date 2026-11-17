package dev.phoenix.dedupe;

import java.util.Locale;

/** Title helpers shared by grouping and the cooldown guard. */
public final class TitleText {

    private TitleText() {
    }

    /** Lowercase, every run of non-alphanumerics becomes one space (same rule scan.py used to collapse reqs). */
    public static String normalize(String title) {
        return title == null ? "" : title.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").strip();
    }

    /** 1.0 for identical normalised titles, falling with the edit distance relative to the longer title. */
    public static double similarity(String a, String b) {
        String x = normalize(a);
        String y = normalize(b);
        int longest = Math.max(x.length(), y.length());
        if (longest == 0) {
            return 1.0;
        }
        return 1.0 - (double) editDistance(x, y) / longest;
    }

    private static int editDistance(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] swap = prev;
            prev = cur;
            cur = swap;
        }
        return prev[b.length()];
    }
}
