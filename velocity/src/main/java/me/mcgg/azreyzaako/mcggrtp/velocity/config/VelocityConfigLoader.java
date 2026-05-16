package me.mcgg.azreyzaako.mcggrtp.velocity.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

public final class VelocityConfigLoader {
    private static final String DEFAULT_CONFIG = """
            # McggRTP Velocity config
            # Notes:
            # - `plugin-message-channel` should stay `mcggrtp:main` unless the code
            #   is changed on both the Paper and Velocity plugins.
            # - Server ids like `survival-1` and `survival-2` must match:
            #   1. the names used in Velocity's `[servers]` block
            #   2. `network.current-server` on each Paper backend
            #
            settings:
              plugin-message-channel: "mcggrtp:main"
              pending-expire-seconds: 30

            cooldowns:
              enabled: true
              default-seconds: 300
              bypass-permission: "mcggrtp.bypass.cooldown"
              server-permission-prefix: "mcggrtp.server."

            servers:
              # If a server entry omits `permission`, McggRTP derives it as
              # `mcggrtp.server.<server-id>`.
              survival-1:
                display-name: "&aSurvival 1"
                enabled: true
              survival-2:
                display-name: "&aSurvival 2"
                enabled: true
              survival-3:
                display-name: "&aSurvival 3"
                enabled: true

            dimensions:
              overworld:
                servers: ["survival-1", "survival-2", "survival-3"]
              nether:
                servers: ["survival-1", "survival-2"]
              end:
                servers: ["survival-1"]
            """;

    private final Path dataDirectory;
    private final Logger logger;
    private final Yaml yaml;

    public VelocityConfigLoader(Path dataDirectory, Logger logger) {
        this.dataDirectory = dataDirectory;
        this.logger = logger;
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        this.yaml = new Yaml(options);
    }

    public VelocityConfig load() {
        try {
            Files.createDirectories(dataDirectory);
            Path configPath = dataDirectory.resolve("config.yml");
            if (Files.notExists(configPath)) {
                try (Writer writer = Files.newBufferedWriter(configPath)) {
                    writer.write(DEFAULT_CONFIG);
                }
            }

            try (InputStream inputStream = Files.newInputStream(configPath)) {
                Map<String, Object> root = yaml.load(inputStream);
                return parse(root);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load Velocity config", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private VelocityConfig parse(Map<String, Object> root) {
        Map<String, Object> settings = map(root, "settings");
        Map<String, Object> cooldowns = map(root, "cooldowns");
        String serverPermissionPrefix = string(cooldowns, "server-permission-prefix", "mcggrtp.server.");
        Map<String, Object> rawServers = map(root, "servers");
        Map<String, Object> rawDimensions = map(root, "dimensions");

        Map<String, VelocityConfig.NetworkServer> servers = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : rawServers.entrySet()) {
            Map<String, Object> section = (Map<String, Object>) entry.getValue();
            servers.put(entry.getKey(), new VelocityConfig.NetworkServer(
                    entry.getKey(),
                    string(section, "display-name"),
                    bool(section, "enabled", true),
                    serverPermission(serverPermissionPrefix, entry.getKey(), string(section, "permission"))
            ));
        }

        Map<String, List<String>> dimensions = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : rawDimensions.entrySet()) {
            Map<String, Object> section = (Map<String, Object>) entry.getValue();
            List<String> values = new ArrayList<>();
            for (Object value : (List<Object>) section.getOrDefault("servers", List.of())) {
                values.add(String.valueOf(value));
            }
            dimensions.put(entry.getKey(), List.copyOf(values));
        }

        VelocityConfig config = new VelocityConfig(
                string(settings, "plugin-message-channel"),
                integer(settings, "pending-expire-seconds", 30),
                bool(cooldowns, "enabled", true),
                integer(cooldowns, "default-seconds", 300),
                string(cooldowns, "bypass-permission"),
                serverPermissionPrefix,
                Map.copyOf(servers),
                Map.copyOf(dimensions)
        );
        logger.info("Loaded McggRTP Velocity config with {} servers", config.servers().size());
        return config;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Map<String, Object> root, String key) {
        Object value = root.getOrDefault(key, Map.of());
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private String string(Map<String, Object> section, String key) {
        return string(section, key, "");
    }

    private String string(Map<String, Object> section, String key, String fallback) {
        Object value = section.getOrDefault(key, fallback);
        return String.valueOf(value);
    }

    private boolean bool(Map<String, Object> section, String key, boolean fallback) {
        Object value = section.get(key);
        return value instanceof Boolean bool ? bool : fallback;
    }

    private int integer(Map<String, Object> section, String key, int fallback) {
        Object value = section.get(key);
        return value instanceof Number number ? number.intValue() : fallback;
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
