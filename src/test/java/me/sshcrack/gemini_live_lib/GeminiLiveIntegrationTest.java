package me.sshcrack.gemini_live_lib;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.sshcrack.gemini_live_lib.gson.BidiGenerateContentSetup;
import me.sshcrack.gemini_live_lib.gson.ClientMessages;
import me.sshcrack.gemini_live_lib.gson.RealtimeInput;
import me.sshcrack.gemini_live_lib.websocket.handshake.ServerHandshake;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Opt-in: one real session, one short response, no retries or parallel connections. */
class GeminiLiveIntegrationTest {
    @Test
    void realProviderCompletesSetupAndOneAudioTurn() throws Exception {
        String configPath = System.getenv("GEMINI_LIVE_TEST_CONFIG");
        String keyPath = System.getenv("GEMINI_LIVE_TEST_KEY_FILE");
        String key = System.getenv("GEMINI_API_KEY");
        assumeTrue(configPath != null || keyPath != null || (key != null && !key.isBlank()),
                "Set GEMINI_LIVE_TEST_CONFIG or GEMINI_API_KEY to explicitly enable live testing");
        String model = System.getenv("GEMINI_LIVE_TEST_MODEL");
        if (keyPath != null) key = Files.readString(Path.of(keyPath)).strip();
        if (configPath != null) {
            JsonObject config;
            try (var reader = Files.newBufferedReader(Path.of(configPath))) {
                config = JsonParser.parseReader(reader).getAsJsonObject();
            }
            key = config.get("geminiApiKey").getAsString();
            if (model == null && config.has("currentAiModel")) {
                model = switch (config.get("currentAiModel").getAsString()) {
                    case "Flash3" -> "gemini-3.1-flash-live-preview";
                    case "Flash2_5" -> "gemini-2.5-flash-native-audio-preview-12-2025";
                    default -> throw new IllegalArgumentException("Set GEMINI_LIVE_TEST_MODEL for this configured model");
                };
            }
        }
        assertTrue(key != null && !key.isBlank(), "Configured key is empty");
        String selectedModel = model == null ? "gemini-3.1-flash-live-preview" : model;
        CountDownLatch setup = new CountDownLatch(1);
        CountDownLatch turn = new CountDownLatch(1);
        AtomicInteger audioBytes = new AtomicInteger();
        AtomicReference<String> phase = new AtomicReference<>("CONNECTING");
        AtomicReference<String> failure = new AtomicReference<>("");
        GeminiLiveClient client = new GeminiLiveClient(key) {
            @Override public BidiGenerateContentSetup getSetup() {
                var result = new BidiGenerateContentSetup("models/" + selectedModel);
                result.generationConfig.responseModalities = List.of("AUDIO");
                result.generationConfig.maxOutputTokens = "64";
                result.systemInstruction = new BidiGenerateContentSetup.SystemInstruction();
                result.systemInstruction.parts.add(new BidiGenerateContentSetup.SystemInstruction.Part(
                        "For this connection test, say only the word hello."));
                return result;
            }
            @Override public void onOpen(ServerHandshake handshake) {
                phase.set("SETTING_UP");
                super.onOpen(handshake);
            }
            @Override public void onSetupComplete() { phase.set("ACTIVE"); setup.countDown(); }
            @Override public void onGeneratedAudio(byte[] data, int sampleRate) { audioBytes.addAndGet(data.length); }
            @Override public void onTurnComplete() { turn.countDown(); }
            @Override public void addPromptAudio(short[] audio) { }
            @Override public void onError(Exception error) {
                // Exception messages can contain the authenticated URI. Never include them in reports.
                failure.set(error.getClass().getSimpleName());
                setup.countDown();
                turn.countDown();
            }
            @Override public void onClose(int code, String reason, boolean remote) {
                failure.compareAndSet("", "provider closed with code " + code);
                setup.countDown();
                turn.countDown();
                super.onClose(code, reason, remote);
            }
        };
        client.setDaemon(true);
        try {
            client.connect();
            assertTrue(setup.await(20, TimeUnit.SECONDS) && client.isSetupComplete(),
                    () -> "Live setup failed: phase=" + phase.get() + ", error=" + failure.get());
            var input = new RealtimeInput();
            input.text = "Say hello.";
            client.send(ClientMessages.input(input));
            assertTrue(turn.await(30, TimeUnit.SECONDS) && failure.get().isEmpty(),
                    () -> "Live turn did not complete: " + failure.get());
            assertTrue(audioBytes.get() > 0, "Provider completed without audio");
            System.out.println("GEMINI_LIVE_TEST_SUCCESS:setup,audio,turnComplete; sessions=1; retries=0");
        } finally {
            // Force teardown even when the production client's handshake has no timeout.
            if (client.getSocket() != null) client.getSocket().close();
            client.closeConnection(1000, "integration test finished");
            client.close();
        }
    }
}
