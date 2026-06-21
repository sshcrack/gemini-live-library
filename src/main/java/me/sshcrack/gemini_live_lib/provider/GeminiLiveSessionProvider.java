package me.sshcrack.gemini_live_lib.provider;

import me.sshcrack.mc_talking.api.provider.AiProviderRegistry;
import me.sshcrack.mc_talking.api.provider.LiveSession;
import me.sshcrack.mc_talking.api.provider.LiveSessionConfig;
import me.sshcrack.mc_talking.api.provider.LiveSessionProvider;

public class GeminiLiveSessionProvider implements LiveSessionProvider {
    @Override
    public String providerId() {
        return "gemini_live";
    }

    @Override
    public String displayName() {
        return "Gemini Live";
    }

    @Override
    public LiveSession createSession(LiveSessionConfig config) {
        String apiKey = AiProviderRegistry.getConfig(AiProviderRegistry.CONFIG_GEMINI_API_KEY, "");
        return new GeminiLiveSession(apiKey, config);
    }

    @Override
    public int priority() {
        return 0;
    }
}
