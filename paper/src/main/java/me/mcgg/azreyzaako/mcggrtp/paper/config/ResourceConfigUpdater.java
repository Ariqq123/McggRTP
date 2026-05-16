package me.mcgg.azreyzaako.mcggrtp.paper.config;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class ResourceConfigUpdater {
    private ResourceConfigUpdater() {
    }

    public static UpdateResult updateYamlResource(JavaPlugin plugin, String resourceName) {
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            throw new IllegalStateException("Could not create plugin data folder");
        }

        File targetFile = new File(dataFolder, resourceName);
        if (!targetFile.exists()) {
            plugin.saveResource(resourceName, false);
            return new UpdateResult(resourceName, true, 0, true);
        }

        YamlConfiguration existing = YamlConfiguration.loadConfiguration(targetFile);
        YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(
                Objects.requireNonNull(plugin.getResource(resourceName), () -> "Missing bundled resource: " + resourceName),
                StandardCharsets.UTF_8
        ));

        MergeStats stats = mergeMissing(existing, defaults, null);
        if (!stats.changed()) {
            return new UpdateResult(resourceName, false, 0, false);
        }

        try {
            existing.save(targetFile);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not update " + resourceName, exception);
        }
        return new UpdateResult(resourceName, false, stats.addedKeys(), true);
    }

    private static MergeStats mergeMissing(YamlConfiguration existing, ConfigurationSection defaults, String path) {
        boolean changed = false;
        int addedKeys = 0;
        for (String key : defaults.getKeys(false)) {
            String childPath = path == null ? key : path + "." + key;
            Object defaultValue = defaults.get(key);
            if (defaultValue instanceof ConfigurationSection defaultSection) {
                if (!existing.isConfigurationSection(childPath) && !existing.contains(childPath)) {
                    existing.createSection(childPath);
                    changed = true;
                    addedKeys++;
                }
                MergeStats nestedStats = mergeMissing(existing, defaultSection, childPath);
                changed |= nestedStats.changed();
                addedKeys += nestedStats.addedKeys();
                continue;
            }

            if (!existing.contains(childPath)) {
                existing.set(childPath, defaultValue);
                changed = true;
                addedKeys++;
            }
        }
        return new MergeStats(changed, addedKeys);
    }

    public record UpdateResult(String resourceName, boolean created, int addedKeys, boolean changed) {
    }

    private record MergeStats(boolean changed, int addedKeys) {
    }
}
