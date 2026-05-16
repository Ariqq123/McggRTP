package me.mcgg.azreyzaako.mcggrtp.common;

public record PendingRtpResponse(
        boolean hasPending,
        String requestId,
        String targetWorld,
        String dimension
) {
    public static PendingRtpResponse empty() {
        return new PendingRtpResponse(false, "", "", "");
    }
}
