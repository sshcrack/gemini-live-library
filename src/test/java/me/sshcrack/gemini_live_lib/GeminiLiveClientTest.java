package me.sshcrack.gemini_live_lib;

import me.sshcrack.gemini_live_lib.gson.BidiGenerateContentSetup;
import org.junit.jupiter.api.Test;

import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeminiLiveClientTest {
    @Test
    void connectionAttemptHasAFiniteSocketTimeout() throws Exception {
        RecordingClient client = new RecordingClient();
        AtomicInteger timeout = new AtomicInteger(-1);
        CountDownLatch attempted = new CountDownLatch(1);
        client.setSocket(new Socket() {
            @Override
            public void connect(SocketAddress endpoint, int timeoutMillis) throws SocketTimeoutException {
                timeout.set(timeoutMillis);
                attempted.countDown();
                throw new SocketTimeoutException("Simulated unreachable endpoint");
            }
        });
        try {
            client.connect();
            assertTrue(attempted.await(2, TimeUnit.SECONDS));
            assertTrue(timeout.get() > 0,
                    "CONNECTING must have a finite deadline; Socket.connect(..., 0) can wait indefinitely");
        } finally {
            client.closeBlocking();
        }
    }

    @Test
    void closeClearsSetupReadiness() {
        RecordingClient client = readyClient();
        client.onClose(1006, "lost transport", true);
        assertFalse(client.isSetupComplete(), "A disconnected session must not remain ready");
    }

    @Test
    void newHandshakeRequiresANewSetupAcknowledgement() {
        RecordingClient client = readyClient();
        client.onOpen(null);
        assertFalse(client.isSetupComplete(), "Reconnection must wait for its own setupComplete");
    }

    @Test
    void combinedCompletionFlagsDeliverBothCallbacks() {
        RecordingClient client = readyClient();
        client.onMessage("{\"serverContent\":{\"generationComplete\":true,\"turnComplete\":true}}");
        assertEquals(List.of("generation", "turn"), client.events);
    }

    @Test
    void finalContentIsDeliveredBeforeCompletion() {
        RecordingClient client = readyClient();
        client.onMessage("{\"serverContent\":{\"modelTurn\":{\"parts\":[{\"text\":\"hello\"}]},"
                + "\"outputTranscription\":{\"text\":\"hello\"},\"generationComplete\":true,\"turnComplete\":true}}");
        assertTrue(client.events.contains("text:hello"));
        assertTrue(client.events.contains("transcript:hello"));
        assertEquals(List.of("generation", "turn"), client.events.subList(client.events.size() - 2, client.events.size()));
    }

    @Test
    void allAudioPartsAreDelivered() {
        RecordingClient client = readyClient();
        String part = "{\"inlineData\":{\"mimeType\":\"audio/pcm;rate=24000\",\"data\":\"AAA=\"}}";
        client.onMessage("{\"serverContent\":{\"modelTurn\":{\"parts\":[" + part + "," + part + "]}}}");
        assertEquals(List.of("audio", "audio"), client.events);
    }

    @Test
    void binaryMessagesRespectBufferBoundsAndSupportDirectBuffers() {
        RecordingClient client = new RecordingClient();
        byte[] json = "{\"setupComplete\":{}}".getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocateDirect(json.length + 4);
        buffer.putInt(42).put(json).flip().position(4);
        assertDoesNotThrow(() -> client.onMessage(buffer));
        assertTrue(client.isSetupComplete());
    }

    private static RecordingClient readyClient() {
        RecordingClient client = new RecordingClient();
        client.onMessage("{\"setupComplete\":{}}");
        return client;
    }

    private static class RecordingClient extends GeminiLiveClient {
        final List<String> events = new ArrayList<>();

        RecordingClient() { super("unused-offline-test-key"); }
        @Override public BidiGenerateContentSetup getSetup() { return new BidiGenerateContentSetup("models/test"); }
        @Override public void send(String text) { }
        @Override public void onError(Exception error) { }
        @Override public void addPromptAudio(short[] audio) { }
        @Override public void onGenerationComplete() { events.add("generation"); }
        @Override public void onTurnComplete() { events.add("turn"); }
        @Override public void onGeneratedText(String text) { events.add("text:" + text); }
        @Override public void onOutputTranscription(String text) { events.add("transcript:" + text); }
        @Override public void onGeneratedAudio(byte[] data, int rate) { events.add("audio"); }
    }
}
