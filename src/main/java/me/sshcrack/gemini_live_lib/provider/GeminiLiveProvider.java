package me.sshcrack.gemini_live_lib.provider;

import me.sshcrack.gemini_live_lib.GeminiLiveLib;
import me.sshcrack.mc_talking.api.provider.BundledAiProvider;
import me.sshcrack.mc_talking.api.provider.Capability;
import me.sshcrack.mc_talking.api.session.BundledSession;
import me.sshcrack.mc_talking.api.voice.Gender;
import me.sshcrack.mc_talking.api.voice.VoiceDescriptor;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class GeminiLiveProvider implements BundledAiProvider {

    @Override
    public String id() {
        return "gemini_live";
    }

    @Override
    public String displayName() {
        return "Gemini Live";
    }

    @Override
    public Set<Capability> capabilities() {
        return Set.of(Capability.BUNDLED);
    }

    @Override
    public List<VoiceDescriptor> availableVoices() {
        return List.of(
            new VoiceDescriptor("Zephyr", "Zephyr", Gender.FEMALE, 1.0f, "en-US", Map.of()),
            new VoiceDescriptor("Kore", "Kore", Gender.FEMALE, 1.0f, "en-US", Map.of()),
            new VoiceDescriptor("Leda", "Leda", Gender.FEMALE, 1.0f, "en-US", Map.of()),
            new VoiceDescriptor("Aoede", "Aoede", Gender.FEMALE, 1.0f, "en-US", Map.of()),
            new VoiceDescriptor("Callirrhoe", "Callirrhoe", Gender.FEMALE, 1.0f, "en-US", Map.of()),
            new VoiceDescriptor("Puck", "Puck", Gender.MALE, 1.0f, "en-US", Map.of()),
            new VoiceDescriptor("Charon", "Charon", Gender.MALE, 1.0f, "en-US", Map.of()),
            new VoiceDescriptor("Orus", "Orus", Gender.MALE, 1.0f, "en-US", Map.of()),
            new VoiceDescriptor("Enceladus", "Enceladus", Gender.MALE, 1.0f, "en-US", Map.of())
        );
    }

    @Override
    public BundledSession createSession(SessionConfig config) {
        String voiceName = config.voice() != null ? config.voice().id() : "Zephyr";
        String model = config.model() != null ? config.model() : "gemini-3.1-flash-live-preview";

        return new GeminiLiveSession.Builder()
            .model(model)
            .systemPrompt(config.systemPrompt() != null ? config.systemPrompt() : "")
            .voiceName(voiceName)
            .enableAudio(config.enableAudio())
            .build();
    }
}
