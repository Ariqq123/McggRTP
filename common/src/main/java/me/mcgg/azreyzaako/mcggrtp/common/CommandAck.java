package me.mcgg.azreyzaako.mcggrtp.common;

public record CommandAck(
        String requestId,
        boolean success,
        String message
) {
}
