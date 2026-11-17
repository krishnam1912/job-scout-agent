package dev.phoenix.legacy;

import dev.phoenix.error.UserErrorException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Reads the legacy seen list: one {@code company:id} per line, blank lines and # comments ignored. */
final class SeenListParser {

    ParsedFile<LegacyJob> parse(Path file) {
        ParsedFile<LegacyJob> out = new ParsedFile<>(file.getFileName().toString());
        List<String> lines;
        try {
            lines = Files.readAllLines(file);
        } catch (IOException e) {
            throw new UserErrorException("Cannot read " + file.getFileName() + ": " + e.getMessage(), e);
        }
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int colon = line.indexOf(':');
            if (colon <= 0 || colon == line.length() - 1) {
                out.reject(i + 1, "expected company:id");
                continue;
            }
            out.accept(new LegacyJob(LegacyJob.Origin.SEEN_LIST, line.substring(0, colon).strip(),
                    line.substring(colon + 1).strip(), false, null, null, null, null, null, null, null,
                    "seen in the legacy list"));
        }
        return out;
    }
}
