package edu.cmu.pocketsphinx;

import java.io.File;


public interface AssetsTaskCallback {

    void onTaskStart(int size);

    void onTaskProgress(File file);

    void onTaskComplete(File applicationDir);

    void onTaskCancelled();

    void onTaskError(Throwable error);
}
