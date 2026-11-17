package dev.phoenix.dedupe;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExactKeyTest {

    @Test
    void companyNamesMeetAtTheSameKeyWhateverTheirSpelling() {
        assertThat(ExactKey.of("Service-Now", " 42 ")).isEqualTo(ExactKey.of("servicenow", "42"));
        assertThat(ExactKey.of("Amazon", "7")).hasToString("amazon:7");
    }

    @Test
    void nullsBecomeEmptyParts() {
        assertThat(ExactKey.of(null, null)).isEqualTo(new ExactKey("", ""));
    }
}
