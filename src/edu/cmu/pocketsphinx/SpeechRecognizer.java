/* ====================================================================
 * Copyright (c) 2014 Alpha Cephei Inc.  All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY ALPHA CEPHEI INC. ``AS IS'' AND
 * ANY EXPRESSED OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL CARNEGIE MELLON UNIVERSITY
 * NOR ITS EMPLOYEES BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * ====================================================================
 */

package edu.cmu.pocketsphinx;

import static java.lang.String.format;

import java.io.File;
import java.util.Collection;
import java.util.HashSet;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder.AudioSource;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

/**
 * Main class to access recognizer functions. After configuration this class
 * starts a listener thread which records the data and recognizes it using
 * Pocketsphinx engine. Recognition events are passed to a client using
 * {@link RecognitionListener}
 * 
 */
public class SpeechRecognizer {

    protected static final String TAG = SpeechRecognizer.class.getSimpleName();

    private static final int BUFFER_SIZE = 1024;

    private final Decoder decoder;

    private Thread recognizerThread;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Collection<RecognitionListener> listeners = new HashSet<RecognitionListener>();

    private final int sampleRate;

    protected SpeechRecognizer(Config config) {
        sampleRate = (int) config.getFloat("-samprate");
        if (config.getFloat("-samprate") != sampleRate)
            throw new IllegalArgumentException("sampling rate must be integer");
        decoder = new Decoder(config);
    }

    /**
     * Adds listener.
     */
    public void addListener(RecognitionListener listener) {
        synchronized (listeners) {
            listeners.add(listener);
        }
    }

    /**
     * Removes listener.
     */
    public void removeListener(RecognitionListener listener) {
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }

    /**
     * Starts recognition. Does nothing if recognition is active.
     * 
     * @return true if recognition was actually started
     */
    public boolean startListening(String searchName) {
        if (null != recognizerThread)
            return false;

        Log.i(TAG, format("Start recognition \"%s\"", searchName));
        decoder.setSearch(searchName);
        recognizerThread = new RecognizerThread();
        recognizerThread.start();
        return true;
    }

    private boolean stopRecognizerThread() {
        if (null == recognizerThread)
            return false;

        try {
            recognizerThread.interrupt();
            recognizerThread.join();
        } catch (InterruptedException e) {
            // Restore the interrupted status.
            Thread.currentThread().interrupt();
        }

        recognizerThread = null;
        return true;
    }

    /**
     * Stops recognition. All listeners should receive final result if there is
     * any. Does nothing if recognition is not active.
     * 
     * @return true if recognition was actually stopped
     */
    public boolean stop() {
        boolean result = stopRecognizerThread();
        if (result) {
            Log.i(TAG, "Stop recognition");
            final Hypothesis hypothesis = decoder.hyp();
            mainHandler.post(new ResultEvent(hypothesis, true));
        }
        return result;
    }

    /**
     * Cancels recognition. Listeners do not receive final result. Does nothing
     * if recognition is not active.
     * 
     * @return true if recognition was actually canceled
     */
    public boolean cancel() {
        boolean result = stopRecognizerThread();
        if (result) {
            Log.i(TAG, "Cancel recognition");
        }

        return result;
    }

    /**
     * Gets name of the currently active search.
     * 
     * @return active search name or null if no search was started
     */
    public String getSearchName() {
        return decoder.getSearch();
    }

    public void addFsgSearch(String searchName, FsgModel fsgModel) {
        decoder.setFsg(searchName, fsgModel);
    }

    /**
     * Adds searches based on JSpeech grammar.
     * 
     * @param name
     *            search name
     * @param file
     *            JSGF file
     */
    public void addGrammarSearch(String name, File file) {
        Log.i(TAG, format("Load JSGF %s", file));
        decoder.setJsgfFile(name, file.getPath());
    }

    /**
     * Adds search based on N-gram language model.
     * 
     * @param name
     *            search name
     * @param file
     *            N-gram model file
     */
    public void addNgramSearch(String name, File file) {
        Log.i(TAG, format("Load N-gram model %s", file));
        decoder.setLmFile(name, file.getPath());
    }

    /**
     * Adds search based on a single phrase.
     * 
     * @param name
     *            search name
     * @param phrase
     *            search phrase
     */
    public void addKeyphraseSearch(String name, String phrase) {
        decoder.setKeyphrase(name, phrase);
    }

    /**
     * Adds search based on a keyphrase file.
     * 
     * @param name
     *            search name
     * @param phrase
     *            search phrase
     */
    public void addKeywordSearch(String name, File file) {
        decoder.setKws(name, file.getPath());
    }

    private final class RecognizerThread extends Thread {
        @Override
        public void run() {
            AudioRecord recorder = new AudioRecord(
                    AudioSource.VOICE_RECOGNITION, sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, 8192); // TODO:calculate
                                                           // properly
            decoder.startUtt(null);
            recorder.startRecording();
            short[] buffer = new short[BUFFER_SIZE];
            boolean vadState = decoder.getVadState();

            while (!interrupted()) {
                int nread = recorder.read(buffer, 0, buffer.length);

                if (-1 == nread) {
                    throw new RuntimeException("error reading audio buffer");
                } else if (nread > 0) {
                    decoder.processRaw(buffer, nread, false, false);

                    if (decoder.getVadState() != vadState) {
                        vadState = decoder.getVadState();
                        mainHandler.post(new VadStateChangeEvent(vadState));
                    }

                    final Hypothesis hypothesis = decoder.hyp();
                    if (null != hypothesis)
                        mainHandler.post(new ResultEvent(hypothesis, false));
                }
            }

            recorder.stop();
            int nread = recorder.read(buffer, 0, buffer.length);
            recorder.release();
            decoder.processRaw(buffer, nread, false, false);
            decoder.endUtt();

            // Remove all pending notifications.
            mainHandler.removeCallbacksAndMessages(null);
        }
    }

    private abstract class RecognitionEvent implements Runnable {
        public void run() {
            RecognitionListener[] emptyArray = new RecognitionListener[0];
            for (RecognitionListener listener : listeners.toArray(emptyArray))
                execute(listener);
        }

        protected abstract void execute(RecognitionListener listener);
    }

    private class VadStateChangeEvent extends RecognitionEvent {
        private final boolean state;

        VadStateChangeEvent(boolean state) {
            this.state = state;
        }

        @Override
        protected void execute(RecognitionListener listener) {
            if (state)
                listener.onBeginningOfSpeech();
            else
                listener.onEndOfSpeech();
        }
    }

    private class ResultEvent extends RecognitionEvent {
        protected final Hypothesis hypothesis;
        private final boolean finalResult;

        ResultEvent(Hypothesis hypothesis, boolean finalResult) {
            this.hypothesis = hypothesis;
            this.finalResult = finalResult;
        }

        @Override
        protected void execute(RecognitionListener listener) {
            if (finalResult)
                listener.onResult(hypothesis);
            else
                listener.onPartialResult(hypothesis);
        }
    }
}
