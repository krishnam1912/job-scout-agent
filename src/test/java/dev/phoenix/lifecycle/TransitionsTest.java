package dev.phoenix.lifecycle;

import dev.phoenix.domain.JobState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static dev.phoenix.domain.JobState.*;
import static org.assertj.core.api.Assertions.assertThat;

class TransitionsTest {

    /** The spec table, written out independently of the implementation. */
    private static final Map<JobState, Set<JobState>> SPEC = Map.ofEntries(
            Map.entry(DISCOVERED, EnumSet.of(FILTERED_OUT, CANDIDATE)),
            Map.entry(CANDIDATE, EnumSet.of(SCORED)),
            Map.entry(SCORED, EnumSet.of(NEAR_MISS, BELOW, QUEUED)),
            Map.entry(NEAR_MISS, EnumSet.of(SCORED)),
            Map.entry(BELOW, EnumSet.of(SCORED)),
            Map.entry(QUEUED, EnumSet.of(APPROVED, SKIPPED)),
            Map.entry(APPROVED, EnumSet.of(IN_PROGRESS, SKIPPED, QUEUED)),
            Map.entry(IN_PROGRESS, EnumSet.of(SUBMITTED, BLOCKED, FAILED, SUBMIT_UNKNOWN, SKIPPED)),
            Map.entry(BLOCKED, EnumSet.of(IN_PROGRESS, SKIPPED)),
            Map.entry(FAILED, EnumSet.of(IN_PROGRESS, SKIPPED)),
            Map.entry(SUBMIT_UNKNOWN, EnumSet.of(SUBMITTED, IN_PROGRESS)),
            Map.entry(SUBMITTED, EnumSet.of(OA, INTERVIEW, REJECTED, GHOSTED)),
            Map.entry(OA, EnumSet.of(INTERVIEW, REJECTED)),
            Map.entry(INTERVIEW, EnumSet.of(OFFER, REJECTED)));

    private static Set<JobState> expectedFrom(JobState from) {
        Set<JobState> allowed = EnumSet.noneOf(JobState.class);
        allowed.addAll(SPEC.getOrDefault(from, Set.of()));
        if (!from.isTerminal()) {
            allowed.add(CLOSED);
            allowed.add(EXPIRED);
        }
        return allowed;
    }

    static Stream<Arguments> allPairs() {
        List<Arguments> pairs = new ArrayList<>();
        for (JobState from : JobState.values()) {
            for (JobState to : JobState.values()) {
                pairs.add(Arguments.of(from, to, expectedFrom(from).contains(to)));
            }
        }
        return pairs.stream();
    }

    @ParameterizedTest(name = "{0} -> {1} allowed={2}")
    @MethodSource("allPairs")
    void everyPairMatchesTheSpecTable(JobState from, JobState to, boolean allowed) {
        assertThat(Transitions.isAllowed(from, to)).isEqualTo(allowed);
    }

    @Test
    void terminalStatesHaveNoWayOut() {
        for (JobState s : JobState.values()) {
            if (s.isTerminal()) {
                for (JobState to : JobState.values()) {
                    assertThat(Transitions.isAllowed(s, to)).as(s + " -> " + to).isFalse();
                }
            }
        }
    }

    @Test
    void terminalSetMatchesTheSpec() {
        Set<JobState> terminal = EnumSet.noneOf(JobState.class);
        for (JobState s : JobState.values()) {
            if (s.isTerminal()) {
                terminal.add(s);
            }
        }
        assertThat(terminal).containsExactlyInAnyOrder(FILTERED_OUT, SKIPPED, REJECTED, OFFER, GHOSTED, CLOSED, EXPIRED);
    }
}
