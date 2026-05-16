package me.mcgg.azreyzaako.mcggrtp.common;

import java.util.UUID;

public record RtpResult(
        String requestId,
        UUID playerUuid,
        boolean success,
        String reason
) {
}
