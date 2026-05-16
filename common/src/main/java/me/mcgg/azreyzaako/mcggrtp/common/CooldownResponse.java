package me.mcgg.azreyzaako.mcggrtp.common;

public record CooldownResponse(
        String requestId,
        boolean active,
        long remainingSeconds
) {
}
