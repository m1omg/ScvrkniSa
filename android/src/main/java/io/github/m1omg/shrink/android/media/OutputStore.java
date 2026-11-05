package io.github.m1omg.shrink.android.media;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** Puts finished files where the user can find them: Movies/Shrink or Music/Shrink. */
public final class OutputStore {

    public static final String FOLDER = "Shrink";

    private OutputStore() {
    }

    /** "Movies/Shrink" or "Music/Shrink". */
    public static String folderLabel(boolean audio) {
        return (audio ? Environment.DIRECTORY_MUSIC : Environment.DIRECTORY_MOVIES) + "/" + FOLDER;
    }

    /** Copies {@code file} into the shared media collection and returns its content URI. */
    public static Uri saveToMediaStore(Context context, File file, String displayName, String mimeType,
                                       boolean audio) throws IOException {
        ContentResolver resolver = context.getContentResolver();
        Uri collection = audio
                ? MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                : MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, displayName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, mimeType);
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, folderLabel(audio));
        values.put(MediaStore.MediaColumns.IS_PENDING, 1);
        Uri uri = resolver.insert(collection, values);
        if (uri == null) {
            throw new IOException("Could not create the file in " + folderLabel(audio));
        }
        try {
            copy(file, resolver, uri);
            values.clear();
            values.put(MediaStore.MediaColumns.IS_PENDING, 0);
            resolver.update(uri, values, null, null);
            return uri;
        } catch (IOException | RuntimeException e) {
            resolver.delete(uri, null, null);
            throw e instanceof IOException ? (IOException) e : new IOException(e);
        }
    }

    /** Copies {@code file} to a location the user picked (Storage Access Framework). */
    public static void copyTo(Context context, File file, Uri target) throws IOException {
        copy(file, context.getContentResolver(), target);
    }

    private static void copy(File file, ContentResolver resolver, Uri target) throws IOException {
        try (InputStream in = new FileInputStream(file); OutputStream out = resolver.openOutputStream(target, "w")) {
            if (out == null) {
                throw new IOException("Cannot write to " + target);
            }
            byte[] buffer = new byte[1 << 16];
            int n;
            while ((n = in.read(buffer)) > 0) {
                out.write(buffer, 0, n);
            }
        }
    }
}
