package dev.phoenix.service;

import dev.phoenix.error.UserErrorException;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A standing instruction typed by the user such as {@code fit>=80}: approve every queued job at or above that fit. */
public record ApprovalRule(int minFit) {

    private static final Pattern FORMAT = Pattern.compile("fit\\s*>=\\s*(\\d{1,3})");

    public static ApprovalRule parse(String text) {
        Matcher m = FORMAT.matcher(text == null ? "" : text.strip().toLowerCase(Locale.ROOT));
        if (!m.matches() || Integer.parseInt(m.group(1)) > 100) {
            throw new UserErrorException("Unsupported rule '" + text + "'; the only form is fit>=N with N from 0 to 100");
        }
        return new ApprovalRule(Integer.parseInt(m.group(1)));
    }
}
