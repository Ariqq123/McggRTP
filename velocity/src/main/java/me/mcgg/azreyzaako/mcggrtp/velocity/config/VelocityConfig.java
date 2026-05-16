package me.mcgg.azreyzaako.mcggrtp.velocity.config;

import java.util.List;
import java.util.Map;

public record VelocityConfig(
        String channel,
        int pendingExpireSeconds,
        boolean cooldownEnabled,
        int defaultCooldownSeconds,
        String bypassPermission,
        String serverPermissionPrefix,
        Map<String, NetworkServer> servers,
        Map<String, List<String>> dimensions
) {
    public record NetworkServer(
            String id,
            String displayName,
            boolean enabled,
            String permission
    ) {
    }
}
