package me.mcgg.azreyzaako.mcggrtp.common;

import java.util.UUID;

public record PendingRtp(
        String requestId,
        UUID playerUuid,
        String targetServer,
        String targetWorld,
        String dimension,
        long createdAt
) {
}
