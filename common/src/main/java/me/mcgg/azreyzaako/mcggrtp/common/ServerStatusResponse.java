package me.mcgg.azreyzaako.mcggrtp.common;

import java.util.List;

public record ServerStatusResponse(
        String requestId,
        String dimension,
        List<ServerStatusEntry> entries
) {
}
