package me.mcgg.azreyzaako.mcggrtp.velocity.manager;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CooldownManager {
    private final Clock clock;
    private final boolean enabled;
    private final long defaultCooldownMillis;
    private final long cleanupIntervalMillis;
    private final Map<UUID, Long> lastUse = new ConcurrentHashMap<>();
    private volatile long nextCleanupAtMillis;

    public CooldownManager(Clock clock, boolean enabled, int defaultCooldownSeconds) {
        this.clock = clock;
        this.enabled = enabled;
        this.defaultCooldownMillis = defaultCooldownSeconds * 1000L;
        this.cleanupIntervalMillis = Math.max(1_000L, Math.min(this.defaultCooldownMillis, 30_000L));
    }

    public CooldownState getState(UUID playerUuid) {
        if (!enabled) {
            return new CooldownState(false, 0);
        }

        long now = clock.millis();
        Long previous = lastUse.get(playerUuid);
        if (previous == null) {
            purgeExpiredIfDue(now);
            return new CooldownState(false, 0);
        }

        long remainingMillis = (previous + defaultCooldownMillis) - now;
        if (remainingMillis <= 0) {
            lastUse.remove(playerUuid, previous);
            purgeExpiredIfDue(now);
            return new CooldownState(false, 0);
        }

        purgeExpiredIfDue(now);
        long seconds = Math.max(1L, (remainingMillis + 999L) / 1000L);
        return new CooldownState(true, seconds);
    }

    public void markUsed(UUID playerUuid) {
        if (enabled) {
            long now = clock.millis();
            purgeExpiredIfDue(now);
            lastUse.put(playerUuid, now);
        }
    }

    int trackedCount() {
        return lastUse.size();
    }

    private void purgeExpiredIfDue(long now) {
        if (!enabled || now < nextCleanupAtMillis) {
            return;
        }
        nextCleanupAtMillis = now + cleanupIntervalMillis;
        lastUse.entrySet().removeIf(entry -> (entry.getValue() + defaultCooldownMillis) <= now);
    }

    public record CooldownState(boolean active, long remainingSeconds) {
    }
}
