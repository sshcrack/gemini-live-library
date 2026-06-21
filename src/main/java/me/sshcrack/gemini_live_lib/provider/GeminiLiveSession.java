package me.sshcrack.gemini_live_lib.provider;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import me.sshcrack.gemini_live_lib.GeminiLiveClient;
import me.sshcrack.gemini_live_lib.gson.BidiGenerateContentSetup;
import me.sshcrack.gemini_live_lib.gson.ClientMessages;
import me.sshcrack.gemini_live_lib.gson.RealtimeInput;
import me.sshcrack.gemini_live_lib.gson.properties.ArrayProperty;
import me.sshcrack.gemini_live_lib.gson.properties.EnumProperty;
import me.sshcrack.gemini_live_lib.gson.properties.ObjectProperty;
import me.sshcrack.gemini_live_lib.gson.properties.PrimitiveProperty;
import me.sshcrack.gemini_live_lib.gson.properties.Property;
import me.sshcrack.gemini_live_lib.websocket.handshake.ServerHandshake;
import me.sshcrack.mc_talking.api.provider.AudioData;
import me.sshcrack.mc_talking.api.provider.LiveSession;
import me.sshcrack.mc_talking.api.provider.LiveSessionConfig;
import me.sshcrack.mc_talking.api.provider.LiveSessionListener;
import me.sshcrack.mc_talking.api.provider.ToolCall;
import me.sshcrack.mc_talking.api.provider.ToolDefinition;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

public class GeminiLiveSession implements LiveSession {
    private final WsClient client;
    private final LiveSessionConfig config;
    private volatile LiveSessionListener listener;

    private final List<short[]> pendingAudio = Collections.synchronizedList(new ArrayList<>());
    private final List<String> pendingTextAfterTalking = new CopyOnWriteArrayList<>();
    private final Timer batchTimer = new Timer("gemini-live-batch", true);
    private static final int BATCH_TIMEOUT_MS = 100;
    private static final int MAX_BATCH_SIZE = 5;

    private volatile boolean closed = false;

    public GeminiLiveSession(String apiKey, LiveSessionConfig config) {
        this.config = config;
        this.client = new WsClient(apiKey);
    }

    @Override
    public void setListener(LiveSessionListener listener) {
        this.listener = listener;
    }

    @Override
    public LiveSessionListener getListener() {
        return listener;
    }

    @Override
    public void sendAudio(short[] pcmAudio) {
        if (closed || client.isClosed()) return;
        pendingAudio.add(pcmAudio);
        scheduleBatchFlushIfNeeded();
    }

    @Override
    public void sendText(String text) {
        if (closed || client.isClosed()) return;
        var input = new RealtimeInput();
        input.text = text;
        client.send(ClientMessages.input(input));
    }

    @Override
    public void addPromptTextAfterTalkingComplete(String text) {
        pendingTextAfterTalking.add(text);
    }

    @Override
    public void interrupt() {
        if (closed) return;
        flushAudioBatch();
    }

    @Override
    public void close() {
        closed = true;
        batchTimer.cancel();
        client.close();
    }

    @Override
    public boolean isOpen() {
        return client.isOpen();
    }

    @Override
    public boolean isClosed() {
        return closed || client.isClosed();
    }

    @Override
    public boolean isActive() {
        return client.isOpen() && !closed;
    }

    @Override
    public CompletableFuture<Void> connect() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        new Thread(() -> {
            try {
                client.connectBlocking();
                future.complete(null);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        }, "gemini-live-connect").start();
        return future;
    }

    private void scheduleBatchFlushIfNeeded() {
        if (pendingAudio.size() >= MAX_BATCH_SIZE) {
            flushAudioBatch();
        }
    }

    private void flushAudioBatch() {
        if (pendingAudio.isEmpty()) return;
        List<short[]> batch;
        synchronized (pendingAudio) {
            if (pendingAudio.isEmpty()) return;
            batch = new ArrayList<>(pendingAudio);
            pendingAudio.clear();
        }

        for (short[] audio : batch) {
            byte[] pcmBytes = shortsToBytes(audio);
            var blob = new RealtimeInput.Blob("audio/pcm;rate=16000", pcmBytes);
            var input = new RealtimeInput();
            input.audio = blob;
            client.send(ClientMessages.input(input));
        }
    }

    private static byte[] shortsToBytes(short[] shorts) {
        byte[] bytes = new byte[shorts.length * 2];
        for (int i = 0; i < shorts.length; i++) {
            bytes[i * 2] = (byte) (shorts[i] & 0xFF);
            bytes[i * 2 + 1] = (byte) ((shorts[i] >> 8) & 0xFF);
        }
        return bytes;
    }

    private BidiGenerateContentSetup buildSetup() {
        String modelName = config.modelName() != null ? config.modelName() : "gemini-2.5-flash-native-audio-preview-12-2025";
        var setup = new BidiGenerateContentSetup("models/" + modelName);

        setup.generationConfig.responseModalities = new ArrayList<>();
        setup.generationConfig.responseModalities.add("AUDIO");

        if (config.language() != null) {
            setup.generationConfig.speechConfig = new BidiGenerateContentSetup.GenerationConfig.SpeechConfig();
            setup.generationConfig.speechConfig.language_code = config.language();
            if (config.voice() != null) {
                setup.generationConfig.speechConfig.voice_config = new BidiGenerateContentSetup.GenerationConfig.SpeechConfig.VoiceConfig();
                setup.generationConfig.speechConfig.voice_config.prebuiltVoiceConfig = new BidiGenerateContentSetup.GenerationConfig.SpeechConfig.PrebuiltVoiceConfig();
                setup.generationConfig.speechConfig.voice_config.prebuiltVoiceConfig.voice_name = config.voice();
            }
        }

        if (config.resumptionHandle() != null && !config.resumptionHandle().isBlank()) {
            setup.sessionResumption = new BidiGenerateContentSetup.SessionResumptionConfig(config.resumptionHandle());
        }

        if (!config.systemPrompt().isBlank()) {
            var sys = new BidiGenerateContentSetup.SystemInstruction();
            sys.parts.add(new BidiGenerateContentSetup.SystemInstruction.Part(config.systemPrompt()));
            setup.systemInstruction = sys;
        }

        for (ToolDefinition toolDef : config.tools()) {
            var tool = new BidiGenerateContentSetup.Tool();
            var decl = new BidiGenerateContentSetup.Tool.FunctionDeclaration(toolDef.name(), toolDef.description());

            if (!toolDef.parameters().isEmpty()) {
                var objProp = new ObjectProperty();
                for (Map.Entry<String, Object> entry : toolDef.parameters().entrySet()) {
                    Object value = entry.getValue();
                    if (value instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> paramObj = (Map<String, Object>) value;
                        String type = (String) paramObj.getOrDefault("type", "string");
                        objProp.addProperty(entry.getKey(), toProperty(type, paramObj));
                    }
                }
                decl.parameters = objProp;
            }

            tool.functionDeclarations.add(decl);
            setup.tools.add(tool);
        }

        setup.outputAudioTranscription = new JsonObject();
        setup.realtimeInputConfig = new BidiGenerateContentSetup.RealtimeInputConfig();

        return setup;
    }

    private Property toProperty(String type, Map<String, Object> paramObj) {
        return switch (type) {
            case "string" -> {
                if (paramObj.containsKey("enum")) {
                    @SuppressWarnings("unchecked")
                    List<String> enumValues = (List<String>) paramObj.get("enum");
                    yield new EnumProperty(enumValues, false);
                }
                yield new PrimitiveProperty(PrimitiveProperty.Type.STRING, false);
            }
            case "integer" -> new PrimitiveProperty(PrimitiveProperty.Type.INTEGER, false);
            case "number" -> new PrimitiveProperty(PrimitiveProperty.Type.NUMBER, false);
            case "boolean" -> new PrimitiveProperty(PrimitiveProperty.Type.BOOLEAN, false);
            case "array" -> {
                @SuppressWarnings("unchecked")
                Map<String, Object> items = (Map<String, Object>) paramObj.getOrDefault("items", Map.of());
                String itemType = (String) items.getOrDefault("type", "string");
                yield new ArrayProperty(toProperty(itemType, items), false);
            }
            case "object" -> {
                var obj = new ObjectProperty();
                @SuppressWarnings("unchecked")
                Map<String, Object> properties = (Map<String, Object>) paramObj.getOrDefault("properties", Map.of());
                for (Map.Entry<String, Object> prop : properties.entrySet()) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> propObj = (Map<String, Object>) prop.getValue();
                    String propType = (String) propObj.getOrDefault("type", "string");
                    obj.addProperty(prop.getKey(), toProperty(propType, propObj));
                }
                yield obj;
            }
            default -> new PrimitiveProperty(PrimitiveProperty.Type.STRING, false);
        };
    }

    private class WsClient extends GeminiLiveClient {
        WsClient(String apiKey) {
            super(apiKey);
        }

        @Override
        public BidiGenerateContentSetup getSetup() {
            return buildSetup();
        }

        @Override
        public void addPromptAudio(short[] audio) {
            sendAudio(audio);
        }

        @Override
        public void onSetupComplete() {
            if (!pendingTextAfterTalking.isEmpty()) {
                for (String text : pendingTextAfterTalking) {
                    sendText(text);
                }
                pendingTextAfterTalking.clear();
            }
        }

        @Override
        public void onGeneratedText(String text) {
            var l = listener;
            if (l != null) l.onGeneratedText(text);
        }

        @Override
        public void onGeneratedAudio(byte[] audio, int sampleRate) {
            var l = listener;
            if (l != null) {
                l.onGeneratedAudio(new AudioData(audio, sampleRate, 1));
            }
        }

        @Override
        public void onTurnComplete() {
            var l = listener;
            if (l != null) l.onTurnComplete();
        }

        @Override
        public void onInterrupted() {
            var l = listener;
            if (l != null) l.onInterrupted();
        }

        @Override
        public void onGenerationComplete() {
            var l = listener;
            if (l != null) l.onGenerationComplete();
        }

        @Override
        public void onSessionResumptionUpdate(String newHandle, boolean resumable) {
            var l = listener;
            if (l != null) l.onSessionResumptionUpdate(newHandle, resumable);
        }

        @Override
        public void onInputTranscription(String transcription) {
            var l = listener;
            if (l != null) l.onInputTranscription(transcription);
        }

        @Override
        public void onOutputTranscription(String transcription) {
            var l = listener;
            if (l != null) l.onOutputTranscription(transcription);
        }

        @Override
        public JsonObject onFunctionCall(String name, @Nullable JsonObject args) {
            var l = listener;
            if (l != null) {
                Map<String, Object> argsMap = args != null ? new Gson().fromJson(args, Map.class) : Map.of();
                var toolCall = new ToolCall("", name, argsMap);
                l.onToolCall(toolCall);
            }
            return super.onFunctionCall(name, args);
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            var l = listener;
            if (l != null) l.onClosed(code, reason);
            closed = true;
        }

        @Override
        public void onQuotaExceeded() {
            var l = listener;
            if (l != null) l.onError(new RuntimeException("Quota exceeded for Gemini Live API"));
        }
    }
}
