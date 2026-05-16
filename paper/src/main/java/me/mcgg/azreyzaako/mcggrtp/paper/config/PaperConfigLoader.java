package me.mcgg.azreyzaako.mcggrtp.paper.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import me.mcgg.azreyzaako.mcggrtp.paper.McggRTPPaper;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Biome;
import org.bukkit.configuration.ConfigurationSection;

public final class PaperConfigLoader {
    private final McggRTPPaper plugin;

    public PaperConfigLoader(McggRTPPaper plugin) {
        this.plugin = plugin;
    }

    public PaperConfig load() {
        plugin.reloadConfig();
        ConfigurationSection root = plugin.getConfig();
        ConfigurationSection debug = root.getConfigurationSection("debug");

        ConfigurationSection gui = root.getConfigurationSection("gui");
        ConfigurationSection filler = gui == null ? null : gui.getConfigurationSection("filler");
        PaperConfig.GuiSettings guiSettings = new PaperConfig.GuiSettings(
                string(gui, "title", "&8Random Teleport"),
                integer(gui, "size", 27),
                filler != null && filler.getBoolean("enabled", true),
                material(filler, "material", Material.BLACK_STAINED_GLASS_PANE),
                string(filler, "name", " ")
        );

        ConfigurationSection serverMenu = root.getConfigurationSection("server-menu");
        PaperConfig.ServerMenuSettings serverMenuSettings = new PaperConfig.ServerMenuSettings(
                string(serverMenu, "title", "&8Choose Server: {dimension}"),
                integer(serverMenu, "size", 27),
                material(serverMenu, "online-material", Material.LIME_WOOL),
                material(serverMenu, "offline-material", Material.BARRIER)
        );
        ConfigurationSection sounds = root.getConfigurationSection("sounds");
        PaperConfig.SoundSettings soundSettings = new PaperConfig.SoundSettings(
                sound(sounds, "menu-open", "BLOCK_NOTE_BLOCK_PLING"),
                sound(sounds, "menu-click", "BLOCK_NOTE_BLOCK_PLING"),
                sound(sounds, "denied", "ENTITY_VILLAGER_NO"),
                sound(sounds, "teleport-success", "ENTITY_ENDERMAN_TELEPORT")
        );

        Map<String, PaperConfig.DimensionOption> dimensions = new LinkedHashMap<>();
        ConfigurationSection mainMenu = root.getConfigurationSection("main-menu");
        if (mainMenu != null) {
            for (String key : mainMenu.getKeys(false)) {
                ConfigurationSection section = mainMenu.getConfigurationSection(key);
                if (section == null) {
                    continue;
                }
                dimensions.put(key, new PaperConfig.DimensionOption(
                        key,
                        section.getInt("slot"),
                        section.getString("display-name", key),
                        Material.matchMaterial(section.getString("material", "STONE")),
                        section.getString("world-name", "world"),
                        section.getString("permission", ""),
                        section.getInt("warmup-seconds", 0),
                        section.getStringList("lore")
                ));
            }
        }

        ConfigurationSection network = root.getConfigurationSection("network");
        String serverPermissionPrefix = network == null
                ? "mcggrtp.server."
                : network.getString("server-permission-prefix", "mcggrtp.server.");
        Map<String, List<String>> networkDimensions = new LinkedHashMap<>();
        ConfigurationSection networkDimensionSection = network == null ? null : network.getConfigurationSection("dimensions");
        if (networkDimensionSection != null) {
            for (String key : networkDimensionSection.getKeys(false)) {
                ConfigurationSection section = networkDimensionSection.getConfigurationSection(key);
                if (section != null) {
                    networkDimensions.put(key, List.copyOf(section.getStringList("servers")));
                }
            }
        }

        Map<String, PaperConfig.NetworkServer> networkServers = new LinkedHashMap<>();
        ConfigurationSection networkServersSection = network == null ? null : network.getConfigurationSection("servers");
        if (networkServersSection != null) {
            for (String key : networkServersSection.getKeys(false)) {
                ConfigurationSection section = networkServersSection.getConfigurationSection(key);
                if (section != null) {
                    networkServers.put(key, new PaperConfig.NetworkServer(
                            key,
                            section.getString("display-name", key),
                            serverPermission(serverPermissionPrefix, key, section.getString("permission", ""))
                    ));
                }
            }
        }

        Map<String, PaperConfig.WorldRtpSettings> worlds = new LinkedHashMap<>();
        ConfigurationSection rtp = root.getConfigurationSection("rtp");
        ConfigurationSection worldsSection = rtp == null ? null : rtp.getConfigurationSection("worlds");
        if (worldsSection != null) {
            for (String key : worldsSection.getKeys(false)) {
                ConfigurationSection section = worldsSection.getConfigurationSection(key);
                if (section == null) {
                    continue;
                }

                worlds.put(key, new PaperConfig.WorldRtpSettings(
                        section.getBoolean("enabled", true),
                        section.getInt("center-x"),
                        section.getInt("center-z"),
                        section.getInt("min-radius", 0),
                        section.getInt("max-radius", 2000),
                        section.getInt("max-attempts", 32),
                        section.getBoolean("avoid-bedrock-roof", false),
                        section.getStringList("blacklisted-biomes").stream()
                                .map(this::biome)
                                .collect(Collectors.toUnmodifiableSet()),
                        section.getStringList("unsafe-blocks").stream()
                                .map(String::toUpperCase)
                                .map(Material::valueOf)
                                .collect(Collectors.toUnmodifiableSet())
                ));
            }
        }

        return new PaperConfig(
                new PaperConfig.DebugSettings(debug != null && debug.getBoolean("enabled", false)),
                guiSettings,
                serverMenuSettings,
                soundSettings,
                Map.copyOf(dimensions),
                new PaperConfig.NetworkSettings(
                        network == null ? "survival-1" : network.getString("current-server", "survival-1"),
                        serverPermissionPrefix,
                        Map.copyOf(networkDimensions),
                        Map.copyOf(networkServers)
                ),
                rtp == null ? 300 : rtp.getInt("cooldown-seconds", 300),
                Map.copyOf(worlds)
        );
    }

    private String string(ConfigurationSection section, String key, String fallback) {
        return section == null ? fallback : section.getString(key, fallback);
    }

    private int integer(ConfigurationSection section, String key, int fallback) {
        return section == null ? fallback : section.getInt(key, fallback);
    }

    private Material material(ConfigurationSection section, String key, Material fallback) {
        if (section == null) {
            return fallback;
        }
        Material material = Material.matchMaterial(section.getString(key, fallback.name()));
        return material == null ? fallback : material;
    }

    private Sound sound(ConfigurationSection section, String key, String fallback) {
        String value = section == null ? fallback : section.getString(key, fallback);
        Sound resolved = soundByField(value);
        if (resolved != null) {
            return resolved;
        }

        Sound sound = Registry.SOUNDS.match(value);
        if (sound != null) {
            return sound;
        }

        Sound fallbackSound = soundByField(fallback);
        if (fallbackSound != null) {
            return fallbackSound;
        }
        return Registry.SOUNDS.getOrThrow(NamespacedKey.minecraft(fallback.toLowerCase()));
    }

    private Biome biome(String value) {
        Biome biome = Registry.BIOME.match(value);
        if (biome != null) {
            return biome;
        }
        return Registry.BIOME.getOrThrow(NamespacedKey.minecraft(value.toLowerCase()));
    }

    private Sound soundByField(String name) {
        try {
            return (Sound) Sound.class.getField(name).get(null);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private String serverPermission(String prefix, String serverId, String configuredPermission) {
        if (configuredPermission != null && !configuredPermission.isBlank()) {
            return configuredPermission;
        }
        return prefix + normalizePermissionKey(serverId);
    }

    private String normalizePermissionKey(String value) {
        String normalized = value.toLowerCase().replaceAll("[^a-z0-9._-]", "-");
        return normalized.isBlank() ? "unknown" : normalized;
    }
}
