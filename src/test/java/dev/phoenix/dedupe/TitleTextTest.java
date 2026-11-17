package dev.phoenix.dedupe;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TitleTextTest {

    @Test
    void normalizeCollapsesPunctuationAndCase() {
        assertThat(TitleText.normalize("  Software Engineer II - (Payments) ")).isEqualTo("software engineer ii payments");
        assertThat(TitleText.normalize(null)).isEmpty();
    }

    @Test
    void identicalNormalisedTitlesScoreOne() {
        assertThat(TitleText.similarity("SDE II, Payments", "sde ii payments")).isEqualTo(1.0);
    }

    @Test
    void similarityFallsWithEditDistance() {
        assertThat(TitleText.similarity("software engineer ii", "software engineer iii")).isBetween(0.9, 1.0);
        assertThat(TitleText.similarity("backend engineer", "data scientist")).isLessThan(0.5);
    }

    @Test
    void twoBlankTitlesAreTreatedAsEqual() {
        assertThat(TitleText.similarity("", null)).isEqualTo(1.0);
    }
}
