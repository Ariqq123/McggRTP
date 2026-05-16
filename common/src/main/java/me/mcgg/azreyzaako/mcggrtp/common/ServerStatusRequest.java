package me.mcgg.azreyzaako.mcggrtp.common;

import java.util.UUID;

public record ServerStatusRequest(
        String requestId,
        UUID playerUuid,
        String dimension
) {
}
