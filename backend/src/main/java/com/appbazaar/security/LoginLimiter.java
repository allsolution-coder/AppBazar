package com.appbazaar.security;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** Slows down password guessing: too many wrong passwords for one email blocks that email for a while. */
@Component
public class LoginLimiter {
    private static final int MAX_FAILS = 10;
    private static final long WINDOW_SECONDS = 900; // 15 minutes

    private record Fails(int count, Instant since) {}

    private final ConcurrentHashMap<String, Fails> fails = new ConcurrentHashMap<>();

    public boolean blocked(String key) {
        Fails f = fails.get(key);
        if (f == null) return false;
        if (f.since().plusSeconds(WINDOW_SECONDS).isBefore(Instant.now())) {
            fails.remove(key);
            return false;
        }
        return f.count() >= MAX_FAILS;
    }

    public void fail(String key) {
        if (fails.size() > 10_000) fails.clear(); // keep memory bounded
        Instant now = Instant.now();
        fails.merge(key, new Fails(1, now), (old, fresh) ->
                old.since().plusSeconds(WINDOW_SECONDS).isBefore(now) ? fresh : new Fails(old.count() + 1, old.since()));
    }

    public void clear(String key) { fails.remove(key); }
}
