package me.sshcrack.gemini_live_lib.misc;

import com.google.gson.Gson;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    public static List<AudioChunk> generateAudioConversation(String model, String apiKey, RequestPayload payload) throws IOException, InterruptedException, UnexpectedResponseException {
        Gson gson = new Gson();
        String payloadStr = gson.toJson(payload, RequestPayload.class);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(getUrl(model, apiKey)))
                .POST(HttpRequest.BodyPublishers.ofString(payloadStr))
                .header("Content-Type", "application/json")
                .build();

        HttpResponse<String> response = client.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );


        TtsResponse.Item[] responseArray = gson.fromJson(response.body(), TtsResponse.Item[].class);

        List<AudioChunk> audioChunks = new ArrayList<>();
        for (TtsResponse.Item item : responseArray) {
            if (item.candidates == null) continue;

            for (TtsResponse.Candidate candidate : item.candidates) {
                if (candidate.content == null || candidate.content.parts == null) continue;

                for (TtsResponse.Part part : candidate.content.parts) {
                    if (part.inlineData == null) continue;

                    byte[] audioBytes = Base64.getDecoder().decode(part.inlineData.data);
                    String mime = part.inlineData.mimeType;

                    int sampleRate = extractSampleRate(mime);
                    audioChunks.add(new AudioChunk(audioBytes, sampleRate));
                }
            }
        }

        if (audioChunks.isEmpty()) {
            throw new UnexpectedResponseException(String.format("Expected audio chunks for TTS generation, but got: %s", response.body()));
        }

        return audioChunks;
    }
}
