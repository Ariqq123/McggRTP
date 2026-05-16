package me.mcgg.azreyzaako.mcggrtp.common;

import java.util.UUID;

public record ReloadConfigRequest(
        String requestId,
        UUID playerUuid
) {
}
