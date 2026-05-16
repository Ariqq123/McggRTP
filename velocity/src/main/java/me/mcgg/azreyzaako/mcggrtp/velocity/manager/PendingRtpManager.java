package me.mcgg.azreyzaako.mcggrtp.velocity.manager;

import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import me.mcgg.azreyzaako.mcggrtp.common.PendingRtp;

public final class PendingRtpManager {
    private final Clock clock;
    private final long expireMillis;
    private final long cleanupIntervalMillis;
    private final Map<UUID, PendingRtp> pending = new ConcurrentHashMap<>();
    private volatile long nextPurgeAtMillis;

    public PendingRtpManager(Clock clock, int expireSeconds) {
        this.clock = clock;
        this.expireMillis = expireSeconds * 1000L;
        this.cleanupIntervalMillis = Math.max(1_000L, Math.min(this.expireMillis, 5_000L));
    }

    public void put(PendingRtp pendingRtp) {
        purgeExpiredIfDue(clock.millis());
        pending.put(pendingRtp.playerUuid(), pendingRtp);
    }

    public Optional<PendingRtp> findFor(UUID playerUuid, String currentServer) {
        long now = clock.millis();
        PendingRtp pendingRtp = pending.get(playerUuid);
        if (pendingRtp == null) {
            purgeExpiredIfDue(now);
            return Optional.empty();
        }
        if (isExpired(pendingRtp, now)) {
            pending.remove(playerUuid, pendingRtp);
            purgeExpiredIfDue(now);
            return Optional.empty();
        }
        if (!pendingRtp.targetServer().equalsIgnoreCase(currentServer)) {
            purgeExpiredIfDue(now);
            return Optional.empty();
        }
        purgeExpiredIfDue(now);
        return Optional.of(pendingRtp);
    }

    public void clear(UUID playerUuid, String requestId) {
        pending.computeIfPresent(playerUuid, (uuid, existing) -> existing.requestId().equals(requestId) ? null : existing);
    }

    int trackedCount() {
        return pending.size();
    }

    private void purgeExpiredIfDue(long now) {
        if (now < nextPurgeAtMillis) {
            return;
        }
        nextPurgeAtMillis = now + cleanupIntervalMillis;
        pending.entrySet().removeIf(entry -> isExpired(entry.getValue(), now));
    }

    private boolean isExpired(PendingRtp pendingRtp, long now) {
        return (now - pendingRtp.createdAt()) > expireMillis;
    }
}
