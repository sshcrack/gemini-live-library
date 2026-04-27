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
    private final List<short[]> audioBatch = Collections.synchronizedList(new ArrayList<>());
    protected final long BATCH_TIMEOUT; // 100ms batch window
    protected final int MAX_BATCH_SIZE; // Maximum number of audio packets in a batch

    private volatile Timer batchTimer;
    private volatile TimerTask currentBatchTask;
    private final Object batchLock = new Object();


    private boolean setupComplete = false;

    private static String getUrl(String apiKey) {
        return "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=" + apiKey;
    }

    public GeminiLiveClient(String apiKey) {
        super(URI.create(getUrl(apiKey)));

        this.BATCH_TIMEOUT = 100;
        this.MAX_BATCH_SIZE = 5;
    }

    public GeminiLiveClient(String apiKey, long batchTimeout, int maxBatchSize) {
        super(URI.create(getUrl(apiKey)));
        this.BATCH_TIMEOUT = batchTimeout;
        this.MAX_BATCH_SIZE = maxBatchSize;
    }

    public boolean isSetupComplete() {
        return setupComplete;
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        if (reason.contains("You exceeded your current quota, please")) {
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

    /**
     * Batches audio data and sends it when a batch is complete or times out
     *
     * @param audio The audio data to batch
     */
    public void batchAudio(short[] audio) {
        boolean batchFull;
        boolean isFirstElement;

        synchronized (batchLock) {
            // Add to batch
            audioBatch.add(audio);

            // Check if batch is full
            batchFull = audioBatch.size() >= MAX_BATCH_SIZE;
            isFirstElement = audioBatch.size() == 1;
        }

        if (batchFull) {
            // Process and send the batch immediately
            sendCurrentBatch();
        } else if (isFirstElement) {
            // If this is the first element in the batch, start the timer
            scheduleFlushTimer();
        }
    }


    /**
     * Schedules a timer to flush the current batch after the timeout period
     */
    protected void scheduleFlushTimer() {
        // Cancel any existing task
        if (currentBatchTask != null) {
            currentBatchTask.cancel();
        }

        // Create new task
        currentBatchTask = new TimerTask() {
            @Override
            public void run() {
                if (!audioBatch.isEmpty()) {
                    sendCurrentBatch();
                }
            }
        };

        // Initialize timer if needed
        if (batchTimer == null) {
            batchTimer = new Timer("AudioBatchTimer", true);
        }

        // Schedule the task
        batchTimer.schedule(currentBatchTask, BATCH_TIMEOUT);
    }

    /**
     * Combines and sends the current batch of audio
     */
    protected void sendCurrentBatch() {
        List<short[]> batchCopy;

        synchronized (batchLock) {
            if (audioBatch.isEmpty()) return;

            // Create a copy of the batch to work with
            batchCopy = new ArrayList<>(audioBatch);
            // Clear the original batch immediately to allow new additions
            audioBatch.clear();
        }

        // Process the copy outside the synchronized block
        int totalLength = 0;
        for (short[] audioData : batchCopy) {
            totalLength += audioData.length;
        }

        short[] combinedAudio = new short[totalLength];
        int position = 0;

        for (short[] audioData : batchCopy) {
            System.arraycopy(audioData, 0, combinedAudio, position, audioData.length);
            position += audioData.length;
        }

        // Send the combined audio
        addPromptAudio(combinedAudio);
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
