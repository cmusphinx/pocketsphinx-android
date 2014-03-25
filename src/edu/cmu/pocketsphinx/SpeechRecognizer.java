package edu.cmu.pocketsphinx;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

import android.os.Handler;
import android.os.Handler.Callback;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;

import android.util.Log;

import edu.cmu.pocketsphinx.Config;
import edu.cmu.pocketsphinx.Decoder;
import edu.cmu.pocketsphinx.Hypothesis;

public class SpeechRecognizer {

    private static final int MSG_START = 1;
    private static final int MSG_NEXT = 2;
    private static final int MSG_STOP = 3;
    private static final int MSG_SET_SEARCH = 4;

    private final AudioRecord recorder;
    private final Decoder decoder;

    private final Handler handler;
    private final HandlerThread handlerThread;

    private final Handler mainLoopHandler = new Handler(Looper.getMainLooper());
    private Collection<RecognitionListener> listeners =
        new ArrayList<RecognitionListener>();

    private final short[] buffer = new short[1024];
    private boolean vadState = false;

    public SpeechRecognizer(Config config) {
        decoder = new Decoder(config);
        recorder = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,
                                   (int) config.getFloat("-samprate"),
                                   AudioFormat.CHANNEL_IN_MONO,
                                   AudioFormat.ENCODING_PCM_16BIT,
                                   8192);

        handlerThread = new HandlerThread(getClass().getSimpleName());
        handlerThread.start();

        handler = new Handler(handlerThread.getLooper(), new Callback() {

            @Override
            public boolean handleMessage(Message msg) {
                return SpeechRecognizer.this.handleMessage(msg);
            }
        });
    }

    public void addListener(RecognitionListener listener) {
        synchronized (listeners) {
            listeners.add(listener);
        }
    }

    public void removeListener(RecognitionListener listener) {
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }

    public void startListening() {
        sendMessage(MSG_START);
    }

    public void stopListening() {
        sendMessage(MSG_STOP);
    }
    
    public void setSearch(String searchName) {
        handler.sendMessage(handler.obtainMessage(MSG_SET_SEARCH, (Object)searchName));
    }

    public boolean isActive() {
        int state = recorder.getRecordingState();
        return AudioRecord.RECORDSTATE_RECORDING == state;
    }

    private void sendMessage(int what) {
        handler.sendMessage(handler.obtainMessage(what));
    }

    private boolean handleMessage(Message msg) {
        switch (msg.what) {
            default:
                return false;
            case MSG_SET_SEARCH:
                decoder.setSearch((String)msg.obj);
                break;
            case MSG_STOP:
                if (isActive()) {
                    endUtterance();
                }
                break;
            case MSG_START:
                if (!isActive()) {
                    startUtterance();
                }
            case MSG_NEXT:
                if (isActive())
                    continueUtterance();
        }

        return true;
    }

    private void startUtterance() {
        decoder.startUtt(null);
        handler.removeMessages(MSG_STOP);
        handler.removeMessages(MSG_START);
        vadState = false;
        recorder.startRecording();
    }

    private void continueUtterance() {
        int nread = recorder.read(buffer, 0, buffer.length);

        if (-1 == nread) {
            sendMessage(MSG_STOP);
            return;
        } else if (nread > 0) {
            decoder.processRaw(buffer, nread, false, false);
            boolean curVadState = decoder.getVadState();
            if (curVadState != vadState) {
                if (vadState = curVadState)
                    mainLoopHandler.post(new SpeechStartCallback());
                else
                    mainLoopHandler.post(new SpeechEndCallback());
            }
            final Hypothesis hypothesis = decoder.hyp();
            if (null != hypothesis)
                mainLoopHandler.post(new PartialResultCallback(hypothesis));
        }

        sendMessage(MSG_NEXT);
    }

    private void endUtterance() {
        recorder.stop();
        int nread = recorder.read(buffer, 0, buffer.length);
        Log.d(getClass().getSimpleName(), "recorder.read returned " + nread);
        if (nread > 0)
            decoder.processRaw(buffer, nread, false, false);

        decoder.endUtt();
        handler.removeMessages(MSG_NEXT);
        final Hypothesis hypothesis = decoder.hyp();
        if (null != hypothesis)
            mainLoopHandler.post(new ResultCallback(hypothesis));
    }
    
    public String getSearchName() {
        return decoder.getSearch();
    }
    
    public void setFsg(String name, FsgModel fsg) {
        decoder.setFsg(name, fsg);
    }
    
    public void setLm(String name, NGramModel lm) {
        decoder.setLm(name, lm);
    }
    
    public void setKws(String name, String keyphrase) {
        decoder.setKws(name, keyphrase);
    }
        
    public SWIGTYPE_p_LogMath getLogmath() {
        return decoder.getLogmath();
    }

    private abstract class RecognitionCallback implements Runnable {
        public void run() {
            RecognitionListener[] emptyArray = new RecognitionListener[0];
            for (RecognitionListener listener : listeners.toArray(emptyArray))
                execute(listener);
        }

        protected abstract void execute(RecognitionListener listener);
    }

    private class SpeechStartCallback extends RecognitionCallback {
        @Override
        protected void execute(RecognitionListener listener) {
            listener.onBeginningOfSpeech();
        }
    }

    private class SpeechEndCallback extends RecognitionCallback {
        @Override
        protected void execute(RecognitionListener listener) {
            listener.onEndOfSpeech();
        }
    }

    private class ResultCallback extends RecognitionCallback {

        protected final Hypothesis hypothesis;

        public ResultCallback(Hypothesis hypothesis) {
            this.hypothesis = hypothesis;
        }

        @Override
        public void execute(RecognitionListener listener) {
            listener.onResult(hypothesis);
        }
    }

    private class PartialResultCallback extends ResultCallback {

        public PartialResultCallback(Hypothesis hypothesis) {
            super(hypothesis);
        }

        @Override
        public void execute(RecognitionListener listener) {
            listener.onPartialResult(hypothesis);
        }
    }
}

/* vim: set ts=4 sw=4: */
