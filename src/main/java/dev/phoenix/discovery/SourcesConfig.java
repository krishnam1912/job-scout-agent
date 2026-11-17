package dev.phoenix.discovery;

import java.util.List;

/** Which public job boards to scan: board tokens per ATS, plus the two company-wide feeds. */
public record SourcesConfig(List<String> greenhouse, List<String> lever, List<String> ashby,
                            List<String> smartrecruiters, boolean amazon, boolean atlassian) {

    public SourcesConfig {
        greenhouse = greenhouse == null ? List.of() : List.copyOf(greenhouse);
        lever = lever == null ? List.of() : List.copyOf(lever);
        ashby = ashby == null ? List.of() : List.copyOf(ashby);
        smartrecruiters = smartrecruiters == null ? List.of() : List.copyOf(smartrecruiters);
    }
}
