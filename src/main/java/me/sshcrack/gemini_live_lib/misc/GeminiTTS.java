package me.sshcrack.gemini_live_lib.misc;

import com.google.gson.Gson;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

public class GeminiTTS {
    private static final HttpClient client = HttpClient.newHttpClient();

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
        Gson gson = new Gson();
        String payloadStr = gson.toJson(payload, RequestPayload.class);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(getUrl(model, apiKey)))
                .POST(HttpRequest.BodyPublishers.ofString(payloadStr))
                .header("Content-Type", "application/json")
                .build();

        HttpResponse<InputStream> response = client.send(
                request,
                HttpResponse.BodyHandlers.ofInputStream()
        );

        InputStream is = response.body();
        boolean foundAny = false;
        try (InputStreamReader isr = new InputStreamReader(is); JsonReader jr = new JsonReader(isr)) {
            jr.setLenient(true);

            JsonToken next = jr.peek();
            if (next == JsonToken.BEGIN_ARRAY) {
                jr.beginArray();
                while (jr.hasNext()) {
                    TtsResponse.Item item = gson.fromJson(jr, TtsResponse.Item.class);
                    if (item == null || item.candidates == null) continue;

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
                }
                jr.endArray();
            } else {
                // Possibly newline-delimited JSON objects or a single object
                while (jr.peek() != JsonToken.END_DOCUMENT) {
                    if (jr.peek() == JsonToken.BEGIN_OBJECT) {
                        TtsResponse.Item item = gson.fromJson(jr, TtsResponse.Item.class);
                        if (item == null || item.candidates == null) continue;
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
                    } else {
                        jr.skipValue();
                    }
                }
            }
        }

        if (!foundAny) {
            throw new UnexpectedResponseException("Expected audio chunks for TTS generation, but none were streamed.");
        }
    }
}
