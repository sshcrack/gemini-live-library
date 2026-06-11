package me.sshcrack.gemini_live_lib;

import me.sshcrack.gemini_live_lib.gson.BidiGenerateContentSetup;
import org.jetbrains.annotations.Nullable;

public abstract class GeminiLiveTranslateClient extends GeminiLiveClient {
    private final String targetLanguageCode;
    @Nullable
    private final String sourceLanguageCode;
    private final boolean echoTargetLanguage;

    public GeminiLiveTranslateClient(String apiKey, String targetLanguageCode) {
        this(apiKey, targetLanguageCode, true, null);
    }

    public GeminiLiveTranslateClient(String apiKey, String targetLanguageCode, boolean echoTargetLanguage) {
        this(apiKey, targetLanguageCode, echoTargetLanguage, null);
    }

    public GeminiLiveTranslateClient(String apiKey, String targetLanguageCode, boolean echoTargetLanguage, @Nullable String sourceLanguageCode) {
        super(apiKey);
        this.targetLanguageCode = targetLanguageCode;
        this.echoTargetLanguage = echoTargetLanguage;
        this.sourceLanguageCode = sourceLanguageCode;
    }

    @Override
    public BidiGenerateContentSetup getSetup() {
        var config = new BidiGenerateContentSetup.GenerationConfig.TranslationConfig(targetLanguageCode);
        config.echoTargetLanguage = echoTargetLanguage;
        config.sourceLanguageCode = sourceLanguageCode;

        var genConfig = new BidiGenerateContentSetup.GenerationConfig();
        genConfig.responseModalities = new java.util.ArrayList<>();
        genConfig.responseModalities.add("AUDIO");
        genConfig.translationConfig = config;

        var setup = new BidiGenerateContentSetup("models/gemini-3.5-live-translate-preview");
        setup.generationConfig = genConfig;

        return setup;
    }

    @Override
    public void onGeneratedText(String text) {
        onTranslatedText(text);
    }

    @Override
    public void onGeneratedAudio(byte[] audio, int sampleRate) {
        onTranslatedAudio(audio, sampleRate);
    }

    public void onTranslatedText(String text) {
    }

    public void onTranslatedAudio(byte[] audio, int sampleRate) {
    }
}
