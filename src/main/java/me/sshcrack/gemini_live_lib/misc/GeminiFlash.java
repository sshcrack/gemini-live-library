package me.sshcrack.gemini_live_lib.misc;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

public class GeminiFlash {
    private static final HttpClient client = HttpClient.newHttpClient();
    private static final int DEFAULT_MAX_ATTEMPTS = 3;
    private static final long INITIAL_RETRY_DELAY_MS = 500L;

    public static class GenerateContentRequest {
        public static class Part {
            public String text;
        }

        public static class SystemInstruction {
            public List<Part> parts;
        }

        public static class Content {
            public List<Part> parts;
        }

        public SystemInstruction system_instruction;
        public Content contents;
    }

    private static String getUrl(String model, String apiKey) {
        return String.format("https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s", model, apiKey);
    }

    public static String sendSimpleFlashRequest(String model, String apiKey, String systemPrompt, String prompt) throws IOException, InterruptedException, UnexpectedResponseException {
        return sendSimpleFlashRequest(model, apiKey, systemPrompt, prompt, DEFAULT_MAX_ATTEMPTS);
    }

    public static String sendSimpleFlashRequest(String model, String apiKey, String systemPrompt, String prompt, int maxAttempts) throws IOException, InterruptedException, UnexpectedResponseException {
        GenerateContentRequest request = new GenerateContentRequest();
        request.system_instruction = new GenerateContentRequest.SystemInstruction();

        var sytemPart = new GenerateContentRequest.Part();
        sytemPart.text = systemPrompt;
        request.system_instruction.parts = List.of(sytemPart);

        var contentPart = new GenerateContentRequest.Part();
        contentPart.text = prompt;
        request.contents = new GenerateContentRequest.Content();
        request.contents.parts = List.of(contentPart);

        return sendFlashRequest(model, apiKey, request, maxAttempts);
    }


    /**
     * @param model                  the gemini model to use (e.g. "gemini-3-flash-preview")
     * @param apiKey                 your google api key with access to the gemini API
     * @param generateContentRequest the request body to send to the gemini API
     * @return The response body from the gemini API as a string. This is NOT parsed in any way, just the raw response body.
     * @throws IOException                 if an I/O error occurs when sending or receiving
     * @throws InterruptedException        if the operation is interrupted
     * @throws UnexpectedResponseException if the response from the Gemini API is not in the expected format (e.g. missing "candidates" field)
     */
    public static String sendFlashRequest(String model, String apiKey, GenerateContentRequest generateContentRequest) throws IOException, InterruptedException, UnexpectedResponseException {
        return sendFlashRequest(model, apiKey, generateContentRequest, DEFAULT_MAX_ATTEMPTS);
    }

    public static String sendFlashRequest(String model, String apiKey, GenerateContentRequest generateContentRequest, int maxAttempts) throws IOException, InterruptedException, UnexpectedResponseException {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be at least 1");
        }

        Gson gson = new Gson();
        String jsonBody = gson.toJson(generateContentRequest, GenerateContentRequest.class);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(getUrl(model, apiKey)))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .header("Content-Type", "application/json")
                .build();

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                HttpResponse<String> response = client.send(
                        request,
                        HttpResponse.BodyHandlers.ofString()
                );

                if (isRetryableServerResponse(response)) {
                    if (attempt < maxAttempts) {
                        sleepBeforeRetry(attempt);
                        continue;
                    }

                    throw new UnexpectedResponseException("Gemini API returned a retryable server error after " + maxAttempts + " attempts: " + response.body());
                }

                // This is JUST the response body
                JsonObject obj = gson.fromJson(response.body(), JsonObject.class);

                if (obj == null || !obj.has("candidates")) {
                    throw new UnexpectedResponseException("Invalid response from Gemini API: " + response.body());
                }

                JsonArray candidates = obj.getAsJsonArray("candidates");
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < candidates.size(); i++) {
                    JsonObject candidate = candidates.get(i).getAsJsonObject();
                    if (!candidate.has("content")) {
                        throw new UnexpectedResponseException("Invalid response from Gemini API: " + response.body());
                    }

                    JsonObject content = candidate.getAsJsonObject("content");
                    if (!content.has("parts")) {
                        throw new UnexpectedResponseException("Invalid response from Gemini API: " + response.body());
                    }

                    JsonArray parts = content.getAsJsonArray("parts");
                    for (int j = 0; j < parts.size(); j++) {
                        JsonObject part = parts.get(j).getAsJsonObject();
                        if (!part.has("text")) {
                            throw new UnexpectedResponseException("Invalid response from Gemini API: " + response.body());
                        }

                        sb.append(part.get("text").getAsString());
                    }
                }

                return sb.toString();
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

    private static boolean isRetryableServerResponse(HttpResponse<String> response) {
        int statusCode = response.statusCode();
        return statusCode >= 500 && statusCode < 600;
    }

    private static void sleepBeforeRetry(int attempt) throws InterruptedException {
        long delayMillis = INITIAL_RETRY_DELAY_MS * (1L << (attempt - 1));
        Thread.sleep(delayMillis);
    }

    /*
    public static void main(String[] args) {

        try {
            String response = sendFlashRequest("gemini-3-flash-preview", System.getenv("GEMINI_API_KEY"),new GeminiFlash.GenerateContentRequest() {{
                system_instruction = new SystemInstruction();
                system_instruction.parts = List.of(new Part() {{
                    text = "say hello at every end of the message";
                }});

                contents = new Content();
                contents.parts = List.of(new Part() {{
                    text = "What is the capital of france";
                }});
            }});
            System.out.println("Response from Gemini API: " + response);
        } catch (IOException | InterruptedException | UnexpectedResponseException e) {
            e.printStackTrace();
        }
    }
    */
}
