package edu.cmu.pocketsphinx;

import edu.cmu.pocketsphinx.Hypothesis;

public interface RecognitionListener {

    public void onPartialResult(Hypothesis hypothesis);

    public void onResult(Hypothesis hypothesis);
}

/* vim: set ts=4 sw=4: */
