package me.sshcrack.gemini_live_lib.provider;

import me.sshcrack.gemini_live_lib.misc.GeminiFlash;
import me.sshcrack.gemini_live_lib.misc.UnexpectedResponseException;
import me.sshcrack.mc_talking.api.provider.AiProviderRegistry;
import me.sshcrack.mc_talking.api.provider.LlmProvider;
import me.sshcrack.mc_talking.api.provider.LlmRequest;
import me.sshcrack.mc_talking.api.provider.LlmResponse;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GeminiFlashLlm implements LlmProvider {
    private final String modelName;
    private final ExecutorService executor;

    public GeminiFlashLlm() {
        this("gemini-2.5-flash-preview");
    }

    public GeminiFlashLlm(String modelName) {
        this.modelName = modelName;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "gemini-flash-llm");
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
        return "Gemini Flash";
    }

    @Override
    public int priority() {
        return 1;
    }

    @Override
    public CompletableFuture<LlmResponse> generate(LlmRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String apiKey = AiProviderRegistry.getConfig(AiProviderRegistry.CONFIG_GEMINI_API_KEY, "");
                if (apiKey.isEmpty()) {
                    throw new IllegalStateException("Gemini API key not configured");
                }

                StringBuilder historyBuilder = new StringBuilder();
                for (LlmRequest.LlmMessage msg : request.history()) {
                    historyBuilder.append(msg.role().name()).append(": ").append(msg.text()).append("\n");
                }

                String combinedPrompt = historyBuilder + request.userText();

                String result = GeminiFlash.sendSimpleFlashRequest(
                        modelName,
                        apiKey,
                        request.systemPrompt(),
                        combinedPrompt
                );

                return LlmResponse.ofText(result);
            } catch (UnexpectedResponseException | java.io.IOException | InterruptedException e) {
                throw new RuntimeException("Gemini Flash request failed", e);
            }
        }, executor);
    }
}
