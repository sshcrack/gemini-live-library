package me.sshcrack.gemini_live_lib.gson;

import org.jetbrains.annotations.Nullable;

import java.util.Base64;

public class RealtimeInput {
    @Nullable
    public Blob audio;

    @Nullable
    public String text;

    public static class Blob {
        public String data;
        public String mime_type;



        public Blob(String mimeType, byte[] data) {
            this.mime_type = mimeType;
            this.data = Base64.getEncoder().encodeToString(data);

        }
    }
}
