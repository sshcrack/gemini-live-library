package me.sshcrack.gemini_live_lib.provider;

import me.sshcrack.gemini_live_lib.GeminiLiveLib;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class GeminiConnectionPool {
    private final int maxConcurrent;
    private final Map<UUID, SessionEntry> sessions = new ConcurrentHashMap<>();

    public GeminiConnectionPool(int maxConcurrent) {
        this.maxConcurrent = Math.max(1, maxConcurrent);
    }

    public synchronized boolean tryAcquire(UUID sessionId, GeminiLiveSession session, int priority) {
        if (sessions.containsKey(sessionId)) {
            GeminiLiveLib.LOGGER.warn("[ConnectionPool] Session {} already exists", sessionId);
            return false;
        }

        if (sessions.size() < maxConcurrent) {
            sessions.put(sessionId, new SessionEntry(session, priority, null));
            return true;
        }

        UUID evict = findLowestPrioritySession(priority);
        if (evict != null) {
            SessionEntry entry = sessions.remove(evict);
            if (entry.onEvicted != null) {
                entry.onEvicted.run();
            }
            sessions.put(sessionId, new SessionEntry(session, priority, null));
            GeminiLiveLib.LOGGER.info("[ConnectionPool] Evicted session {} for new session {}", evict, sessionId);
            return true;
        }

        return false;
    }

    public synchronized void release(UUID sessionId) {
        SessionEntry entry = sessions.remove(sessionId);
        if (entry != null && entry.session != null) {
            entry.session.close();
        }
    }

    public synchronized void onEvicted(UUID sessionId, Runnable handler) {
        SessionEntry entry = sessions.get(sessionId);
        if (entry != null) {
            sessions.put(sessionId, new SessionEntry(entry.session, entry.priority, handler));
        }
    }

    @Nullable
    private UUID findLowestPrioritySession(int minPriority) {
        UUID victim = null;
        int lowestPriority = Integer.MAX_VALUE;
        for (Map.Entry<UUID, SessionEntry> e : sessions.entrySet()) {
            if (e.getValue().priority < lowestPriority) {
                lowestPriority = e.getValue().priority;
                victim = e.getKey();
            }
        }
        if (victim != null && lowestPriority < minPriority) {
            return victim;
        }
        return null;
    }

    public int getActiveCount() {
        return sessions.size();
    }

    public int getMaxConcurrent() {
        return maxConcurrent;
    }

    public void shutdown() {
        for (SessionEntry entry : sessions.values()) {
            if (entry.session != null) {
                entry.session.close();
            }
        }
        sessions.clear();
    }

    private record SessionEntry(GeminiLiveSession session, int priority, @Nullable Runnable onEvicted) {}
}
