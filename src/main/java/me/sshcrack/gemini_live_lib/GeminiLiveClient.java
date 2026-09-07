package me.sshcrack.gemini_live_lib;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.sshcrack.gemini_live_lib.gson.BidiGenerateContentSetup;
import me.sshcrack.gemini_live_lib.gson.BidiGenerateContentToolResponse;
import me.sshcrack.gemini_live_lib.gson.ClientMessages;
import me.sshcrack.gemini_live_lib.websocket.client.WebSocketClient;
import me.sshcrack.gemini_live_lib.websocket.drafts.Draft_6455;
import me.sshcrack.gemini_live_lib.websocket.handshake.ServerHandshake;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;

public abstract class GeminiLiveClient extends WebSocketClient {
    protected final long batchTimeout; // 100ms batch window
    protected final int maxBatchSize; // Maximum number of audio packets in a batch

    private volatile Timer batchTimer;
    private volatile TimerTask currentBatchTask;


    private volatile boolean setupComplete = false;
    private final int startupTimeoutMillis;
    private final Object startupLock = new Object();
    private Timer startupTimer;
    private boolean startupCancelled;

    private static String getUrl(String apiKey) {
        return "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=" + apiKey;
    }

    public GeminiLiveClient(String apiKey) {
        this(apiKey, 15_000);
    }

    /** Deadline covers TCP, TLS, WebSocket upgrade and the Gemini setup acknowledgement. */
    protected GeminiLiveClient(String apiKey, int startupTimeoutMillis) {
        super(URI.create(getUrl(apiKey)), new Draft_6455(), null, startupTimeoutMillis);
        if (startupTimeoutMillis <= 0) throw new IllegalArgumentException("startup timeout must be positive");
        this.startupTimeoutMillis = startupTimeoutMillis;

        this.batchTimeout = 100;
        this.maxBatchSize = 5;
    }

    @Override
    public void connect() {
        synchronized (startupLock) {
            cancelStartupTimer();
            setupComplete = false;
            startupCancelled = false;
            var connection = getConnection();
            startupTimer = new Timer("gemini-live-startup", true);
            startupTimer.schedule(new TimerTask() {
                @Override public void run() {
                    synchronized (startupLock) {
                        if (startupCancelled || setupComplete || getConnection() != connection) return;
                        startupCancelled = true;
                    }
                    // Closing the physical socket interrupts a blocked TLS/HTTP read as well.
                    try {
                        var socket = getSocket();
                        if (getConnection() == connection && socket != null) socket.close();
                    } catch (java.io.IOException ignored) { }
                    connection.closeConnection(1006, "Gemini startup deadline exceeded");
                }
            }, startupTimeoutMillis);
            try {
                super.connect();
            } catch (RuntimeException error) {
                cancelStartupTimer();
                throw error;
            }
        }
    }

    private void cancelStartupTimer() {
        startupCancelled = true;
        if (startupTimer != null) {
            startupTimer.cancel();
            startupTimer = null;
        }
    }


    public boolean isSetupComplete() {
        return setupComplete;
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        synchronized (startupLock) {
            setupComplete = false;
            cancelStartupTimer();
        }
        if (reason != null && reason.contains("You exceeded your current quota, please")) {
            onQuotaExceeded();
        }
    }

    public void onQuotaExceeded() {
    }

    public abstract BidiGenerateContentSetup getSetup();

    @Override
    public void onOpen(ServerHandshake data) {
        setupComplete = false;
        try {
            send(ClientMessages.setup(getSetup()));
        } catch (RuntimeException error) {
            // A half-configured open socket cannot carry a conversation. Do not leave it
            // alive merely because application setup construction failed.
            try { closeConnection(1007, "Failed to construct or send Gemini setup"); }
            finally { onError(error); }
        }
    }

    public void onSetupComplete() {
    }

    @Override
    public void onMessage(ByteBuffer bytes) {
        String newContent = StandardCharsets.UTF_8.decode(bytes.asReadOnlyBuffer()).toString();
        onMessage(newContent);
    }

    @Override
    public void onMessage(String message) {
        var p = JsonParser.parseString(message);
        if (!p.isJsonObject())
            return;
        var outer = p.getAsJsonObject();
        if (outer.has("setupComplete")) {
            synchronized (startupLock) {
                if (startupCancelled || setupComplete) return;
                setupComplete = true;
                cancelStartupTimer();
            }
            onSetupComplete();
            return;
        }


        if (!setupComplete)
            return;

        if (outer.has("usageMetadata")) {
            onUsageMetadata(outer.getAsJsonObject("usageMetadata"));
        }

        if (outer.has("toolCall")) {
            var obj = outer.getAsJsonObject("toolCall");
            if (!obj.has("functionCalls") || !obj.get("functionCalls").isJsonArray())
                return;

            var functionCalls = obj.getAsJsonArray("functionCalls");
            for (JsonElement fnCall : functionCalls) {
                if (!fnCall.isJsonObject())
                    continue;

                var objFnCall = fnCall.getAsJsonObject();
                if (!objFnCall.has("name") || !objFnCall.get("name").isJsonPrimitive())
                    continue;

                var name = objFnCall.get("name").getAsString();
                JsonObject args = null;
                if (objFnCall.has("args")) {
                    args = objFnCall.getAsJsonObject("args");
                }

                var output = onFunctionCall(name, args);
                if (output == null) {
                    System.err.println("Function call " + name + " returned null output, using empty object instead.");
                    output = new JsonObject();
                    output.addProperty("error", "Function call returned null output");
                }

                var id = objFnCall.has("id") ? objFnCall.get("id").getAsString() : "";
                var res = new BidiGenerateContentToolResponse();
                res.functionResponses.add(new BidiGenerateContentToolResponse.FunctionResponse(
                        id,
                        name,
                        output
                ));

                send(ClientMessages.response(res));
            }
        }

        if (outer.has("sessionResumptionUpdate")) {
            var obj = outer.get("sessionResumptionUpdate").getAsJsonObject();
            if (!obj.has("newHandle") || !obj.get("newHandle").isJsonPrimitive())
                return;

            if (!obj.has("resumable"))
                return;
            var handle = obj.get("newHandle").getAsString();

            onSessionResumptionUpdate(handle, obj.get("resumable").getAsBoolean());
            return;
        }
        if (outer.has("serverContent") && outer.get("serverContent").isJsonObject()) {
            var obj = outer.getAsJsonObject("serverContent");
            if (obj.has("outputTranscription")) {
                onOutputTranscription(obj.get("outputTranscription").getAsJsonObject().get("text").getAsString());
            }

            if (obj.has("inputTranscription")) {
                onInputTranscription(obj.get("inputTranscription").getAsJsonObject().get("text").getAsString());
            }

            if (obj.has("modelTurn")) {
                var modelTurn = obj.getAsJsonObject("modelTurn");
                if (modelTurn.has("parts")) {
                    var parts = modelTurn.getAsJsonArray("parts");
                    for (var part : parts) {
                        if (!part.isJsonObject())
                            continue;

                        var pObj = part.getAsJsonObject();
                        if (pObj.has("text") && pObj.get("text").isJsonPrimitive()) {
                            var text = pObj.get("text").getAsString();
                            onGeneratedText(text);
                        }

                        if (!pObj.has("inlineData") || !pObj.get("inlineData").isJsonObject())
                            continue;

                        var inlineData = pObj.getAsJsonObject("inlineData");
                        if (!inlineData.has("data") || !inlineData.get("data").isJsonPrimitive())
                            continue;

                        var mimeType = inlineData.get("mimeType").getAsString();
                        if (!mimeType.contains("audio/pcm")) {
                            System.err.println("Invalid mime type: " + inlineData.get("mimeType").getAsString());
                            continue;
                        }

                        var sampleRateStr = mimeType.split("rate=")[1];
                        var sampleRate = Integer.parseInt(sampleRateStr);

                        var data = Base64.getDecoder().decode(inlineData.get("data").getAsString());
                        onGeneratedAudio(data, sampleRate);
                    }
                }
            }

            if (obj.has("interrupted") && obj.get("interrupted").getAsBoolean()) onInterrupted();
            if (obj.has("generationComplete") && obj.get("generationComplete").getAsBoolean()) onGenerationComplete();
            if (obj.has("turnComplete") && obj.get("turnComplete").getAsBoolean()) onTurnComplete();
            if (!obj.has("modelTurn") && !obj.has("outputTranscription") && !obj.has("inputTranscription")
                    && !obj.has("interrupted") && !obj.has("generationComplete") && !obj.has("turnComplete")) {
                onUnknownMessage(outer);
            }
        }
    }

    public JsonObject onFunctionCall(String name, @Nullable JsonObject args) {
        return null;
    }

    public void onUnknownMessage(JsonObject json) {

    }

    public void onGeneratedAudio(byte[] audio, int sampleRate) {
        // Default implementation does nothing
    }

    public void onGeneratedText(String text) {
        // Default implementation does nothing
    }

    public void onUsageMetadata(JsonObject obj) {
    }

    public void onSessionResumptionUpdate(String newHandle, boolean resumable) {

    }

    public void onGenerationComplete() {

    }

    public void onInterrupted() {

    }

    public void onTurnComplete() {

    }

    public void onInputTranscription(String transcription) {
    }

    public void onOutputTranscription(String transcription) {
    }

    public abstract void addPromptAudio(short[] audio);

    @Override
    public void close() {
        synchronized (startupLock) {
            setupComplete = false;
            cancelStartupTimer();
        }
        // Clean up timer resources
        if (batchTimer != null) {
            batchTimer.cancel();
            batchTimer = null;
        }
        if (currentBatchTask != null) {
            currentBatchTask.cancel();
            currentBatchTask = null;
        }

        super.close();
    }
}
