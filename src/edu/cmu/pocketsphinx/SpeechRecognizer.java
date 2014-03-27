package edu.cmu.pocketsphinx;

import static java.lang.String.format;

import java.io.File;

import java.util.Collection;
import java.util.HashSet;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder.AudioSource;

import android.os.*;

import android.util.Log;

import edu.cmu.pocketsphinx.Config;
import edu.cmu.pocketsphinx.Decoder;
import edu.cmu.pocketsphinx.Hypothesis;


public class SpeechRecognizer {

    protected static final String TAG = SpeechRecognizer.class.getSimpleName();

    private static final int MSG_START = 1;
    private static final int MSG_STOP = 2;
    private static final int MSG_CANCEL = 3;

    private static final int BUFFER_SIZE = 1024;

    private final Config config;
    private final Decoder decoder;

    private Thread recognizerThread;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Collection<RecognitionListener> listeners =
        new HashSet<RecognitionListener>();

    private final int sampleRate;

    protected SpeechRecognizer(Config config) {
        sampleRate = (int) config.getFloat("-samprate");
        if (config.getFloat("-samprate") != sampleRate)
            throw new IllegalArgumentException("sampling rate must be integer");

        this.config = config;
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
     * Starts recognition.
     *
     * @return true if recognition was actually started, false otherwise
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

    /**
     * Stops recognition. All listeners should receive final result if there is
     * any.
     */
    public boolean stop() {
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
     * Cancels recogition. Listeners do not recevie final result.
     */
    public void cancel() {
        // TODO: implement
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
     * @param name search name
     * @param file JSGF file
     */
    public void addGrammarSearch(String name, File file) {
        Log.i(TAG, format("Load JSGF %s", file));
        Jsgf jsgf = new Jsgf(file.getPath());

        for (JsgfRule rule : jsgf) {
            if (!rule.isPublic())
                continue;

            int lw = config.getInt("-lw");
            Log.i(TAG, format("Use rule %s to build FSG", rule.getName()));
            addFsgSearch(name, jsgf.buildFsg(rule, decoder.getLogmath(), lw));
            return;
        }

        throw new IllegalArgumentException("grammar has no public rules");
    }

    /**
     * Adds search based on N-gram language model.
     *
     * @param name search name
     * @param file N-gram model file
     */
    public void addNgramSearch(String name, File file) {
        String path = file.getPath();
        Log.i(TAG, format("Load N-gram model %s", path));
        NGramModel lm = new NGramModel(config, decoder.getLogmath(), path);
        decoder.setLm(name, lm);
    }

    /**
     * Adds search based on a single phrase.
     *
     * @param name search name
     * @param phrase search phrase
     */
    public void addKeywordSearch(String name, String phrase) {
        decoder.setKws(name, phrase);
    }

    private final class RecognizerThread extends Thread {
        @Override public void run() {
            AudioRecord recorder =
                new AudioRecord(AudioSource.VOICE_RECOGNITION,
                                sampleRate,
                                AudioFormat.CHANNEL_IN_MONO,
                                AudioFormat.ENCODING_PCM_16BIT,
                                8192); // TODO:calculate properly
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
            recorder = null;

            decoder.processRaw(buffer, nread, false, false);
            decoder.endUtt();
            mainHandler.removeCallbacksAndMessages(null);

            final Hypothesis hypothesis = decoder.hyp();
            if (null != hypothesis)
                mainHandler.post(new ResultEvent(hypothesis, true));
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

        @Override protected void execute(RecognitionListener listener) {
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

        @Override protected void execute(RecognitionListener listener) {
            if (finalResult)
                listener.onResult(hypothesis);
            else
                listener.onPartialResult(hypothesis);
        }
    }
}

/* vim: set ts=4 sw=4: */
