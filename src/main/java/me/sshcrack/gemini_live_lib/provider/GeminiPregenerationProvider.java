package me.sshcrack.gemini_live_lib.provider;

import me.sshcrack.gemini_live_lib.GeminiLiveLib;
import me.sshcrack.gemini_live_lib.misc.GeminiFlash;
import me.sshcrack.gemini_live_lib.misc.GeminiTTS;
import me.sshcrack.gemini_live_lib.misc.UnexpectedResponseException;
import me.sshcrack.mc_talking.api.audio.AudioChunk;
import me.sshcrack.mc_talking.api.audio.AudioFormat;
import me.sshcrack.mc_talking.api.provider.Capability;
import me.sshcrack.mc_talking.api.provider.PregenerationProvider;
import me.sshcrack.mc_talking.api.voice.Gender;
import me.sshcrack.mc_talking.api.voice.VoiceDescriptor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class GeminiPregenerationProvider implements PregenerationProvider {

    @Override
    public String id() {
        return "gemini_pregeneration";
    }

    @Override
    public String displayName() {
        return "Gemini Pregeneration";
    }

    @Override
    public Set<Capability> capabilities() {
        return Set.of(Capability.PREGENERATION);
    }

    @Override
    public List<VoiceDescriptor> availableVoices() {
        return List.of(
            new VoiceDescriptor("Zephyr", "Zephyr", Gender.FEMALE, 1.0f, "en-US", java.util.Map.of()),
            new VoiceDescriptor("Puck", "Puck", Gender.MALE, 1.0f, "en-US", java.util.Map.of())
        );
    }

    @Override
    public CompletableFuture<PregeneratedConversation> generateConversation(
            ConversationScript script, PregenerationConfig config) {
        return CompletableFuture.supplyAsync(() -> {
            String apiKey = GeminiLiveLib.resolveApiKey();
            String model = config.model() != null ? config.model() : "gemini-flash-lite-latest";
            String ttsModel = "gemini-3.1-flash-tts-preview";

            StringBuilder dialogueText = new StringBuilder();
            for (DialogueLine line : script.lines()) {
                dialogueText.append(line.speakerName()).append(": ").append(line.text()).append("\n");
            }

            try {
                String flashResult = GeminiFlash.sendSimpleFlashRequest(
                    model, apiKey,
                    "You are a script writer for a Minecraft colony. Generate a natural conversation between the given citizens.",
                    "Generate a conversation script for the following:\n" + dialogueText
                );

                var ttsPayload = new GeminiTTS.RequestPayload();
                var content = new GeminiTTS.RequestPayload.Content();
                content.role = "user";
                var part = new GeminiTTS.RequestPayload.Part();
                part.text = flashResult;
                content.parts = List.of(part);
                ttsPayload.contents = List.of(content);

                var genConfig = new GeminiTTS.RequestPayload.GenerationConfig();
                genConfig.responseModalities = List.of("AUDIO");
                genConfig.temperature = 1.0;

                var speechConfig = new GeminiTTS.RequestPayload.SpeechConfig();
                var multiSpeakerConfig = new GeminiTTS.RequestPayload.MultiSpeakerVoiceConfig();

                var speaker1Config = new GeminiTTS.RequestPayload.SpeakerVoiceConfig();
                speaker1Config.speaker = "speaker1";
                var vc1 = new GeminiTTS.RequestPayload.VoiceConfig();
                var pb1 = new GeminiTTS.RequestPayload.PrebuiltVoiceConfig();
                pb1.voice_name = "Zephyr";
                vc1.prebuilt_voice_config = pb1;
                speaker1Config.voice_config = vc1;

                var speaker2Config = new GeminiTTS.RequestPayload.SpeakerVoiceConfig();
                speaker2Config.speaker = "speaker2";
                var vc2 = new GeminiTTS.RequestPayload.VoiceConfig();
                var pb2 = new GeminiTTS.RequestPayload.PrebuiltVoiceConfig();
                pb2.voice_name = "Puck";
                vc2.prebuilt_voice_config = pb2;
                speaker2Config.voice_config = vc2;

                multiSpeakerConfig.speaker_voice_configs = List.of(speaker1Config, speaker2Config);
                speechConfig.multi_speaker_voice_config = multiSpeakerConfig;
                genConfig.speech_config = speechConfig;
                ttsPayload.generationConfig = genConfig;

                List<byte[]> chunks = new ArrayList<>();
                GeminiTTS.streamGenerateAudioConversation(ttsModel, apiKey, ttsPayload, chunk -> {
                    chunks.add(chunk.audioBytes());
                });

                int totalSize = chunks.stream().mapToInt(b -> b.length).sum();
                byte[] combined = new byte[totalSize];
                int offset = 0;
                for (byte[] chunk : chunks) {
                    System.arraycopy(chunk, 0, combined, offset, chunk.length);
                    offset += chunk.length;
                }

                return new PregeneratedConversation(new AudioChunk(combined, AudioFormat.DEFAULT_PCM));
            } catch (IOException | InterruptedException | UnexpectedResponseException e) {
                throw new RuntimeException("Pregeneration failed", e);
            }
        });
    }
}
