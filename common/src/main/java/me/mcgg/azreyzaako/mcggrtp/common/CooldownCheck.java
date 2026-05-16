package me.mcgg.azreyzaako.mcggrtp.common;

import java.util.UUID;

public record CooldownCheck(
        String requestId,
        UUID playerUuid
) {
}
