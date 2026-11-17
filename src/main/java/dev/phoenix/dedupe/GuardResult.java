package dev.phoenix.dedupe;

/** Outcome of the already-applied check; {@code blockingJobId} points at the job that caused a block. */
public record GuardResult(GuardVerdict verdict, Long blockingJobId, String reason) {

    public static GuardResult ok() {
        return new GuardResult(GuardVerdict.OK, null, "no conflicting application");
    }

    public boolean allowed() {
        return verdict == GuardVerdict.OK;
    }
}
