package dev.phoenix.dedupe;

import java.util.Locale;

/**
 * Layer 1: the identity of a posting. Company names are reduced to lowercase letters and digits so
 * "Amazon", "amazon" and a board token all meet at the same key.
 */
public record ExactKey(String company, String extId) {

    public static ExactKey of(String company, String extId) {
        return new ExactKey(normalizeCompany(company), extId == null ? "" : extId.strip());
    }

    public static String normalizeCompany(String company) {
        return company == null ? "" : company.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    @Override
    public String toString() {
        return company + ":" + extId;
    }
}
