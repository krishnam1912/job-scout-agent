package dev.phoenix.discovery;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CandidateSelectorTest {

    private final CandidateSelector selector = new CandidateSelector();

    private static CandidateView view(String extId, Long group, int prescore) {
        return new CandidateView(null, group, "acme", extId, "Backend", "Pune", "", prescore, "");
    }

    @Test
    void ordersByPrescoreAndKeepsFetchOrderForTies() {
        List<CandidateView> chosen = selector.select(
                List.of(view("a", null, 10), view("b", null, 30), view("c", null, 30), view("d", null, 20)), Set.of(), 10);

        assertThat(chosen).extracting(CandidateView::extId).containsExactly("b", "c", "d", "a");
    }

    @Test
    void showsOnlyTheBestMemberOfAGroupAndTheNewestOnATie() {
        List<CandidateView> chosen = selector.select(
                List.of(view("100", 1L, 50), view("300", 1L, 50), view("200", 1L, 40), view("7", 2L, 45)), Set.of(), 10);

        assertThat(chosen).extracting(CandidateView::extId).containsExactly("300", "7");
    }

    @Test
    void aBetterScoreBeatsANewerId() {
        List<CandidateView> chosen = selector.select(List.of(view("900", 1L, 40), view("100", 1L, 60)), Set.of(), 10);

        assertThat(chosen).extracting(CandidateView::extId).containsExactly("100");
    }

    @Test
    void groupsWithAnEngagedMemberAreHidden() {
        List<CandidateView> chosen = selector.select(List.of(view("a", 1L, 90), view("b", 2L, 10)), Set.of(1L), 10);

        assertThat(chosen).extracting(CandidateView::extId).containsExactly("b");
    }

    @Test
    void theLimitCapsTheResultAndZeroReturnsNothing() {
        List<CandidateView> pool = List.of(view("a", null, 3), view("b", null, 2), view("c", null, 1));

        assertThat(selector.select(pool, Set.of(), 2)).extracting(CandidateView::extId).containsExactly("a", "b");
        assertThat(selector.select(pool, Set.of(), 0)).isEmpty();
    }
}
