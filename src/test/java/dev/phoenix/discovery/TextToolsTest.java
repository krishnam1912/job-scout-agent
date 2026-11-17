package dev.phoenix.discovery;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.assertThat;

/** Expected values were produced by running the same inputs through scripts/scan.py. */
class TextToolsTest {

    @Test
    void htmlIsUnescapedStrippedAndCollapsed() {
        assertThat(HtmlText.strip("&lt;p&gt;Hello&amp;nbsp;<b>World</b>&lt;/p&gt;&lt;ul&gt;&lt;li&gt;one&lt;/li&gt;&lt;li&gt;two&lt;/li&gt;&lt;/ul&gt;"))
                .isEqualTo("Hello World one two");
        assertThat(HtmlText.strip("<p>Tom &amp; Jerry&#39;s</p><br/>next&nbsp;line")).isEqualTo("Tom & Jerry's next line");
        assertThat(HtmlText.strip(null)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "I know Java.|true", "javascript|false", "Springs boot|false", "spring  boot|false", "C++ dev|true",
            "xjava|false", "JAVAs|true", "java9|false", "use c++s|true"})
    void keywordsMatchWholeWordsCaseInsensitivelyWithAPluralSuffix(String text, boolean expected) {
        KeywordMatcher matcher = KeywordMatcher.of(List.of("java", "c++", "spring boot"));

        assertThat(matcher.matches(text)).isEqualTo(expected);
    }

    @Test
    void anEmptyKeywordListMatchesNothing() {
        assertThat(KeywordMatcher.of(List.of()).matches("java")).isFalse();
        assertThat(KeywordMatcher.of(List.of()).isEmpty()).isTrue();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', nullValues = "NONE", value = {
            "5+ years of experience|5", "experience: 3 to 5 yrs|3", "7-10 years experience needed|7",
            "20 years of experience|NONE", "no number here experience|NONE", "Minimum 4 years|NONE", "exp. 2 yrs|2"})
    void requiredYearsFollowsScanPy(String text, Integer expected) {
        OptionalInt years = ExperienceYears.required(text);

        if (expected == null) {
            assertThat(years).isEmpty();
        } else {
            assertThat(years).hasValue(expected);
        }
    }

    @Test
    void theHighestPlausibleRequirementWins() {
        assertThat(ExperienceYears.required("2 years of experience, or 4 years of experience with a degree"))
                .hasValue(4);
    }

    @Test
    void aShortDescriptionIsReturnedAsIs() {
        assertThat(JdSummary.of("short text")).isEqualTo("short text");
    }

    @Test
    void aLongDescriptionKeepsTheRoleStartAndTheQualificationsWithinTheLimit() {
        String jd = "intro ".repeat(50) + "About the role " + "x ".repeat(500) + "Basic qualifications: java " + "y ".repeat(500);

        String summary = JdSummary.of(jd);

        assertThat(summary).hasSize(1498).startsWith("About the role x x x")
                .contains(" … Basic qualifications: java y y");
    }

    @Test
    void aLongDescriptionWithoutSectionsIsCutAtTheLimit() {
        String jd = String.join(" ", java.util.stream.IntStream.range(0, 300).mapToObj(i -> "w" + i).toList());

        assertThat(JdSummary.of(jd)).hasSize(1389);
    }
}
