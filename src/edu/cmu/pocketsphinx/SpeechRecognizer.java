package edu.cmu.pocketsphinx;

import static java.lang.String.format;

import java.io.File;

import java.util.Collection;
import java.util.HashSet;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder.AudioSource;

import android.os.*;
import android.os.Handler.Callback;

import android.util.Log;

import edu.cmu.pocketsphinx.Config;
import edu.cmu.pocketsphinx.Decoder;
import edu.cmu.pocketsphinx.Hypothesis;


public class SpeechRecognizer {

    protected static final String TAG = SpeechRecognizer.class.getSimpleName();

    private static final int MSG_START = 1;
    private static final int MSG_ADVANCE = 2;
    private static final int MSG_STOP = 3;
    private static final int MSG_CANCEL = 4;

    private final AudioRecord recorder;
    private final Config config;
    private final Decoder decoder;

    private final Handler handler;

    private final Handler mainLoopHandler =
        new Handler(Looper.getMainLooper());
    private Collection<RecognitionListener> listeners =
        new HashSet<RecognitionListener>();

    private final short[] buffer = new short[1024];
    private boolean vadState;

    /**
     * Creates new speech recognizer with default configuration.
     */
    public SpeechRecognizer() {
        this(Decoder.defaultConfig());
    }

    /**
     * Creates new speech recognizer using configuration file.
     *
     * @param configFile configuration file
     */
    public SpeechRecognizer(File configFile) {
        this(Decoder.fileConfig(configFile.getPath()));
    }

    protected SpeechRecognizer(Config config) {
        int sampleRate = (int) config.getFloat("-samprate");
        if (config.getFloat("-samprate") != sampleRate)
            throw new IllegalArgumentException("sampling rate must be integer");

        this.config = config;
        decoder = new Decoder(config);
        recorder = new AudioRecord(AudioSource.VOICE_RECOGNITION,
                                   sampleRate,
                                   AudioFormat.CHANNEL_IN_MONO,
                                   AudioFormat.ENCODING_PCM_16BIT,
                                   8192); // TODO: calculate properly

        HandlerThread thread = new HandlerThread(getClass().getSimpleName());
        thread.start();

        handler = new Handler(thread.getLooper(), new Callback() {
            @Override
            public boolean handleMessage(Message msg) {
                return SpeechRecognizer.this.handleMessage(msg);
            }
        });
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
     */
    public void startListening(String searchName) {
        sendMessage(MSG_START, searchName);
    }

    /**
     * Stops recognition. All listeners should receive final result if there is
     * any.
     */
    public void stop() {
        sendMessage(MSG_STOP);
    }

    /**
     * Cancels recogition. Listeners do not recevie final result.
     */
    public void cancel() {
        sendMessage(MSG_CANCEL);
    }

    /**
     * Sets active search.
     */
    public boolean isActive() {
        int state = recorder.getRecordingState();
        return AudioRecord.RECORDSTATE_RECORDING == state;
    }

    private void sendMessage(int what) {
        handler.sendMessage(handler.obtainMessage(what));
    }

    private void sendMessage(int what, Object object) {
        handler.sendMessage(handler.obtainMessage(what, object));
    }

    private boolean handleMessage(Message msg) {
        switch (msg.what) {
            default:
                return false;
            case MSG_STOP:
            case MSG_CANCEL:
                if (isActive())
                    endUtterance(MSG_CANCEL == msg.what);
                break;
            case MSG_START:
                if (!isActive())
                    startUtterance((String) msg.obj);
            case MSG_ADVANCE:
                if (isActive())
                    continueUtterance();
        }

        return true;
    }

    private void startUtterance(String searchName) {
        Log.i(TAG, format("Recognition started, search is \"%s\"", searchName));
        vadState = false;
        decoder.setSearch(searchName);
        decoder.startUtt(null);
        recorder.startRecording();
    }

    private void continueUtterance() {
        int nread = recorder.read(buffer, 0, buffer.length);

        if (-1 == nread) {
            throw new RuntimeException("error reading audio buffer");
        } else if (nread > 0) {
            decoder.processRaw(buffer, nread, false, false);

            if (decoder.getVadState() != vadState) {
                vadState = decoder.getVadState();
                mainLoopHandler.post(new VadStateChangeEvent(vadState));
            }

            final Hypothesis hypothesis = decoder.hyp();
            if (null != hypothesis)
                mainLoopHandler.post(new ResultEvent(hypothesis, true));
        }

        sendMessage(MSG_ADVANCE);
    }

    private void endUtterance(boolean canceled) {
        recorder.stop();
        int nread = recorder.read(buffer, 0, buffer.length);
        decoder.processRaw(buffer, nread, false, false);
        decoder.endUtt();

        if (!canceled) {
            Log.i(TAG, "Recognition ended");
            final Hypothesis hypothesis = decoder.hyp();
            if (null != hypothesis)
                mainLoopHandler.post(new ResultEvent(hypothesis, true));
        } else {
            Log.i(TAG, "Recognition canceled");
        }
    }

    /**
     * Gets name of the active search.
     */
    public String getSearchName() {
        return decoder.getSearch();
    }

    public void addFsgSearch(String searchName, FsgModel fsgModel) {
        decoder.setFsg(searchName, fsgModel);
    }

    /**
     * Adds searchs based on JSpeech grammar.
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
        private final boolean partial;

        ResultEvent(Hypothesis hypothesis, boolean partial) {
            this.hypothesis = hypothesis;
            this.partial = partial;
        }

        @Override protected void execute(RecognitionListener listener) {
            if (partial)
                listener.onPartialResult(hypothesis);
            else
                listener.onResult(hypothesis);
        }
    }
}

/* vim: set ts=4 sw=4: */
