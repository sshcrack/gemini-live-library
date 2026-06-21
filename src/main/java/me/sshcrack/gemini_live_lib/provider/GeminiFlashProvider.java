package me.sshcrack.gemini_live_lib.provider;

import me.sshcrack.gemini_live_lib.GeminiLiveLib;
import me.sshcrack.gemini_live_lib.misc.GeminiFlash;
import me.sshcrack.gemini_live_lib.misc.UnexpectedResponseException;
import me.sshcrack.mc_talking.api.provider.Capability;
import me.sshcrack.mc_talking.api.provider.LlmProvider;
import me.sshcrack.mc_talking.api.voice.VoiceDescriptor;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class GeminiFlashProvider implements LlmProvider {

    @Override
    public String id() {
        return "gemini_flash";
    }

    @Override
    public String displayName() {
        return "Gemini Flash";
    }

    @Override
    public Set<Capability> capabilities() {
        return Set.of(Capability.LLM);
    }

    @Override
    public List<VoiceDescriptor> availableVoices() {
        return List.of();
    }

    @Override
    public CompletableFuture<String> generate(String prompt, LlmConfig config) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String model = config.model() != null ? config.model() : "gemini-flash-lite-latest";
                String systemPrompt = config.systemPrompt() != null ? config.systemPrompt() : "";
                String apiKey = GeminiLiveLib.resolveApiKey();
                return GeminiFlash.sendSimpleFlashRequest(model, apiKey, systemPrompt, prompt);
            } catch (IOException | InterruptedException | UnexpectedResponseException e) {
                throw new RuntimeException("Flash generation failed", e);
            }
        });
    }
}
