package me.sshcrack.gemini_live_lib.misc;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

public class GeminiTTS {
    private static final HttpClient client = HttpClient.newHttpClient();
    private static final int DEFAULT_MAX_ATTEMPTS = 3;
    private static final long INITIAL_RETRY_DELAY_MS = 500L;
    private static final long MAX_QUOTA_RETRY_DELAY_MS = 6000L;

    public static class RequestPayload {

        public List<Content> contents;
        public GenerationConfig generationConfig;

        public static class Content {
            public String role;
            public List<Part> parts;
        }

        public static class Part {
            public String text;
        }

        public static class GenerationConfig {
            public List<String> responseModalities;
            public double temperature;
            public SpeechConfig speech_config;
        }

        public static class SpeechConfig {
            public MultiSpeakerVoiceConfig multi_speaker_voice_config;
        }

        public static class MultiSpeakerVoiceConfig {
            public List<SpeakerVoiceConfig> speaker_voice_configs;
        }

        public static class SpeakerVoiceConfig {
            public String speaker;
            public VoiceConfig voice_config;
        }

        public static class VoiceConfig {
            public PrebuiltVoiceConfig prebuilt_voice_config;
        }

        public static class PrebuiltVoiceConfig {
            public String voice_name;
        }
    }

    public static class TtsResponse {

        public List<Item> items;

        public static class Item {
            public List<Candidate> candidates;
        }

        public static class Candidate {
            public Content content;
        }

        public static class Content {
            public List<Part> parts;
        }

        public static class Part {
            public InlineData inlineData;
            public String text; // sometimes present instead
        }

        public static class InlineData {
            public String mimeType;
            public String data; // base64
        }
    }

    private static String getUrl(String model, String apiKey) {
        return String.format("https://generativelanguage.googleapis.com/v1beta/models/%s:streamGenerateContent?key=%s", model, apiKey);
    }

    public record AudioChunk(byte[] audioBytes, int sampleRate) {
    }

    private static int extractSampleRate(String mimeType) {
        if (mimeType == null) return -1;

        Matcher m = Pattern.compile("rate=(\\d+)").matcher(mimeType);
        if (m.find()) {
            return Integer.parseInt(m.group(1));
        }
        return -1;
    }

    /**
     * Streams the TTS response and passes decoded audio chunks to the provided consumer.
     * The method blocks while the HTTP response is being read.
     */
    public static void streamGenerateAudioConversation(String model, String apiKey, RequestPayload payload, Consumer<AudioChunk> chunkConsumer) throws IOException, InterruptedException, UnexpectedResponseException {
        streamGenerateAudioConversation(model, apiKey, payload, DEFAULT_MAX_ATTEMPTS, chunkConsumer);
    }

    public static void streamGenerateAudioConversation(String model, String apiKey, RequestPayload payload, int maxAttempts, Consumer<AudioChunk> chunkConsumer) throws IOException, InterruptedException, UnexpectedResponseException {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be at least 1");
        }

        Gson gson = new Gson();
        String payloadStr = gson.toJson(payload, RequestPayload.class);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(getUrl(model, apiKey)))
                .POST(HttpRequest.BodyPublishers.ofString(payloadStr))
                .header("Content-Type", "application/json")
                .build();

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                HttpResponse<InputStream> response = client.send(
                        request,
                        HttpResponse.BodyHandlers.ofInputStream()
                );

                int statusCode = response.statusCode();
                if (statusCode >= 200 && statusCode < 300) {
                    boolean foundAny = false;
                    try (InputStream is = response.body(); InputStreamReader isr = new InputStreamReader(is); JsonReader jr = new JsonReader(isr)) {
                        jr.setLenient(true);
                        foundAny = consumeStreamedAudioChunks(gson, jr, chunkConsumer);
                    }

                    if (!foundAny) {
                        throw new UnexpectedResponseException("Expected audio chunks for TTS generation, but none were streamed.");
                    }

                    return;
                }

                String responseBody = readResponseBody(response.body());
                if (statusCode == 429) {
                    Long retryDelayMs = extractRetryDelayMillis(responseBody);
                    if (retryDelayMs != null && retryDelayMs < MAX_QUOTA_RETRY_DELAY_MS) {
                        if (attempt < maxAttempts) {
                            Thread.sleep(retryDelayMs);
                            continue;
                        }

                        throw buildUnexpectedResponseException(statusCode, responseBody);
                    }
                }

                if (statusCode >= 500 && statusCode < 600) {
                    if (attempt < maxAttempts) {
                        sleepBeforeRetry(attempt);
                        continue;
                    }

                    throw buildUnexpectedResponseException(statusCode, responseBody);
                }

                throw buildUnexpectedResponseException(statusCode, responseBody);
            } catch (IOException e) {
                if (attempt < maxAttempts) {
                    sleepBeforeRetry(attempt);
                    continue;
                }

                throw e;
            }
        }

        throw new UnexpectedResponseException("Gemini API request failed after " + maxAttempts + " attempts.");
    }

    private static boolean consumeStreamedAudioChunks(Gson gson, JsonReader jr, Consumer<AudioChunk> chunkConsumer) throws IOException {
        boolean foundAny = false;

        JsonToken next = jr.peek();
        if (next == JsonToken.BEGIN_ARRAY) {
            jr.beginArray();
            while (jr.hasNext()) {
                TtsResponse.Item item = gson.fromJson(jr, TtsResponse.Item.class);
                foundAny |= consumeItem(item, chunkConsumer);
            }
            jr.endArray();
            return foundAny;
        }

        // Possibly newline-delimited JSON objects or a single object
        while (jr.peek() != JsonToken.END_DOCUMENT) {
            if (jr.peek() == JsonToken.BEGIN_OBJECT) {
                TtsResponse.Item item = gson.fromJson(jr, TtsResponse.Item.class);
                foundAny |= consumeItem(item, chunkConsumer);
            } else {
                jr.skipValue();
            }
        }

        return foundAny;
    }

    private static boolean consumeItem(TtsResponse.Item item, Consumer<AudioChunk> chunkConsumer) {
        if (item == null || item.candidates == null) {
            return false;
        }

        boolean foundAny = false;
        for (TtsResponse.Candidate candidate : item.candidates) {
            if (candidate.content == null || candidate.content.parts == null) continue;
            for (TtsResponse.Part part : candidate.content.parts) {
                if (part.inlineData == null || part.inlineData.data == null) continue;
                byte[] audioBytes = Base64.getDecoder().decode(part.inlineData.data);
                int sampleRate = extractSampleRate(part.inlineData.mimeType);
                chunkConsumer.accept(new AudioChunk(audioBytes, sampleRate));
                foundAny = true;
            }
        }

        return foundAny;
    }

    private static String readResponseBody(InputStream body) throws IOException {
        try (InputStream inputStream = body) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void sleepBeforeRetry(int attempt) throws InterruptedException {
        long delayMillis = INITIAL_RETRY_DELAY_MS * (1L << (attempt - 1));
        Thread.sleep(delayMillis);
    }

    private static Long extractRetryDelayMillis(String responseBody) {
        try {
            JsonObject errorObject = extractErrorObject(responseBody);
            if (errorObject == null || !errorObject.has("details") || !errorObject.get("details").isJsonArray()) {
                return null;
            }

            JsonArray details = errorObject.getAsJsonArray("details");
            for (JsonElement detailElement : details) {
                if (!detailElement.isJsonObject()) continue;

                JsonObject detailObject = detailElement.getAsJsonObject();
                if (!detailObject.has("@type") || !detailObject.has("retryDelay")) continue;

                String type = detailObject.get("@type").getAsString();
                if (!type.endsWith("google.rpc.RetryInfo")) continue;

                Long retryDelayMillis = parseRetryDelayMillis(detailObject.get("retryDelay").getAsString());
                if (retryDelayMillis != null) {
                    return retryDelayMillis;
                }
            }
        } catch (RuntimeException ignored) {
        }

        return null;
    }

    private static Long parseRetryDelayMillis(String retryDelay) {
        Matcher matcher = Pattern.compile("^(\\d+(?:\\.\\d+)?)(ms|s|m|h)$").matcher(retryDelay);
        if (!matcher.matches()) {
            return null;
        }

        double value = Double.parseDouble(matcher.group(1));
        return switch (matcher.group(2)) {
            case "ms" -> (long) value;
            case "s" -> (long) (value * 1000.0d);
            case "m" -> (long) (value * 60_000.0d);
            case "h" -> (long) (value * 3_600_000.0d);
            default -> null;
        };
    }

    private static JsonObject extractErrorObject(String responseBody) {
        JsonElement parsed = JsonParser.parseString(responseBody);
        if (parsed.isJsonObject()) {
            JsonObject object = parsed.getAsJsonObject();
            if (object.has("error") && object.get("error").isJsonObject()) {
                return object.getAsJsonObject("error");
            }

            return object;
        }

        if (parsed.isJsonArray()) {
            JsonArray array = parsed.getAsJsonArray();
            if (array.isEmpty()) {
                return null;
            }

            JsonElement first = array.get(0);
            if (!first.isJsonObject()) {
                return null;
            }

            JsonObject firstObject = first.getAsJsonObject();
            if (firstObject.has("error") && firstObject.get("error").isJsonObject()) {
                return firstObject.getAsJsonObject("error");
            }

            return firstObject;
        }

        return null;
    }

    private static UnexpectedResponseException buildUnexpectedResponseException(int statusCode, String responseBody) {
        JsonObject errorObject = null;
        try {
            errorObject = extractErrorObject(responseBody);
        } catch (RuntimeException ignored) {
        }

        if (errorObject == null) {
            return new UnexpectedResponseException("Gemini API returned HTTP " + statusCode + ": " + responseBody);
        }

        StringBuilder message = new StringBuilder("Gemini API returned HTTP ").append(statusCode);
        if (errorObject.has("status")) {
            message.append(" (status: ").append(errorObject.get("status").getAsString()).append(")");
        }
        if (errorObject.has("code")) {
            message.append(" (code: ").append(errorObject.get("code").getAsInt()).append(")");
        }
        if (errorObject.has("message")) {
            message.append(": ").append(errorObject.get("message").getAsString());
        }
        message.append(" | raw body: ").append(responseBody);
        return new UnexpectedResponseException(message.toString());
    }
}
