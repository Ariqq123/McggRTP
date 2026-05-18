package me.mcgg.azreyzaako.mcggrtp.paper.config;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.Sound;
import org.bukkit.Material;
import org.bukkit.block.Biome;

public record PaperConfig(
        DebugSettings debug,
        GuiSettings gui,
        ServerMenuSettings serverMenu,
        SoundSettings sounds,
        Map<String, DimensionOption> dimensions,
        NetworkSettings network,
        int cooldownSeconds,
        int maxConcurrentSearches,
        AdaptiveThrottleSettings adaptiveThrottle,
        Map<String, WorldRtpSettings> worlds
) {
    public record DebugSettings(boolean enabled) {
    }

    public record GuiSettings(String title, int size, boolean fillerEnabled, Material fillerMaterial, String fillerName) {
    }

    public record ServerMenuSettings(String title, int size, Material onlineMaterial, Material offlineMaterial) {
    }

    public record SoundSettings(
            Sound menuOpen,
            Sound menuClick,
            Sound denied,
            Sound teleportSuccess
    ) {
    }

    public record DimensionOption(
            String key,
            int slot,
            String displayName,
            Material material,
            String worldName,
            String permission,
            int warmupSeconds,
            List<String> lore
    ) {
    }

    public record NetworkSettings(
            String currentServer,
            String serverPermissionPrefix,
            Map<String, List<String>> dimensions,
            Map<String, NetworkServer> servers
    ) {
    }

    public record NetworkServer(String id, String displayName, String permission) {
    }

    public record AdaptiveThrottleSettings(
            boolean enabled,
            int minConcurrentSearches,
            double minTps,
            double maxMspt,
            int queueStartDelayTicks,
            int metricsLogInterval
    ) {
    }

    public record WorldRtpSettings(
            boolean enabled,
            int centerX,
            int centerZ,
            int minRadius,
            int maxRadius,
            int maxAttempts,
            boolean avoidBedrockRoof,
            Set<Biome> blacklistedBiomes,
            Set<Material> unsafeBlocks
    ) {
    }
}
