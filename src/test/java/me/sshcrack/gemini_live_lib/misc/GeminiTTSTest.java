package me.sshcrack.gemini_live_lib.misc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GeminiTTSTest {
    @Test
    void httpFailurePreservesStatusAndStructuredProviderBody() {
        String body = "{\"error\":{\"code\":400,\"message\":\"No matching speaker voice found: Achird\"}}";

        UnexpectedResponseException error = GeminiTTS.unexpectedHttpResponse(400, body);

        assertEquals(400, error.getStatusCode());
        assertEquals(body, error.getResponseBody());
        assertEquals("Gemini TTS request failed with HTTP 400", error.getMessage());
    }
}
