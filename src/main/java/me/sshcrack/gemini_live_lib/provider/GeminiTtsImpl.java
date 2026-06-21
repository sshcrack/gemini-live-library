package me.sshcrack.gemini_live_lib.provider;

import me.sshcrack.gemini_live_lib.misc.GeminiTTS;
import me.sshcrack.gemini_live_lib.misc.UnexpectedResponseException;
import me.sshcrack.mc_talking.api.provider.AiProviderRegistry;
import me.sshcrack.mc_talking.api.provider.AudioData;
import me.sshcrack.mc_talking.api.provider.TtsProvider;
import me.sshcrack.mc_talking.api.provider.TtsRequest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GeminiTtsImpl implements TtsProvider {
    private final String modelName;
    private final ExecutorService executor;

    public GeminiTtsImpl() {
        this("gemini-3.1-flash-tts-preview");
    }

    public GeminiTtsImpl(String modelName) {
        this.modelName = modelName;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "gemini-tts");
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    public String providerId() {
        return "gemini";
    }

    @Override
    public String displayName() {
        return "Gemini TTS";
    }

    @Override
    public int priority() {
        return 1;
    }

    @Override
    public CompletableFuture<AudioData> synthesize(TtsRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String apiKey = AiProviderRegistry.getConfig(AiProviderRegistry.CONFIG_GEMINI_API_KEY, "");
                if (apiKey.isEmpty()) {
                    throw new IllegalStateException("Gemini API key not configured");
                }

                var payload = new GeminiTTS.RequestPayload();
                payload.generationConfig = new GeminiTTS.RequestPayload.GenerationConfig();
                payload.generationConfig.responseModalities = List.of("AUDIO");
                payload.generationConfig.temperature = 1.0;

                if (request.voice() != null) {
                    payload.generationConfig.speech_config = new GeminiTTS.RequestPayload.SpeechConfig();
                    payload.generationConfig.speech_config.multi_speaker_voice_config =
                            new GeminiTTS.RequestPayload.MultiSpeakerVoiceConfig();
                    var speakerConfig = new GeminiTTS.RequestPayload.SpeakerVoiceConfig();
                    speakerConfig.speaker = "citizen";
                    speakerConfig.voice_config = new GeminiTTS.RequestPayload.VoiceConfig();
                    speakerConfig.voice_config.prebuilt_voice_config =
                            new GeminiTTS.RequestPayload.PrebuiltVoiceConfig();
                    speakerConfig.voice_config.prebuilt_voice_config.voice_name = request.voice();
                    payload.generationConfig.speech_config.multi_speaker_voice_config
                            .speaker_voice_configs = List.of(speakerConfig);
                }

                var content = new GeminiTTS.RequestPayload.Content();
                content.role = "user";
                var part = new GeminiTTS.RequestPayload.Part();
                part.text = request.text();
                content.parts = List.of(part);
                payload.contents = List.of(content);

                ByteArrayOutputStream audioBuffer = new ByteArrayOutputStream();
                int[] sampleRateHolder = {24000};

                GeminiTTS.streamGenerateAudioConversation(
                        modelName,
                        apiKey,
                        payload,
                        chunk -> {
                            try {
                                audioBuffer.write(chunk.audioBytes());
                                if (chunk.sampleRate() > 0) {
                                    sampleRateHolder[0] = chunk.sampleRate();
                                }
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        }
                );

                byte[] audioBytes = audioBuffer.toByteArray();
                return new AudioData(audioBytes, sampleRateHolder[0], 1);
            } catch (UnexpectedResponseException | IOException | InterruptedException e) {
                throw new RuntimeException("Gemini TTS request failed", e);
            }
        }, executor);
    }
}
