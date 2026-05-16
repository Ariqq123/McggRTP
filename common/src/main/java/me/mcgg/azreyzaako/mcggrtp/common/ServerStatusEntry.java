package me.mcgg.azreyzaako.mcggrtp.common;

public record ServerStatusEntry(
        String serverId,
        String displayName,
        boolean online,
        int playerCount
) {
}
