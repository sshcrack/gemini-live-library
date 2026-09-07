package me.sshcrack.gemini_live_lib;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.sshcrack.gemini_live_lib.gson.BidiGenerateContentSetup;
import me.sshcrack.gemini_live_lib.gson.ClientMessages;
import me.sshcrack.gemini_live_lib.gson.RealtimeInput;
import me.sshcrack.gemini_live_lib.websocket.WebSocket;
import me.sshcrack.gemini_live_lib.websocket.handshake.ClientHandshake;
import me.sshcrack.gemini_live_lib.websocket.server.WebSocketServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Real localhost WebSocket frames, using the production client and server transports. */
class GeminiLiveWebSocketTest {
    @Test
    void reconnectDoesNotInvokeApplicationShutdown() throws Exception {
        try (Loopback server = new Loopback()) {
            AtomicInteger applicationCloses = new AtomicInteger();
            LocalClient client = new LocalClient(server.uri()) {
                @Override public void close() { applicationCloses.incrementAndGet(); super.close(); }
            };
            try {
                client.connect();
                assertTrue(client.setup.await(2, TimeUnit.SECONDS));
                server.getConnections().iterator().next().close(1012, "restart");
                assertTrue(client.closed.await(2, TimeUnit.SECONDS));
                assertTrue(client.reconnectBlocking(2, TimeUnit.SECONDS));
                assertEquals(0, applicationCloses.get(), "Transport reset must not close mod audio/session ownership");
            } finally { client.closeBlocking(); }
        }
    }

    @Test
    void silentHttpHandshakeIsAbortedWithoutLeakingAnOpenConnection() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            server.setSoTimeout(2000);
            LocalClient client = new LocalClient(URI.create("ws://127.0.0.1:" + server.getLocalPort()), 200);
            try {
                client.connect();
                try (var accepted = server.accept()) {
                    assertTrue(client.closed.await(2, TimeUnit.SECONDS), "Silent handshake exceeded startup deadline");
                    assertFalse(client.isOpen());
                    assertFalse(client.isSetupComplete());
                }
            } finally { client.closeBlocking(); }
        }
    }

    @Test
    void responsiveWebSocketWithoutSetupAcknowledgementIsAborted() throws Exception {
        try (Loopback server = new Loopback(false)) {
            LocalClient client = new LocalClient(server.uri(), 200);
            try {
                client.connect();
                assertTrue(client.closed.await(2, TimeUnit.SECONDS), "Missing setupComplete exceeded startup deadline");
                assertFalse(client.isOpen());
                assertFalse(client.isSetupComplete());
            } finally { client.closeBlocking(); }
        }
    }

    @Test
    void setupConstructionFailureClosesTheTransport() throws Exception {
        try (Loopback server = new Loopback()) {
            LocalClient client = new LocalClient(server.uri()) {
                @Override public BidiGenerateContentSetup getSetup() {
                    throw new IllegalStateException("injected prompt construction failure");
                }
            };
            try {
                client.connect();
                assertTrue(client.closed.await(2, TimeUnit.SECONDS));
                assertFalse(client.isOpen());
                assertTrue(client.error.get() instanceof IllegalStateException);
            } finally { client.closeBlocking(); }
        }
    }

    @Test
    void setupBinaryAudioToolResponseAndTurnCompleteRoundTrip() throws Exception {
        try (Loopback server = new Loopback()) {
            LocalClient client = new LocalClient(server.uri());
            try {
                client.connect();
                assertTrue(client.setup.await(2, TimeUnit.SECONDS), "setup acknowledgement missing");
                assertTrue(client.isSetupComplete());
                var input = new RealtimeInput();
                input.text = "hello";
                client.send(ClientMessages.input(input));
                assertTrue(client.turn.await(2, TimeUnit.SECONDS), "turnComplete missing");
                assertTrue(server.toolResponse.await(2, TimeUnit.SECONDS), "tool response missing");
                assertEquals(2, client.audioBytes.get());
                assertNull(client.error.get());
                assertNull(server.error.get());
            } finally {
                client.closeBlocking();
            }
        }
    }

    @Test
    void remoteCloseInvalidatesSetupReadiness() throws Exception {
        try (Loopback server = new Loopback()) {
            LocalClient client = new LocalClient(server.uri());
            try {
                client.connect();
                assertTrue(client.setup.await(2, TimeUnit.SECONDS));
                server.getConnections().iterator().next().close(1000, "session ended");
                assertTrue(client.closed.await(2, TimeUnit.SECONDS));
                assertFalse(client.isSetupComplete(), "Closed WebSocket still reports Gemini setup ready");
            } finally {
                client.closeBlocking();
            }
        }
    }

    private static class LocalClient extends GeminiLiveClient {
        final CountDownLatch setup = new CountDownLatch(1);
        final CountDownLatch turn = new CountDownLatch(1);
        final CountDownLatch closed = new CountDownLatch(1);
        final AtomicInteger audioBytes = new AtomicInteger();
        final AtomicReference<Exception> error = new AtomicReference<>();

        LocalClient(URI endpoint) { this(endpoint, 2000); }
        LocalClient(URI endpoint, int timeout) { super("unused-local-key", timeout); this.uri = endpoint; setDaemon(true); }
        @Override public BidiGenerateContentSetup getSetup() {
            var result = new BidiGenerateContentSetup("models/local-test");
            result.generationConfig.responseModalities = List.of("AUDIO");
            return result;
        }
        @Override public void onSetupComplete() { setup.countDown(); }
        @Override public void onTurnComplete() { turn.countDown(); }
        @Override public void onGeneratedAudio(byte[] data, int rate) { audioBytes.addAndGet(data.length); }
        @Override public JsonObject onFunctionCall(String name, JsonObject args) {
            var result = new JsonObject();
            result.addProperty("ok", true);
            return result;
        }
        @Override public void addPromptAudio(short[] audio) { }
        @Override public void onError(Exception exception) { error.set(exception); }
        @Override public void onClose(int code, String reason, boolean remote) {
            super.onClose(code, reason, remote);
            closed.countDown();
        }
    }

    private static class Loopback extends WebSocketServer implements AutoCloseable {
        final CountDownLatch started = new CountDownLatch(1);
        final CountDownLatch toolResponse = new CountDownLatch(1);
        final AtomicReference<Exception> error = new AtomicReference<>();
        private final boolean acknowledgeSetup;

        Loopback() throws InterruptedException {
            this(true);
        }
        Loopback(boolean acknowledgeSetup) throws InterruptedException {
            super(new InetSocketAddress("127.0.0.1", 0), 1);
            this.acknowledgeSetup = acknowledgeSetup;
            setDaemon(true);
            start();
            assertTrue(started.await(2, TimeUnit.SECONDS), "local server did not start");
        }
        URI uri() { return URI.create("ws://127.0.0.1:" + getPort()); }
        @Override public void onStart() { started.countDown(); }
        @Override public void onOpen(WebSocket socket, ClientHandshake handshake) { }
        @Override public void onClose(WebSocket socket, int code, String reason, boolean remote) { }
        @Override public void onError(WebSocket socket, Exception exception) { error.set(exception); }
        @Override public void onMessage(WebSocket socket, String text) {
            JsonObject message = JsonParser.parseString(text).getAsJsonObject();
            if (message.has("setup")) {
                if (acknowledgeSetup) socket.send("{\"setupComplete\":{}}".getBytes(StandardCharsets.UTF_8));
            } else if (message.has("realtimeInput") || message.has("realtime_input")) {
                socket.send("{\"serverContent\":{\"modelTurn\":{\"parts\":[{\"inlineData\":{"
                        + "\"mimeType\":\"audio/pcm;rate=24000\",\"data\":\"AAA=\"}}]}}}");
                socket.send("{\"toolCall\":{\"functionCalls\":[{\"id\":\"call-1\",\"name\":\"test_tool\",\"args\":{}}]}}");
                socket.send("{\"serverContent\":{\"generationComplete\":true}}");
                socket.send("{\"serverContent\":{\"turnComplete\":true}}");
            } else if (message.has("toolResponse") || message.has("tool_response")) {
                String field = message.has("toolResponse") ? "toolResponse" : "tool_response";
                var response = message.getAsJsonObject(field).getAsJsonArray("functionResponses").get(0).getAsJsonObject();
                if (response.get("id").getAsString().equals("call-1")
                        && response.getAsJsonObject("response").get("ok").getAsBoolean()) {
                    toolResponse.countDown();
                }
            }
        }
        @Override public void close() throws InterruptedException { stop(1000); }
    }
}
