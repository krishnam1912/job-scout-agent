package dev.phoenix.legacy;

import dev.phoenix.domain.Referral;
import dev.phoenix.error.UserErrorException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the referral tracker CSV. Columns are found by the start of their header (the old header carries a long
 * description of the connection column), in this order: Date, Name, Connection, Company, Role / Job ID, Message sent,
 * Referred?, Follow-up date, Notes.
 */
final class ReferralsCsvParser {

    private static final int COLUMNS = 9;

    ParsedFile<Referral> parse(Path file) {
        ParsedFile<Referral> out = new ParsedFile<>(file.getFileName().toString());
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8);
             CSVParser csv = CSVFormat.DEFAULT.builder().setIgnoreSurroundingSpaces(true).get().parse(reader)) {
            boolean header = true;
            for (CSVRecord row : csv) {
                if (header) {
                    header = false;
                    continue;
                }
                int line = (int) csv.getCurrentLineNumber();
                if (row.size() != COLUMNS) {
                    out.reject(line, "expected " + COLUMNS + " columns, found " + row.size());
                } else if (row.get(1).isBlank()) {
                    out.reject(line, "row has no name");
                } else {
                    out.accept(toReferral(row));
                }
            }
        } catch (IOException e) {
            throw new UserErrorException("Cannot read " + file.getFileName() + ": " + e.getMessage(), e);
        }
        return out;
    }

    private static Referral toReferral(CSVRecord row) {
        List<String> noteParts = new ArrayList<>();
        if (!row.get(5).isBlank()) {
            noteParts.add("message sent: " + row.get(5));
        }
        if (!row.get(8).isBlank()) {
            noteParts.add(row.get(8));
        }
        return new Referral(row.get(1), blankToNull(row.get(2)), blankToNull(row.get(3)), blankToNull(row.get(4)),
                blankToNull(row.get(6)), blankToNull(row.get(0)), blankToNull(row.get(7)),
                noteParts.isEmpty() ? null : String.join("; ", noteParts));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
