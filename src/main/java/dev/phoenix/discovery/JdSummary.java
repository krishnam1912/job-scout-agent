package dev.phoenix.discovery;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Shortens a description for the operator: the start of the role section plus the qualifications section. */
public final class JdSummary {

    public static final int DEFAULT_LIMIT = 1500;

    private static final Pattern ROLE_START = Pattern.compile(
            "(about the role|the role|what you'?ll do|what you will do|responsibilities|in this role|your role|"
                    + "key responsibilities|job description)", Pattern.CASE_INSENSITIVE);
    private static final Pattern QUALIFICATIONS_START = Pattern.compile(
            "(basic qualifications|minimum qualifications|qualifications|requirements|what you'?ll need|"
                    + "what we'?re looking for|required skills)", Pattern.CASE_INSENSITIVE);
    private static final int ROLE_CHARS = 800;
    private static final int TAIL_GUARD = 600;
    private static final int SEPARATOR_RESERVE = 5;

    private JdSummary() {
    }

    public static String of(String jd) {
        return of(jd, DEFAULT_LIMIT);
    }

    public static String of(String jd, int limit) {
        if (jd.length() <= limit) {
            return jd;
        }
        Matcher role = ROLE_START.matcher(jd);
        int roleStart = role.find() && role.start() < jd.length() - TAIL_GUARD ? role.start() : 0;
        String roleSection = slice(jd, roleStart, roleStart + ROLE_CHARS);
        int searchFrom = roleStart + ROLE_CHARS;
        Matcher qualifications = QUALIFICATIONS_START.matcher(jd);
        String out;
        if (searchFrom <= jd.length() && qualifications.find(searchFrom)) {
            int from = qualifications.start();
            out = roleSection + " … " + slice(jd, from, from + (limit - roleSection.length() - SEPARATOR_RESERVE));
        } else {
            out = slice(jd, roleStart, roleStart + limit);
        }
        return slice(out, 0, limit);
    }

    private static String slice(String s, int from, int to) {
        int end = Math.min(Math.max(to, from), s.length());
        return s.substring(Math.min(from, s.length()), end);
    }
}
