package dev.phoenix.discovery;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import dev.phoenix.discovery.PostingEvaluator.Evaluated;
import dev.phoenix.domain.Posting;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FiltersAndPrescoreTest {

    private PostingEvaluator evaluator;

    @BeforeEach
    void setUp() throws IOException {
        FilterConfig config = new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .readValue(Path.of("src/test/resources/fixtures/filters.json").toFile(), FilterConfig.class);
        evaluator = new PostingEvaluator(config);
    }

    private static Posting posting(String company, String title, String location, String jd, boolean verified) {
        return new Posting("greenhouse", company, "1", title, location, "https://example.com/1", jd, verified);
    }

    @Test
    void theExampleFiltersFileOfTheRepoParses() throws IOException {
        FilterConfig config = new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .readValue(Path.of("templates/filters.example.json").toFile(), FilterConfig.class);

        assertThat(config.filters().maxMinYears()).isEqualTo(6);
        assertThat(config.prescore().tier1Bonus()).isEqualTo(15);
        assertThat(config.prescore().yearsIdeal()).containsExactly(2, 5);
        assertThat(config.prescore().levelTitles().keySet().iterator().next()).isEqualTo("sde ii");
        assertThat(config.amazon().resultLimit()).isEqualTo(50);
    }

    @Test
    void prescoresMatchScanPy() {
        Evaluated a = evaluator.evaluate(posting("bigco", "SDE II Payments", "Bengaluru, India",
                "Build Java and Spring services on Kafka for payment and identity flows. 3+ years of experience required.", false));
        Evaluated b = evaluator.evaluate(posting("midco", "Senior Backend Engineer", "Remote - India",
                "Java backend. 8 years of experience in backend.", false));
        Evaluated c = evaluator.evaluate(posting("other", "Software Engineer", "Pune",
                "Javascript and Springs everywhere; JAVA.", false));
        Evaluated d = evaluator.evaluate(posting("other", "Software Engineer", "Pune", "", false));

        assertThat(a.prescore()).isEqualTo(83);
        assertThat(a.filter().requiredYears()).hasValue(3);
        assertThat(b.prescore()).as("asks 8 years, over the cap, so it is filtered before scoring").isNull();
        assertThat(c.prescore()).isEqualTo(22);
        assertThat(d.prescore()).isZero();
    }

    @Test
    void titleLevelAndPenaltyRulesApply() {
        Evaluated e = evaluator.evaluate(posting("other", "Lead Backend Full Stack Engineer", "Pune",
                "Kotlin backend work, 2-4 years experience.", false));

        assertThat(e.filter().passed()).isTrue();
        assertThat(e.prescore()).isZero();
    }

    @Test
    void theTitleFilterIsCheckedFirst() {
        FilterResult result = evaluator.evaluate(posting("other", "Engineering Manager", "Pune", "java", false)).filter();

        assertThat(result.passed()).isFalse();
        assertThat(result.reason()).isEqualTo("title");
    }

    @Test
    void theLocationFilterDropsOtherCountriesUnlessTheSourceAlreadyRestrictedThem() {
        Posting abroad = posting("other", "Software Engineer", "Berlin", "java", false);

        assertThat(evaluator.evaluate(abroad).filter().reason()).isEqualTo("location");
        assertThat(evaluator.evaluate(posting("other", "Software Engineer", "Berlin", "java", true)).filter().passed()).isTrue();
    }

    @Test
    void theDescriptionMustMentionAnIncludeWordAndNoExcludeWord() {
        assertThat(evaluator.evaluate(posting("o", "Software Engineer", "Pune", "cobol only", false)).filter().reason())
                .isEqualTo("jd");
        assertThat(evaluator.evaluate(posting("o", "Software Engineer", "Pune", "java but pure frontend work", false))
                .filter().reason()).isEqualTo("jd");
    }

    @Test
    void anEmptyDescriptionSkipsTheDescriptionFilter() {
        assertThat(evaluator.evaluate(posting("o", "Software Engineer", "Pune", "", false)).filter().passed()).isTrue();
    }

    @Test
    void postingsAskingMoreExperienceThanTheCapAreDropped() {
        FilterResult result = evaluator.evaluate(posting("o", "Software Engineer", "Pune",
                "java, 7 years of experience", false)).filter();

        assertThat(result.passed()).isFalse();
        assertThat(result.reason()).isEqualTo("years");
    }

    @Test
    void anEmptyIncludeListMeansNoRestriction() {
        PostingEvaluator open = new PostingEvaluator(new FilterConfig(null, null,
                new FilterConfig.Filters(null, null, null, null, null, 6), null));

        assertThat(open.evaluate(posting("o", "Anything", "Anywhere", "whatever", false)).filter().passed()).isTrue();
    }
}
