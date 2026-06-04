package me.sshcrack.gemini_live_lib;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.sshcrack.gemini_live_lib.gson.BidiGenerateContentSetup;
import me.sshcrack.gemini_live_lib.gson.BidiGenerateContentToolResponse;
import me.sshcrack.gemini_live_lib.gson.ClientMessages;
import me.sshcrack.gemini_live_lib.websocket.client.WebSocketClient;
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


    private boolean setupComplete = false;

    private static String getUrl(String apiKey) {
        return "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=" + apiKey;
    }

    public GeminiLiveClient(String apiKey) {
        super(URI.create(getUrl(apiKey)));

        this.batchTimeout = 100;
        this.maxBatchSize = 5;
    }


    public boolean isSetupComplete() {
        return setupComplete;
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        if (reason != null && reason.contains("You exceeded your current quota, please")) {
            onQuotaExceeded();
        }
    }

    public void onQuotaExceeded() {
    }

    public abstract BidiGenerateContentSetup getSetup();

    @Override
    public void onOpen(ServerHandshake data) {
        send(ClientMessages.setup(getSetup()));
    }

    public void onSetupComplete() {
    }

    @Override
    public void onMessage(ByteBuffer bytes) {
        String newContent = new String(bytes.array(), StandardCharsets.UTF_8);
        onMessage(newContent);
    }

    @Override
    public void onMessage(String message) {
        var p = JsonParser.parseString(message);
        if (!p.isJsonObject())
            return;
        var outer = p.getAsJsonObject();
        if (outer.has("setupComplete")) {
            setupComplete = true;
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

                var res = new BidiGenerateContentToolResponse();
                res.functionResponses.add(new BidiGenerateContentToolResponse.FunctionResponse(
                        objFnCall.get("id").getAsString(),
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
            if (obj.has("generationComplete") && obj.get("generationComplete").getAsBoolean()) {
                onGenerationComplete();
                return;
            }

            if (obj.has("outputTranscription")) {
                onOutputTranscription(obj.get("outputTranscription").getAsJsonObject().get("text").getAsString());
            }

            if (obj.has("inputTranscription")) {
                onInputTranscription(obj.get("inputTranscription").getAsJsonObject().get("text").getAsString());
            }

            if (obj.has("interrupted") && obj.get("interrupted").getAsBoolean()) {
                onInterrupted();
                return;
            }

            if (obj.has("turnComplete") && obj.get("turnComplete").getAsBoolean()) {
                onTurnComplete();
                return;
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
                        return;
                    }
                }
            }

            onUnknownMessage(outer);
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
