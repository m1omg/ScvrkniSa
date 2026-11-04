package io.github.m1omg.shrink.core.plan;

import io.github.m1omg.shrink.core.model.Job;

import java.util.Collections;
import java.util.List;

/**
 * The result of planning: the job to run plus anything worth telling the user.
 *
 * @param job          the job (its {@link Job#estimatedBytes()} holds the size estimate)
 * @param warnings     warnings to show, may be empty
 * @param feasible     false when the job cannot produce what was asked (e.g. target too small)
 * @param minimumBytes for target-size plans: the smallest reachable size, otherwise 0
 */
public record Plan(Job job, List<PlanWarning> warnings, boolean feasible, long minimumBytes) {

    public Plan {
        warnings = Collections.unmodifiableList(warnings);
    }

    /** Estimated output size in bytes. */
    public long estimatedBytes() {
        return job.estimatedBytes();
    }

    /**
     * Estimated reduction compared to the original file, in percent. Positive
     * values mean the result is smaller (e.g. 62.0 means "62 % smaller").
     */
    public double reductionPercent() {
        long original = job.source().sizeBytes();
        if (original <= 0) {
            return 0;
        }
        return (1.0 - (double) job.estimatedBytes() / original) * 100.0;
    }

    public boolean has(PlanWarning w) {
        return warnings.contains(w);
    }
}
