package dev.phoenix.service;

import dev.phoenix.domain.Application;
import dev.phoenix.domain.Ask;
import dev.phoenix.domain.AskStatus;
import dev.phoenix.domain.Job;
import dev.phoenix.domain.JobState;
import dev.phoenix.error.UserErrorException;
import dev.phoenix.lifecycle.IllegalTransitionException;
import dev.phoenix.testsupport.Services;
import dev.phoenix.testsupport.TestStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApprovalAndApplicationTest {

    private TestStore store;
    private Services services;

    @BeforeEach
    void setUp(@TempDir Path dir) {
        store = TestStore.create(dir);
        services = new Services(store);
    }

    @AfterEach
    void tearDown() {
        store.close();
    }

    private Job queued(String company, String extId, String title, double score) {
        Job job = store.insertJob("greenhouse", company, extId, title, JobState.CANDIDATE);
        services.scoring.record(List.of(new ScoreInput(job.id(), score, "r", "v1")), "test");
        return store.jobs.findById(job.id()).orElseThrow();
    }

    private ApprovalOutcome approve(Job job) {
        return services.approvals.approve(List.of(job.id()), null, "apply " + job.id(), "claude-chat", "claude-code");
    }

    @Test
    void approvingStoresTheUsersWordsAndMovesTheJobToApproved() {
        Job job = queued("acme", "1", "Backend", 4.4);

        ApprovalOutcome outcome = services.approvals.approve(List.of(job.id()), null, "apply 1 please", "claude-chat", "claude-code");

        assertThat(outcome.approved()).containsExactly(job.id());
        assertThat(store.jobs.findById(job.id()).orElseThrow().state()).isEqualTo(JobState.APPROVED);
        assertThat(store.approvals.findByScope("job:" + job.id())).singleElement().satisfies(a -> {
            assertThat(a.userWords()).isEqualTo("apply 1 please");
            assertThat(a.grantedVia()).isEqualTo("claude-chat");
            assertThat(a.expiresAt()).isEqualTo(store.clock.instant().plus(ApprovalService.TTL));
        });
    }

    @Test
    void approvalsNeedTheUsersWordsAndSomethingToApprove() {
        assertThatThrownBy(() -> services.approvals.approve(List.of(1L), null, " ", "v", "a"))
                .isInstanceOf(UserErrorException.class).hasMessageContaining("own words");
        assertThatThrownBy(() -> services.approvals.approve(List.of(), null, "go", "v", "a"))
                .isInstanceOf(UserErrorException.class).hasMessageContaining("Nothing to approve");
    }

    @Test
    void aRuleIsExpandedAgainstTheQueueAtTheMomentItIsGiven() {
        Job high = queued("acme", "1", "Backend", 4.4);
        Job low = queued("globex", "2", "Backend", 3.6);

        ApprovalOutcome outcome = services.approvals.approve(List.of(), ApprovalRule.parse("fit >= 80"), "apply all >=80", "claude-chat", "claude-code");

        assertThat(outcome.approved()).containsExactly(high.id());
        assertThat(store.jobs.findById(low.id()).orElseThrow().state()).isEqualTo(JobState.QUEUED);
        Job later = queued("initech", "3", "Backend", 4.9);
        assertThat(services.approvals.usableApproval(later.id())).as("a later job is not covered").isEmpty();
    }

    @Test
    void ruleTextIsValidated() {
        assertThat(ApprovalRule.parse("FIT>=85").minFit()).isEqualTo(85);
        assertThatThrownBy(() -> ApprovalRule.parse("everything")).isInstanceOf(UserErrorException.class);
        assertThatThrownBy(() -> ApprovalRule.parse("fit>=101")).isInstanceOf(UserErrorException.class);
    }

    @Test
    void jobsThatAreNotQueuedOrUnknownAreRefusedWithAReason() {
        Job candidate = store.insertJob("acme", "1", "Backend", JobState.CANDIDATE);

        ApprovalOutcome outcome = services.approvals.approve(List.of(candidate.id(), 999L), null, "apply", "v", "a");

        assertThat(outcome.approved()).isEmpty();
        assertThat(outcome.refused()).extracting(ApprovalOutcome.Refusal::reason)
                .anyMatch(r -> r.contains("only QUEUED")).anyMatch(r -> r.contains("not found"));
    }

    @Test
    void theAlreadyAppliedGuardRefusesASecondApprovalForTheSameRole() {
        long group = store.groups.insert("acme", "backend", new int[0], store.clock.instant());
        Job first = store.insertJob("greenhouse", "acme", "1", "Backend", JobState.QUEUED, group);
        store.insertJob("lever", "acme", "2", "Backend", JobState.SUBMITTED, group);

        ApprovalOutcome outcome = approve(first);

        assertThat(outcome.approved()).isEmpty();
        assertThat(outcome.refused().get(0).reason()).contains("BLOCKED_BY_GROUP");
        assertThat(store.approvals.findByScope("job:" + first.id())).isEmpty();
    }

    @Test
    void startingNeedsAnApprovedJobAndAUsableApproval() {
        Job job = queued("acme", "1", "Backend", 4.4);

        StartOutcome notApproved = services.applications.start(job.id(), "claude-code");
        assertThat(notApproved.started()).isFalse();
        assertThat(notApproved.reason()).contains("approved first");

        approve(job);
        store.clock.advance(ApprovalService.TTL.plus(Duration.ofMinutes(1)));
        StartOutcome expired = services.applications.start(job.id(), "claude-code");
        assertThat(expired.started()).isFalse();
        assertThat(expired.reason()).contains("no valid approval");
        assertThat(store.jobs.findById(job.id()).orElseThrow().state()).isEqualTo(JobState.APPROVED);
    }

    @Test
    void startingMovesTheJobToInProgressAndCountsTheAttempt() {
        Job job = queued("acme", "1", "Backend", 4.4);
        approve(job);

        StartOutcome started = services.applications.start(job.id(), "claude-code");

        assertThat(started.started()).isTrue();
        assertThat(store.jobs.findById(job.id()).orElseThrow().state()).isEqualTo(JobState.IN_PROGRESS);
        assertThat(store.applications.findByJob(job.id()).orElseThrow().attempts()).isEqualTo(1);
    }

    @Test
    void startingIsRefusedWhenTheGuardFindsTheRoleAlreadyAppliedTo() {
        long group = store.groups.insert("acme", "backend", new int[0], store.clock.instant());
        Job a = store.insertJob("greenhouse", "acme", "1", "Backend", JobState.QUEUED, group);
        approve(a);
        store.insertJob("lever", "acme", "2", "Backend", JobState.SUBMITTED, group);

        StartOutcome outcome = services.applications.start(a.id(), "claude-code");

        assertThat(outcome.started()).isFalse();
        assertThat(outcome.reason()).contains("BLOCKED_BY_GROUP");
    }

    @Test
    void submittingRecordsTheApplicationAndSpendsTheApproval() {
        Job job = queued("acme", "1", "Backend", 4.4);
        approve(job);
        services.applications.start(job.id(), "claude-code");

        ApplicationService.Reported reported = services.applications.record(
                new ResultReport(job.id(), ApplicationOutcome.SUBMITTED, null, null, "CONF-1"), "claude-code");

        assertThat(reported.job().state()).isEqualTo(JobState.SUBMITTED);
        Application app = store.applications.findByJob(job.id()).orElseThrow();
        assertThat(app.state()).isEqualTo(JobState.SUBMITTED);
        assertThat(app.confirmationRef()).isEqualTo("CONF-1");
        assertThat(app.submittedAt()).isEqualTo(store.clock.instant());
        assertThat(services.approvals.usableApproval(job.id())).isEmpty();
        assertThat(store.jobs.findById(job.id()).orElseThrow().nextActionAt())
                .isEqualTo(store.clock.instant().plus(Duration.ofDays(21)));
    }

    @Test
    void aBlockedAttemptOpensAnAskAndCanBeRetriedWithTheSameApproval() {
        Job job = queued("acme", "1", "Backend", 4.4);
        approve(job);
        services.applications.start(job.id(), "claude-code");

        ApplicationService.Reported reported = services.applications.record(
                new ResultReport(job.id(), ApplicationOutcome.BLOCKED, "captcha", "login wall", null), "claude-code");

        Ask ask = reported.openedAsk();
        assertThat(ask.kind()).isEqualTo("blocker");
        assertThat(ask.prompt()).contains("captcha").contains("login wall").contains("Backend");
        assertThat(ask.status()).isEqualTo(AskStatus.OFFERED);
        assertThat(store.applications.findByJob(job.id()).orElseThrow().blockerType()).isEqualTo("captcha");

        StartOutcome retry = services.applications.start(job.id(), "claude-code");
        assertThat(retry.started()).isTrue();
        assertThat(store.applications.findByJob(job.id()).orElseThrow().attempts()).isEqualTo(2);
        assertThat(store.applications.findByJob(job.id()).orElseThrow().blockerType()).isNull();
    }

    @Test
    void aBlockedReportNeedsABlockerType() {
        Job job = queued("acme", "1", "Backend", 4.4);

        assertThatThrownBy(() -> services.applications.record(
                new ResultReport(job.id(), ApplicationOutcome.BLOCKED, " ", null, null), "x"))
                .isInstanceOf(UserErrorException.class);
    }

    @Test
    void reportingFromAnIllegalStateIsRejectedAndChangesNothing() {
        Job job = queued("acme", "1", "Backend", 4.4);

        assertThatThrownBy(() -> services.applications.record(
                new ResultReport(job.id(), ApplicationOutcome.SUBMITTED, null, null, null), "x"))
                .isInstanceOf(IllegalTransitionException.class);

        assertThat(store.applications.findByJob(job.id())).isEmpty();
    }

    @Test
    void anUnknownSubmitResultCanLaterBeConfirmedAsSubmitted() {
        Job job = queued("acme", "1", "Backend", 4.4);
        approve(job);
        services.applications.start(job.id(), "claude-code");
        services.applications.record(new ResultReport(job.id(), ApplicationOutcome.SUBMIT_UNKNOWN, null, "page froze", null), "x");

        ApplicationService.Reported done = services.applications.record(
                new ResultReport(job.id(), ApplicationOutcome.SUBMITTED, null, null, "CONF-2"), "x");

        assertThat(done.job().state()).isEqualTo(JobState.SUBMITTED);
    }

    @Test
    void skippingAQueuedJobIsAllowed() {
        Job job = queued("acme", "1", "Backend", 4.4);

        services.applications.record(new ResultReport(job.id(), ApplicationOutcome.SKIPPED, null, "not interested", null), "x");

        assertThat(store.jobs.findById(job.id()).orElseThrow().state()).isEqualTo(JobState.SKIPPED);
        assertThat(store.applications.findByJob(job.id()).orElseThrow().note()).isEqualTo("not interested");
    }

    @Test
    void postSubmitOutcomesFollowTheLifecycle() {
        Job job = queued("acme", "1", "Backend", 4.4);
        approve(job);
        services.applications.start(job.id(), "x");
        services.applications.record(new ResultReport(job.id(), ApplicationOutcome.SUBMITTED, null, null, null), "x");

        services.applications.markOutcome(job.id(), PostSubmitOutcome.OA, "x", "hackerrank link");
        services.applications.markOutcome(job.id(), PostSubmitOutcome.INTERVIEW, "x", null);

        assertThat(store.jobs.findById(job.id()).orElseThrow().state()).isEqualTo(JobState.INTERVIEW);
        assertThat(store.applications.findByJob(job.id()).orElseThrow().state()).isEqualTo(JobState.INTERVIEW);
        assertThatThrownBy(() -> services.applications.markOutcome(job.id(), PostSubmitOutcome.OA, "x", null))
                .isInstanceOf(IllegalTransitionException.class);
    }

    @Test
    void outcomeNamesAreParsedCaseInsensitively() {
        assertThat(ApplicationOutcome.parse("Submit_Unknown")).isEqualTo(ApplicationOutcome.SUBMIT_UNKNOWN);
        assertThat(PostSubmitOutcome.parse("offer")).isEqualTo(PostSubmitOutcome.OFFER);
        assertThatThrownBy(() -> ApplicationOutcome.parse("done")).isInstanceOf(UserErrorException.class);
        assertThatThrownBy(() -> PostSubmitOutcome.parse(null)).isInstanceOf(UserErrorException.class);
    }
}
