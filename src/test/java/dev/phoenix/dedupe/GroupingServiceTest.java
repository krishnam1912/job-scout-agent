package dev.phoenix.dedupe;

import dev.phoenix.dedupe.GroupingService.Item;
import dev.phoenix.testsupport.TestStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GroupingServiceTest {

    private static final String JD = jd("alpha", 120);

    private TestStore store;
    private GroupingService grouping;

    private static String jd(String prefix, int words) {
        List<String> list = new ArrayList<>();
        for (int i = 0; i < words; i++) {
            list.add(prefix + i);
        }
        return String.join(" ", list);
    }

    @BeforeEach
    void setUp(@TempDir Path dir) {
        store = TestStore.create(dir);
        grouping = new GroupingService(store.groups, new MinHash(), 0.9, store.clock);
    }

    @AfterEach
    void tearDown() {
        store.close();
    }

    @Test
    void sameCompanyTitleAndDescriptionShareAGroup() {
        List<Long> ids = grouping.assign(List.of(
                new Item("Acme", "Backend Engineer", JD),
                new Item("acme", "backend  engineer", JD)), true);

        assertThat(ids.get(0)).isNotNull().isEqualTo(ids.get(1));
    }

    @Test
    void theSameRoleFromAnotherSourceJoinsTheExistingPersistedGroup() {
        Long first = grouping.assign(List.of(new Item("Acme", "Backend Engineer", JD)), true).get(0);

        Long second = grouping.assign(List.of(new Item("Acme", "Backend Engineer", JD)), true).get(0);

        assertThat(second).isEqualTo(first);
    }

    @Test
    void sameTitleWithADifferentDescriptionIsAnotherGroup() {
        List<Long> ids = grouping.assign(List.of(
                new Item("Acme", "Backend Engineer", JD),
                new Item("Acme", "Backend Engineer", jd("omega", 120))), true);

        assertThat(ids.get(0)).isNotEqualTo(ids.get(1));
    }

    @Test
    void sameDescriptionWithADifferentTitleIsAnotherGroup() {
        List<Long> ids = grouping.assign(List.of(
                new Item("Acme", "Backend Engineer", JD),
                new Item("Acme", "Platform Engineer", JD)), true);

        assertThat(ids.get(0)).isNotEqualTo(ids.get(1));
    }

    @Test
    void groupsNeverCrossCompanies() {
        List<Long> ids = grouping.assign(List.of(
                new Item("Acme", "Backend Engineer", JD),
                new Item("Globex", "Backend Engineer", JD)), true);

        assertThat(ids.get(0)).isNotEqualTo(ids.get(1));
    }

    @Test
    void aBlankTitleCannotBeGrouped() {
        assertThat(grouping.assign(List.of(new Item("Acme", " - ", JD)), true)).containsExactly((Long) null);
    }

    @Test
    void aDryRunWritesNothingAndUsesSyntheticIds() {
        List<Long> ids = grouping.assign(List.of(
                new Item("Acme", "Backend Engineer", JD),
                new Item("Acme", "Backend Engineer", JD)), false);

        assertThat(ids).allMatch(id -> id < 0).containsOnly(ids.get(0));
        assertThat(store.groups.findByCompany("acme")).isEmpty();
    }

    @Test
    void persistedSignaturesSurviveTheRoundTrip() {
        grouping.assign(List.of(new Item("Acme", "Backend Engineer", JD)), true);

        int[] stored = store.groups.findByCompany("acme").get(0).signature();

        assertThat(stored).containsExactly(new MinHash().signature(JD));
    }

    @Test
    void aGroupWithAMemberBeyondCandidateCountsAsEngaged() {
        long group = grouping.assign(List.of(new Item("Acme", "Backend Engineer", JD)), true).get(0);
        store.insertJob("greenhouse", "acme", "1", "Backend Engineer", dev.phoenix.domain.JobState.CANDIDATE, group);
        assertThat(store.groups.hasEngagedMember(group)).isFalse();

        store.insertJob("lever", "acme", "2", "Backend Engineer", dev.phoenix.domain.JobState.SCORED, group);

        assertThat(store.groups.hasEngagedMember(group)).isTrue();
    }
}
