package dev.phoenix.legacy;

import dev.phoenix.dedupe.ExactKey;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Splits "Company Role" cells of tables that have no company column. A company is recognised from the names the
 * other legacy files already used (longest match first); otherwise the first word is taken.
 */
final class CompanyNames {

    private static final int MAX_WORDS = 4;

    private final Set<String> known = new HashSet<>();

    void learn(String company) {
        known.add(ExactKey.normalizeCompany(company));
    }

    /** @return {company, rest of the cell} */
    String[] split(String cell) {
        String[] words = cell.strip().split("\\s+");
        for (int n = Math.min(MAX_WORDS, words.length); n >= 1; n--) {
            String candidate = String.join(" ", Arrays.copyOfRange(words, 0, n));
            if (known.contains(ExactKey.normalizeCompany(candidate))) {
                return new String[] {candidate, String.join(" ", Arrays.copyOfRange(words, n, words.length))};
            }
        }
        return new String[] {words[0], String.join(" ", Arrays.copyOfRange(words, 1, words.length))};
    }
}
