package io.github.m1omg.shrink.android;

import android.graphics.Bitmap;
import android.net.Uri;

import io.github.m1omg.shrink.core.model.Job;
import io.github.m1omg.shrink.core.model.MediaInfo;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The video the user is working on. Lives in the application object so it
 * survives activity re-creation (rotation, language or theme changes).
 */
public final class AppState {

    /** Notified on the main thread when the selected video changes. */
    public interface Listener {
        void sourceChanged();
    }

    private Uri uri;
    private MediaInfo info;
    private Bitmap thumbnail;
    private boolean loading;
    private String error;
    private Job previewJob;
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();

    public Uri uri() {
        return uri;
    }

    public MediaInfo info() {
        return info;
    }

    public Bitmap thumbnail() {
        return thumbnail;
    }

    public boolean isLoading() {
        return loading;
    }

    public String error() {
        return error;
    }

    public boolean hasSource() {
        return info != null && uri != null;
    }

    void setLoading(Uri uri) {
        this.uri = uri;
        this.info = null;
        this.thumbnail = null;
        this.loading = true;
        this.error = null;
        fire();
    }

    void setSource(Uri uri, MediaInfo info) {
        this.uri = uri;
        this.info = info;
        this.loading = false;
        this.error = null;
        fire();
    }

    void setThumbnail(Bitmap bitmap) {
        this.thumbnail = bitmap;
        fire();
    }

    void setError(String message) {
        this.loading = false;
        this.info = null;
        this.error = message;
        fire();
    }

    /** The job whose 5-second preview should be shown (handed to the preview screen). */
    public Job previewJob() {
        return previewJob;
    }

    public void setPreviewJob(Job job) {
        previewJob = job;
    }

    public void addListener(Listener l) {
        listeners.add(l);
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    private void fire() {
        for (Listener l : listeners) {
            l.sourceChanged();
        }
    }
}
