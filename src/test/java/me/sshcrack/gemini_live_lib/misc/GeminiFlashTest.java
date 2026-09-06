package me.sshcrack.gemini_live_lib.misc;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeminiFlashTest {
    @Test
    void simpleTextRequestOmitsStructuredOutputConfiguration() {
        var request = GeminiFlash.createSimpleRequest("system", "prompt", null);
        JsonObject json = JsonParser.parseString(GeminiFlash.serializeGenerateContentRequest(request)).getAsJsonObject();

        assertFalse(json.has("generationConfig"));
        assertEquals("system", json.getAsJsonObject("system_instruction")
                .getAsJsonArray("parts").get(0).getAsJsonObject().get("text").getAsString());
        assertEquals("prompt", json.getAsJsonObject("contents")
                .getAsJsonArray("parts").get(0).getAsJsonObject().get("text").getAsString());
    }

    @Test
    void jsonConfigSerializesUsingGenerateContentWireFields() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        var config = GeminiFlash.GenerateContentRequest.GenerationConfig.json(schema);
        var request = GeminiFlash.createSimpleRequest("system", "prompt", config);

        JsonObject json = JsonParser.parseString(GeminiFlash.serializeGenerateContentRequest(request)).getAsJsonObject();
        JsonObject generationConfig = json.getAsJsonObject("generationConfig");

        assertEquals("application/json", generationConfig.get("responseMimeType").getAsString());
        assertTrue(generationConfig.has("responseJsonSchema"));
        assertEquals("object", generationConfig.getAsJsonObject("responseJsonSchema").get("type").getAsString());
    }

    @Test
    void jsonConfigRejectsNullSchema() {
        assertThrows(IllegalArgumentException.class,
                () -> GeminiFlash.GenerateContentRequest.GenerationConfig.json(null));
    }
}
