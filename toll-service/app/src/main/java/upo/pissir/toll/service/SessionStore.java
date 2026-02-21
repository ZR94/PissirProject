package upo.pissir.toll.service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SessionStore {

    public record Session(
            String passId,
            String channel,          // manual|telepass
            String entryTollboothId,
            String plate,
            Instant entryAt
    ) {}

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    public void put(Session s) {
        sessions.put(s.passId(), s);
    }

    public Session get(String passId) {
        return sessions.get(passId);
    }

    public void remove(String passId) {
        sessions.remove(passId);
    }
}
