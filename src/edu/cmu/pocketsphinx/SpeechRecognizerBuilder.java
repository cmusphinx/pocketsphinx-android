package edu.cmu.pocketsphinx;

import static edu.cmu.pocketsphinx.Decoder.defaultConfig;
import static edu.cmu.pocketsphinx.Decoder.fileConfig;

import java.io.File;


public class SpeechRecognizerBuilder {

    static {
        System.loadLibrary("pocketsphinx_jni");
    }

    private final Config config;

    /**
     * Creates new speech recognizer builder with default configuration.
     */
    public static SpeechRecognizerBuilder getBuilder() {
        return new SpeechRecognizerBuilder(defaultConfig());
    }

    /**
     * Creates new speech recognizer builder from configuration file.
     * Configuration file should consist of lines containing key-value pairs.
     *
     * @param configFile configuration file
     */
    public static SpeechRecognizerBuilder getBuilder(File configFile) {
        return new SpeechRecognizerBuilder(fileConfig(configFile.getPath()));
    }

    private SpeechRecognizerBuilder(Config config) {
        this.config = config;
    }

    public SpeechRecognizer buildRecognizer() {
        return new SpeechRecognizer(config);
    }

    public SpeechRecognizerBuilder setAcousticModel(File model) {
        return setString("-hmm", model.getPath());
    }

    public SpeechRecognizerBuilder setDictionary(File dictionary) {
        return setString("-dict", dictionary.getPath());
    }

    public SpeechRecognizerBuilder setSampleRate(int rate) {
        return setFloat("-samprate", rate);
    }

    public SpeechRecognizerBuilder setRawLogDir(File dir) {
        return setString("-rawlogdir", dir.getPath());
    }

    public SpeechRecognizerBuilder setKeywordThreshold(float threshold) {
        return setFloat("-kws_threshold", threshold);
    }

    public SpeechRecognizerBuilder setBoolean(String key, boolean value) {
        config.setBoolean(key, value);
        return this;
    }

    public SpeechRecognizerBuilder setInteger(String key, int value) {
        config.setInt(key, value);
        return this;
    }

    public SpeechRecognizerBuilder setFloat(String key, float value) {
        config.setFloat(key, value);
        return this;
    }

    public SpeechRecognizerBuilder setString(String key, String value) {
        config.setString(key, value);
        return this;
    }
}
