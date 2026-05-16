package me.mcgg.azreyzaako.mcggrtp.velocity.manager;

import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import me.mcgg.azreyzaako.mcggrtp.common.PendingRtp;

public final class PendingRtpManager {
    private final Clock clock;
    private final int expireSeconds;
    private final Map<UUID, PendingRtp> pending = new ConcurrentHashMap<>();

    public PendingRtpManager(Clock clock, int expireSeconds) {
        this.clock = clock;
        this.expireSeconds = expireSeconds;
    }

    public void put(PendingRtp pendingRtp) {
        purgeExpired();
        pending.put(pendingRtp.playerUuid(), pendingRtp);
    }

    public Optional<PendingRtp> findFor(UUID playerUuid, String currentServer) {
        purgeExpired();
        PendingRtp pendingRtp = pending.get(playerUuid);
        if (pendingRtp == null || !pendingRtp.targetServer().equalsIgnoreCase(currentServer)) {
            return Optional.empty();
        }
        return Optional.of(pendingRtp);
    }

    public void clear(UUID playerUuid, String requestId) {
        pending.computeIfPresent(playerUuid, (uuid, existing) -> existing.requestId().equals(requestId) ? null : existing);
    }

    private void purgeExpired() {
        long now = clock.millis();
        pending.entrySet().removeIf(entry -> (now - entry.getValue().createdAt()) > expireSeconds * 1000L);
    }
}
