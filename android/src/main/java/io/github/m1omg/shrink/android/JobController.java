package io.github.m1omg.shrink.android;

import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import io.github.m1omg.shrink.android.media.EncoderSupport;
import io.github.m1omg.shrink.android.media.Media3Runner;
import io.github.m1omg.shrink.android.media.OutputStore;
import io.github.m1omg.shrink.android.service.ProcessingService;
import io.github.m1omg.shrink.core.model.Job;
import io.github.m1omg.shrink.core.model.MediaInfo;
import io.github.m1omg.shrink.core.plan.JobPlanner;
import io.github.m1omg.shrink.core.plan.PlanText;
import io.github.m1omg.shrink.core.store.HistoryEntry;
import io.github.m1omg.shrink.core.util.OutputNames;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Runs one job at a time and keeps its state, so the progress screen, the
 * notification and the history all see the same thing. Lives as long as the app.
 * All methods are called on the main thread.
 */
public final class JobController {

    private static final String TAG = "Shrink";

    /** What the controller is doing. */
    public enum State { IDLE, RUNNING, SAVING, DONE, FAILED }

    /** Notified on the main thread whenever anything changes. */
    public interface Listener {
        void jobChanged();
    }

    private final ShrinkApp app;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();

    private State state = State.IDLE;
    private Job job;
    private int percent;
    private long startedNanos;
    private double remainingSeconds = -1;
    private Media3Runner runner;
    private File workFile;
    private Uri input;
    private Uri destination;
    private String outputName;
    /** The first result while a corrected second pass runs, else null. */
    private File firstPass;
    private boolean correcting;
    private Uri resultUri;
    private String resultName;
    private long resultBytes;
    private String error;

    JobController(ShrinkApp app) {
        this.app = app;
    }

    public State state() {
        return state;
    }

    public Job job() {
        return job;
    }

    public int percent() {
        return percent;
    }

    /** Estimated seconds left, or -1 when unknown. */
    public double remainingSeconds() {
        return remainingSeconds;
    }

    public Uri resultUri() {
        return resultUri;
    }

    public String resultName() {
        return resultName;
    }

    public long resultBytes() {
        return resultBytes;
    }

    public String error() {
        return error;
    }

    /** True while a second pass runs because the first result came out too big. */
    public boolean isCorrecting() {
        return correcting;
    }

    public boolean isBusy() {
        return state == State.RUNNING || state == State.SAVING;
    }

    /**
     * Starts a job.
     *
     * @param destination a user-chosen file (Storage Access Framework), or
     *                    {@code null} to save into Movies/Shrink or Music/Shrink
     * @param fileSuffix  text added to the file name, e.g. "-compressed"
     */
    public void start(Job job, Uri input, Uri destination, String fileSuffix) {
        if (isBusy()) {
            return;
        }
        this.job = job;
        this.percent = 0;
        this.remainingSeconds = -1;
        this.resultUri = null;
        this.resultName = null;
        this.resultBytes = 0;
        this.error = null;
        this.startedNanos = System.nanoTime();
        this.input = input;
        this.destination = destination;
        this.firstPass = null;
        this.correcting = false;
        outputName = OutputNames.sanitize(OutputNames.suggest(job.source().fileName(), fileSuffix,
                job.outputExtension(), null));
        File dir = new File(app.getCacheDir(), "output");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        workFile = new File(dir, outputName);
        //noinspection ResultOfMethodCallIgnored
        workFile.delete();
        state = State.RUNNING;
        fire();
        ProcessingService.start(app);
        runPass(job, workFile, false);
    }

    private void runPass(Job passJob, File out, boolean constantBitrate) {
        runner = new Media3Runner(app);
        runner.start(passJob, input, out, new Media3Runner.Listener() {
            @Override
            public void progress(int p) {
                percent = Math.max(percent, Math.min(100, p));
                updateEta();
                fire();
            }

            @Override
            public void completed(File output) {
                passCompleted(output);
            }

            @Override
            public void failed(String message) {
                if (firstPass != null) {
                    // The corrected pass failed, but the first result is still good.
                    useResult(firstPass);
                } else {
                    finishFailed(message);
                }
            }
        }, constantBitrate);
    }

    private void passCompleted(File output) {
        if (state != State.RUNNING) {
            return;
        }
        if (firstPass == null) {
            Job corrected = JobPlanner.correctedForSize(job, output.length());
            if (corrected != null) {
                // The encoder overshot the estimate. Encoders keep to a constant bitrate
                // (CBR) much more strictly than to the default variable one, so the second
                // try uses CBR at the planned bitrate when the phone offers it, and
                // otherwise a proportionally lower variable bitrate.
                Job adapted = Media3Runner.adapt(job);
                boolean cbr = adapted.video().isEncode()
                        && EncoderSupport.supportsConstantBitrate(EncoderSupport.mimeOf(adapted.video().codec()));
                Job second = cbr ? job : corrected;
                Log.i(TAG, "Result " + output.length() + " B is far above the estimate of "
                        + job.estimatedBytes() + " B; second pass at " + second.video().bitrate() + " bit/s"
                        + (cbr ? " (constant)" : ""));
                firstPass = output;
                correcting = true;
                percent = 0;
                remainingSeconds = -1;
                startedNanos = System.nanoTime();
                workFile = new File(output.getParentFile(), "pass2-" + output.getName());
                //noinspection ResultOfMethodCallIgnored
                workFile.delete();
                fire();
                runPass(second, workFile, cbr);
                return;
            }
            useResult(output);
            return;
        }
        // Keep whichever of the two results is smaller.
        File keep = output.length() < firstPass.length() ? output : firstPass;
        File drop = keep == output ? firstPass : output;
        //noinspection ResultOfMethodCallIgnored
        drop.delete();
        useResult(keep);
    }

    private void useResult(File output) {
        firstPass = null;
        correcting = false;
        save(output, outputName, destination);
    }

    private void updateEta() {
        double elapsed = (System.nanoTime() - startedNanos) / 1e9;
        if (percent < 2 || elapsed < 1.5) {
            return;
        }
        double raw = elapsed * (100 - percent) / percent;
        remainingSeconds = remainingSeconds < 0 ? raw : remainingSeconds * 0.7 + raw * 0.3;
    }

    private void save(File output, String name, Uri destination) {
        state = State.SAVING;
        percent = 100;
        fire();
        boolean audio = job.isAudioOnly();
        String mime = audio ? job.audioFormat().mimeType() : job.container().mimeType();
        app.background().execute(() -> {
            try {
                long bytes = output.length();
                Uri uri;
                if (destination != null) {
                    OutputStore.copyTo(app, output, destination);
                    uri = destination;
                } else {
                    uri = OutputStore.saveToMediaStore(app, output, name, mime, audio);
                }
                //noinspection ResultOfMethodCallIgnored
                output.delete();
                main.post(() -> finishDone(uri, name, bytes));
            } catch (IOException e) {
                main.post(() -> finishFailed(e.getMessage()));
            }
        });
    }

    private void finishDone(Uri uri, String name, long bytes) {
        state = State.DONE;
        resultUri = uri;
        resultName = name;
        resultBytes = bytes;
        MediaInfo src = job.source();
        if (app.settings().isHistoryEnabled()) {
            app.history().add(new HistoryEntry(HistoryEntry.newId(), System.currentTimeMillis(), job.kind(),
                    HistoryEntry.Status.DONE, src.fileName(), src.location(), src.sizeBytes(), name, uri.toString(),
                    bytes, job.outputDuration(), PlanText.summary(Media3Runner.adapt(job)), ""));
        }
        ProcessingService.finished(app, true);
        fire();
    }

    private void finishFailed(String message) {
        state = State.FAILED;
        error = message == null ? "?" : message;
        if (workFile != null) {
            //noinspection ResultOfMethodCallIgnored
            workFile.delete();
        }
        MediaInfo src = job.source();
        if (app.settings().isHistoryEnabled()) {
            app.history().add(new HistoryEntry(HistoryEntry.newId(), System.currentTimeMillis(), job.kind(),
                    HistoryEntry.Status.FAILED, src.fileName(), src.location(), src.sizeBytes(), "", "", 0,
                    job.outputDuration(), PlanText.summary(Media3Runner.adapt(job)), error));
        }
        ProcessingService.finished(app, false);
        fire();
    }

    /** Stops the running job and throws away the unfinished file. */
    public void cancel() {
        if (state != State.RUNNING) {
            return;
        }
        if (runner != null) {
            runner.cancel();
        }
        for (File f : new File[] {workFile, firstPass}) {
            if (f != null) {
                main.postDelayed(() -> {
                    //noinspection ResultOfMethodCallIgnored
                    f.delete();
                }, 500);
            }
        }
        firstPass = null;
        correcting = false;
        state = State.IDLE;
        ProcessingService.stop(app);
        fire();
    }

    /** Forgets a finished or failed job (when the user leaves the result screen). */
    public void dismiss() {
        if (state == State.DONE || state == State.FAILED) {
            state = State.IDLE;
            fire();
        }
    }

    public void addListener(Listener l) {
        listeners.add(l);
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    private void fire() {
        for (Listener l : listeners) {
            l.jobChanged();
        }
    }
}
