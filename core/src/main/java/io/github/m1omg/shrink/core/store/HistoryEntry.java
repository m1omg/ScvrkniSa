package io.github.m1omg.shrink.core.store;

import io.github.m1omg.shrink.core.model.JobKind;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * One finished (or failed) job in the history list.
 *
 * @param id          unique id
 * @param timestamp   when the job finished, milliseconds since 1970
 * @param kind        which tool was used
 * @param status      outcome
 * @param inputName   file name of the original
 * @param inputPath   path or URI of the original
 * @param inputBytes  size of the original
 * @param outputName  file name of the result (empty on failure)
 * @param outputPath  path or URI of the result
 * @param outputBytes size of the result
 * @param duration    length of the result in seconds
 * @param details     short technical summary, e.g. "H.264 · 1280 × 720 · 1.1 Mbps"
 * @param message     error message for failed jobs
 */
public record HistoryEntry(String id, long timestamp, JobKind kind, Status status,
                           String inputName, String inputPath, long inputBytes,
                           String outputName, String outputPath, long outputBytes,
                           double duration, String details, String message) {

    /** Outcome of a job. */
    public enum Status { DONE, FAILED, CANCELLED }

    public static String newId() {
        return UUID.randomUUID().toString();
    }

    /** Reduction in percent (positive = smaller), or 0 if unknown. */
    public double reductionPercent() {
        if (inputBytes <= 0 || outputBytes <= 0) {
            return 0;
        }
        return (1.0 - (double) outputBytes / inputBytes) * 100.0;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("timestamp", timestamp);
        m.put("kind", kind.id());
        m.put("status", status.name().toLowerCase(java.util.Locale.ROOT));
        m.put("inputName", inputName);
        m.put("inputPath", inputPath);
        m.put("inputBytes", inputBytes);
        m.put("outputName", outputName);
        m.put("outputPath", outputPath);
        m.put("outputBytes", outputBytes);
        m.put("duration", duration);
        m.put("details", details);
        m.put("message", message);
        return m;
    }

    public static HistoryEntry fromMap(Map<String, Object> m) {
        Status status = Status.DONE;
        String s = Json.getString(m, "status", "done");
        for (Status candidate : Status.values()) {
            if (candidate.name().equalsIgnoreCase(s)) {
                status = candidate;
            }
        }
        return new HistoryEntry(
                Json.getString(m, "id", newId()),
                Json.getLong(m, "timestamp", 0),
                JobKind.fromId(Json.getString(m, "kind", null), JobKind.COMPRESS),
                status,
                Json.getString(m, "inputName", ""),
                Json.getString(m, "inputPath", ""),
                Json.getLong(m, "inputBytes", 0),
                Json.getString(m, "outputName", ""),
                Json.getString(m, "outputPath", ""),
                Json.getLong(m, "outputBytes", 0),
                Json.getDouble(m, "duration", 0),
                Json.getString(m, "details", ""),
                Json.getString(m, "message", ""));
    }
}
