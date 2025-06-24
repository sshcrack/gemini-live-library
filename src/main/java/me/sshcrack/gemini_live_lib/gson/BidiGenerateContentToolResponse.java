package me.sshcrack.gemini_live_lib.gson;

import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

public class BidiGenerateContentToolResponse {
    public final List<FunctionResponse> functionResponses = new ArrayList<>();

    public record FunctionResponse(String id, String name, JsonObject response) {
    }
}
