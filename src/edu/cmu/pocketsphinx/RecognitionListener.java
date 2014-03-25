package edu.cmu.pocketsphinx;

import edu.cmu.pocketsphinx.Hypothesis;

public interface RecognitionListener {

    /**
     * Called at the start of utterance.
     */
    public void onBeginningOfSpeech();

    /**
     * Called at the end of utterance.
     */
    public void onEndOfSpeech();

    /**
     * Called when partial recognition result is available.
     */
    public void onPartialResult(Hypothesis hypothesis);

    /**
     * Called after the recognition is ended.
     */
    public void onResult(Hypothesis hypothesis);
}

/* vim: set ts=4 sw=4: */
