package edu.cmu.pocketsphinx;

import static java.lang.String.format;

import java.io.File;
import java.io.IOException;
import java.util.*;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

/**
 * Task for synchronizing assets in background.
 */
public class AssetsTask extends AsyncTask<Void, File, Object> {

    protected static final String TAG = AssetsTask.class.getSimpleName();

    private Assets assets;
    private final AssetsTaskCallback callback;

    private final Collection<String> newItems = new ArrayList<String>();
    private final Collection<String> unusedItems = new ArrayList<String>();
    private Map<String, String> items;

    /**
     * Constructs new task.
     *
     * @param callback callback
     */
    public AssetsTask(Context context, AssetsTaskCallback callback) {
        try {
            assets = new Assets(context);
            items = assets.getItems();
            Map<String, String> externalItems = assets.getExternalItems();
            for (String path : items.keySet()) {
                if (!items.get(path).equals(externalItems.get(path)) ||
                    !(new File(assets.getApplicationDir(), path).exists()))
                    newItems.add(path);
                else
                    Log.i(TAG, format("skip asset %s: equal checksums", path));

            }

            unusedItems.addAll(externalItems.keySet());
            unusedItems.removeAll(items.keySet());
        } catch (IOException e) {
            callback.onTaskError(e);
        }

        this.callback = callback;
    }

    @Override
    protected void onPreExecute() {
        callback.onTaskStart(newItems.size());
    }

    @Override
    protected Object doInBackground(Void... nothing) {
        try {
            for (String path : newItems) {
                File file = assets.copy(path);
                Log.i(TAG, format("copy asset %s to %s", path, file));
                publishProgress(file);
                if (isCancelled())
                    return null;
            }

            for (String path : unusedItems) {
                File file = new File(assets.getApplicationDir(), path);
                file.delete();
                Log.i(TAG, format("remove asset %s", file));
                if (isCancelled())
                    return null;
            }

            assets.writeItemList(items);
            return assets.getApplicationDir();
        } catch (IOException e) {
            // Have AsyncTask to execute onCancel on behalf of the UI thread.
            cancel(false);
            return e;
        }
    }

    @Override
    protected void onProgressUpdate(File... files) {
        for (File file : files)
            callback.onTaskProgress(file);
    }

    @Override
    protected void onPostExecute(Object applicationDirectory) {
        callback.onTaskComplete((File) applicationDirectory);
    }

    @Override
    protected void onCancelled(Object exception) {
        if (null == exception)
            callback.onTaskCancelled();
        else
            callback.onTaskError((Throwable) exception);
    }
}

/* vim: set ts=4 sw=4: */
