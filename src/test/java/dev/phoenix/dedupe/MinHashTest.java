package dev.phoenix.dedupe;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MinHashTest {

    private final MinHash minHash = new MinHash();

    /** Distinct pseudo-words so every 5-word shingle in the text is unique. */
    private static List<String> words(String prefix, int count) {
        List<String> words = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            words.add(prefix + i);
        }
        return words;
    }

    @Test
    void theSameTextAlwaysGivesTheSameSignatureEvenFromAnotherInstance() {
        String text = String.join(" ", words("alpha", 60));

        assertThat(new MinHash().signature(text)).containsExactly(minHash.signature(text));
        assertThat(minHash.signature(text)).hasSize(MinHash.HASH_FUNCTIONS);
    }

    @Test
    void identicalTextsAreFullySimilar() {
        String text = String.join(" ", words("alpha", 60));

        assertThat(minHash.similarity(minHash.signature(text), minHash.signature(text))).isEqualTo(1.0);
    }

    @Test
    void caseAndPunctuationDoNotMatter() {
        String a = "Build backend services, using Java and Kafka at scale. Own the whole lifecycle!";
        String b = "build BACKEND services using java and kafka at scale own the whole lifecycle";

        assertThat(minHash.similarity(minHash.signature(a), minHash.signature(b))).isEqualTo(1.0);
    }

    @Test
    void unrelatedTextsAreFarBelowTheThreshold() {
        double similarity = minHash.similarity(
                minHash.signature(String.join(" ", words("alpha", 80))),
                minHash.signature(String.join(" ", words("omega", 80))));

        assertThat(similarity).isLessThan(0.1);
    }

    @Test
    void oneChangedWordInALongDescriptionStaysAboveTheDefaultThreshold() {
        List<String> original = words("alpha", 300);
        List<String> edited = new ArrayList<>(original);
        edited.set(150, "changed");

        double similarity = minHash.similarity(
                minHash.signature(String.join(" ", original)), minHash.signature(String.join(" ", edited)));

        assertThat(similarity).isGreaterThanOrEqualTo(0.9).isLessThan(1.0);
    }

    @Test
    void halfOfTheTextReplacedFallsBelowTheDefaultThreshold() {
        List<String> original = words("alpha", 300);
        List<String> edited = new ArrayList<>(original.subList(0, 150));
        edited.addAll(words("beta", 150));

        double similarity = minHash.similarity(
                minHash.signature(String.join(" ", original)), minHash.signature(String.join(" ", edited)));

        assertThat(similarity).isLessThan(0.9).isGreaterThan(0.2);
    }

    @Test
    void textsShorterThanOneShingleStillGetASignature() {
        assertThat(minHash.signature("java backend")).hasSize(MinHash.HASH_FUNCTIONS);
    }

    @Test
    void emptyTextHasAnEmptySignatureThatOnlyMatchesAnotherEmptyOne() {
        int[] empty = minHash.signature("  ");
        int[] real = minHash.signature(String.join(" ", words("alpha", 20)));

        assertThat(empty).isEmpty();
        assertThat(minHash.signature(null)).isEmpty();
        assertThat(minHash.similarity(empty, minHash.signature(""))).isEqualTo(1.0);
        assertThat(minHash.similarity(empty, real)).isEqualTo(0.0);
    }
}
