package me.sshcrack.gemini_live_lib.provider;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.sshcrack.gemini_live_lib.GeminiLiveLib;
import me.sshcrack.gemini_live_lib.gson.BidiGenerateContentSetup;
import me.sshcrack.gemini_live_lib.gson.ClientMessages;
import me.sshcrack.gemini_live_lib.gson.RealtimeInput;
import me.sshcrack.mc_talking.api.audio.AudioChunk;
import me.sshcrack.mc_talking.api.audio.AudioFormat;
import me.sshcrack.mc_talking.api.session.BundledSession;
import me.sshcrack.mc_talking.api.session.ToolCallHandler;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class GeminiLiveSession implements BundledSession {
    private final String model;
    private final String systemPrompt;
    private final String voiceName;
    private final boolean enableAudio;
    private final List<ToolDefinition> tools;

    private volatile InternalClient client;
    private volatile boolean active = false;

    private final CopyOnWriteArrayList<Consumer<String>> textHandlers = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Consumer<AudioChunk>> audioHandlers = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Consumer<String>> sttHandlers = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Runnable> turnCompleteHandlers = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Consumer<Throwable>> errorHandlers = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<ToolCallHandler> toolCallHandlers = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Runnable> quotaExceededHandlers = new CopyOnWriteArrayList<>();

    private GeminiLiveSession(Builder builder) {
        this.model = builder.model;
        this.systemPrompt = builder.systemPrompt;
        this.voiceName = builder.voiceName;
        this.enableAudio = builder.enableAudio;
        this.tools = builder.tools;
    }

    @Override
    public void sendAudio(short[] pcmData, int sampleRate) {
        var client = this.client;
        if (client != null && active) {
            client.addPromptAudio(pcmData);
        }
    }

    @Override
    public void sendText(String text) {
        var client = this.client;
        if (client != null && active) {
            client.addPromptTextImmediate(text);
        }
    }

    @Override
    public void interrupt() {
        var client = this.client;
        if (client != null) {
            client.interrupt();
        }
    }

    @Override
    public BundledSession onText(Consumer<String> handler) {
        textHandlers.add(handler);
        return this;
    }

    @Override
    public BundledSession onAudio(Consumer<AudioChunk> handler) {
        audioHandlers.add(handler);
        return this;
    }

    @Override
    public BundledSession onStt(Consumer<String> handler) {
        sttHandlers.add(handler);
        return this;
    }

    @Override
    public BundledSession onTurnComplete(Runnable handler) {
        turnCompleteHandlers.add(handler);
        return this;
    }

    @Override
    public BundledSession onError(Consumer<Throwable> handler) {
        errorHandlers.add(handler);
        return this;
    }

    @Override
    public BundledSession onToolCall(ToolCallHandler handler) {
        toolCallHandlers.add(handler);
        return this;
    }

    @Override
    public BundledSession onQuotaExceeded(Runnable handler) {
        quotaExceededHandlers.add(handler);
        return this;
    }

    @Override
    public void respondToToolCall(String toolCallId, JsonElement result) {
        var client = this.client;
        if (client != null) {
            client.respondToToolCall(toolCallId, result);
        }
    }

    @Override
    public void start() {
        if (active) return;
        active = true;
        client = new InternalClient();
        client.connect();
    }

    @Override
    public void close() {
        active = false;
        var client = this.client;
        if (client != null) {
            client.close();
            this.client = null;
        }
    }

    @Override
    public boolean isActive() {
        return active && client != null && !client.isClosed();
    }

    public record ToolDefinition(String name, String description, JsonObject parameters) {}

    public static class Builder {
        private String model = "gemini-3.1-flash-live-preview";
        private String systemPrompt = "";
        private String voiceName = "Zephyr";
        private boolean enableAudio = true;
        private List<ToolDefinition> tools = List.of();

        public Builder() {
        }

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder systemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
            return this;
        }

        public Builder voiceName(String voiceName) {
            this.voiceName = voiceName;
            return this;
        }

        public Builder enableAudio(boolean enableAudio) {
            this.enableAudio = enableAudio;
            return this;
        }

        public Builder tools(List<ToolDefinition> tools) {
            this.tools = tools;
            return this;
        }

        public GeminiLiveSession build() {
            return new GeminiLiveSession(this);
        }
    }

    private class InternalClient extends me.sshcrack.gemini_live_lib.GeminiLiveClient {
        private final StringBuilder transcript = new StringBuilder();
        private final List<JsonObject> pendingToolResponses = new ArrayList<>();

        InternalClient() {
            super(GeminiLiveLib.resolveApiKey());
        }

        @Override
        public BidiGenerateContentSetup getSetup() {
            var setup = new BidiGenerateContentSetup("models/" + model);
            if (enableAudio) {
                setup.generationConfig.responseModalities = List.of("AUDIO");
                setup.generationConfig.speechConfig = new BidiGenerateContentSetup.GenerationConfig.SpeechConfig();
                setup.generationConfig.speechConfig.language_code = "en-US";
                setup.generationConfig.speechConfig.voice_config = new BidiGenerateContentSetup.GenerationConfig.SpeechConfig.VoiceConfig();
                setup.generationConfig.speechConfig.voice_config.prebuiltVoiceConfig = new BidiGenerateContentSetup.GenerationConfig.SpeechConfig.PrebuiltVoiceConfig();
                setup.generationConfig.speechConfig.voice_config.prebuiltVoiceConfig.voice_name = voiceName;
            } else {
                setup.generationConfig.responseModalities = List.of("TEXT");
            }

            if (systemPrompt != null && !systemPrompt.isEmpty()) {
                var sys = new BidiGenerateContentSetup.SystemInstruction();
                var p = new BidiGenerateContentSetup.SystemInstruction.Part(systemPrompt);
                sys.parts.add(p);
                setup.systemInstruction = sys;
            }

            return setup;
        }

        @Override
        public void addPromptAudio(short[] audio) {
            var byteAudio = new byte[audio.length * 2];
            for (int i = 0; i < audio.length; i++) {
                byteAudio[i * 2] = (byte) (audio[i] & 0xFF);
                byteAudio[i * 2 + 1] = (byte) ((audio[i] >> 8) & 0xFF);
            }
            var input = new RealtimeInput();
            input.audio = new RealtimeInput.Blob("audio/pcm;rate=48000", byteAudio);
            send(ClientMessages.input(input));
        }

        public void addPromptTextImmediate(String text) {
            var input = new RealtimeInput();
            input.text = text;
            send(ClientMessages.input(input));
        }

        public void respondToToolCall(String toolCallId, JsonElement result) {
            var response = new JsonObject();
            response.addProperty("id", toolCallId);
            response.add("result", result);
            pendingToolResponses.add(response);
        }

        public void interrupt() {
            // WebSocket-level interruption is handled by closing and reopening
            close();
        }

        @Override
        public void onSetupComplete() {
            GeminiLiveLib.LOGGER.info("[GeminiLiveSession] Setup complete");
            // Flush any pending tool responses
            for (JsonObject resp : pendingToolResponses) {
                var toolResponse = new me.sshcrack.gemini_live_lib.gson.BidiGenerateContentToolResponse();
                toolResponse.functionResponses.add(
                    new me.sshcrack.gemini_live_lib.gson.BidiGenerateContentToolResponse.FunctionResponse(
                        resp.get("id").getAsString(),
                        "respond",
                        resp.get("result").getAsJsonObject()
                    )
                );
                send(ClientMessages.response(toolResponse));
            }
            pendingToolResponses.clear();
        }

        @Override
        public void onGeneratedText(String text) {
            transcript.append(text);
            for (var handler : textHandlers) {
                handler.accept(text);
            }
        }

        @Override
        public void onGeneratedAudio(byte[] audio, int sampleRate) {
            var chunk = new AudioChunk(audio, new AudioFormat(sampleRate, 16, true, false));
            for (var handler : audioHandlers) {
                handler.accept(chunk);
            }
        }

        @Override
        public void onOutputTranscription(String transcription) {
            for (var handler : sttHandlers) {
                handler.accept(transcription);
            }
        }

        @Override
        public void onTurnComplete() {
            transcript.setLength(0);
            for (var handler : turnCompleteHandlers) {
                handler.run();
            }
        }

        @Override
        public void onError(Exception ex) {
            GeminiLiveLib.LOGGER.error("[GeminiLiveSession] Error", ex);
            for (var handler : errorHandlers) {
                handler.accept(ex);
            }
        }

        @Override
        public void onQuotaExceeded() {
            GeminiLiveLib.LOGGER.warn("[GeminiLiveSession] Quota exceeded");
            for (var handler : quotaExceededHandlers) {
                handler.run();
            }
        }

        @Override
        public JsonObject onFunctionCall(String name, JsonObject args) {
            for (var handler : toolCallHandlers) {
                var result = handler.onToolCall("", name, args);
                if (result != null) {
                    return result.getAsJsonObject();
                }
            }
            return null;
        }
    }
}
