package me.mcgg.azreyzaako.mcggrtp.velocity.manager;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CooldownManager {
    private final Clock clock;
    private final boolean enabled;
    private final int defaultCooldownSeconds;
    private final Map<UUID, Long> lastUse = new ConcurrentHashMap<>();

    public CooldownManager(Clock clock, boolean enabled, int defaultCooldownSeconds) {
        this.clock = clock;
        this.enabled = enabled;
        this.defaultCooldownSeconds = defaultCooldownSeconds;
    }

    public CooldownState getState(UUID playerUuid) {
        if (!enabled) {
            return new CooldownState(false, 0);
        }

        long now = clock.millis();
        Long previous = lastUse.get(playerUuid);
        if (previous == null) {
            return new CooldownState(false, 0);
        }

        long remainingMillis = (previous + defaultCooldownSeconds * 1000L) - now;
        if (remainingMillis <= 0) {
            return new CooldownState(false, 0);
        }

        long seconds = Math.max(1L, (remainingMillis + 999L) / 1000L);
        return new CooldownState(true, seconds);
    }

    public void markUsed(UUID playerUuid) {
        if (enabled) {
            lastUse.put(playerUuid, clock.millis());
        }
    }

    public record CooldownState(boolean active, long remainingSeconds) {
    }
}
