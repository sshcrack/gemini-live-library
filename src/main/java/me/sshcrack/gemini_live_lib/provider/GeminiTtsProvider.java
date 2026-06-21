package me.sshcrack.gemini_live_lib.provider;

import me.sshcrack.gemini_live_lib.GeminiLiveLib;
import me.sshcrack.gemini_live_lib.misc.GeminiTTS;
import me.sshcrack.gemini_live_lib.misc.UnexpectedResponseException;
import me.sshcrack.mc_talking.api.audio.AudioChunk;
import me.sshcrack.mc_talking.api.audio.AudioFormat;
import me.sshcrack.mc_talking.api.provider.Capability;
import me.sshcrack.mc_talking.api.provider.TtsProvider;
import me.sshcrack.mc_talking.api.voice.Gender;
import me.sshcrack.mc_talking.api.voice.VoiceDescriptor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class GeminiTtsProvider implements TtsProvider {

    @Override
    public String id() {
        return "gemini_tts";
    }

    @Override
    public String displayName() {
        return "Gemini TTS";
    }

    @Override
    public Set<Capability> capabilities() {
        return Set.of(Capability.TTS);
    }

    @Override
    public List<VoiceDescriptor> availableVoices() {
        return List.of(
            new VoiceDescriptor("Zephyr", "Zephyr", Gender.FEMALE, 1.0f, "en-US", java.util.Map.of()),
            new VoiceDescriptor("Kore", "Kore", Gender.FEMALE, 1.0f, "en-US", java.util.Map.of()),
            new VoiceDescriptor("Leda", "Leda", Gender.FEMALE, 1.0f, "en-US", java.util.Map.of()),
            new VoiceDescriptor("Puck", "Puck", Gender.MALE, 1.0f, "en-US", java.util.Map.of()),
            new VoiceDescriptor("Charon", "Charon", Gender.MALE, 1.0f, "en-US", java.util.Map.of()),
            new VoiceDescriptor("Orus", "Orus", Gender.MALE, 1.0f, "en-US", java.util.Map.of())
        );
    }

    @Override
    public CompletableFuture<AudioChunk> synthesize(String text, TtsConfig config) {
        return CompletableFuture.supplyAsync(() -> {
            String apiKey = GeminiLiveLib.resolveApiKey();
            String model = config.model() != null ? config.model() : "gemini-3.1-flash-tts-preview";
            String voiceId = config.voiceId() != null ? config.voiceId() : "Zephyr";
            String language = config.language() != null ? config.language() : "en-US";

            var payload = new GeminiTTS.RequestPayload();

            var content = new GeminiTTS.RequestPayload.Content();
            content.role = "user";
            var part = new GeminiTTS.RequestPayload.Part();
            part.text = text;
            content.parts = List.of(part);
            payload.contents = List.of(content);

            var genConfig = new GeminiTTS.RequestPayload.GenerationConfig();
            genConfig.responseModalities = List.of("AUDIO");
            genConfig.temperature = 1.0;

            var speechConfig = new GeminiTTS.RequestPayload.SpeechConfig();
            var multiSpeakerConfig = new GeminiTTS.RequestPayload.MultiSpeakerVoiceConfig();
            var speakerConfig = new GeminiTTS.RequestPayload.SpeakerVoiceConfig();
            speakerConfig.speaker = voiceId;
            var voiceConfig = new GeminiTTS.RequestPayload.VoiceConfig();
            var prebuilt = new GeminiTTS.RequestPayload.PrebuiltVoiceConfig();
            prebuilt.voice_name = voiceId;
            voiceConfig.prebuilt_voice_config = prebuilt;
            speakerConfig.voice_config = voiceConfig;
            multiSpeakerConfig.speaker_voice_configs = List.of(speakerConfig);
            speechConfig.multi_speaker_voice_config = multiSpeakerConfig;
            genConfig.speech_config = speechConfig;

            payload.generationConfig = genConfig;

            List<byte[]> chunks = new ArrayList<>();
            try {
                GeminiTTS.streamGenerateAudioConversation(model, apiKey, payload, chunk -> {
                    chunks.add(chunk.audioBytes());
                });
            } catch (IOException | InterruptedException | UnexpectedResponseException e) {
                throw new RuntimeException("TTS generation failed", e);
            }

            int totalSize = chunks.stream().mapToInt(b -> b.length).sum();
            byte[] combined = new byte[totalSize];
            int offset = 0;
            for (byte[] chunk : chunks) {
                System.arraycopy(chunk, 0, combined, offset, chunk.length);
                offset += chunk.length;
            }

            return new AudioChunk(combined, AudioFormat.DEFAULT_PCM);
        });
    }
}
