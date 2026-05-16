package me.mcgg.azreyzaako.mcggrtp.velocity.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;

class VelocityConfigLoaderTest {
    @TempDir
    Path tempDir;

    @Test
    void loadDerivesServerPermissionFromServerIdWhenPermissionIsOmitted() {
        VelocityConfigLoader loader = new VelocityConfigLoader(tempDir, org.mockito.Mockito.mock(Logger.class));

        VelocityConfig config = loader.load();

        assertTrue(Files.exists(tempDir.resolve("config.yml")));
        assertFalse(config.debugEnabled());
        assertEquals("mcggrtp.server.survival-2", config.servers().get("survival-2").permission());
    }

    @Test
    void loadReadsDebugModeWhenEnabled() throws Exception {
        Path configPath = tempDir.resolve("config.yml");
        Files.writeString(configPath, """
                debug:
                  enabled: true

                settings:
                  plugin-message-channel: "mcggrtp:main"
                  pending-expire-seconds: 30

                cooldowns:
                  enabled: true
                  default-seconds: 300
                  bypass-permission: "mcggrtp.bypass.cooldown"

                servers:
                  survival-1:
                    display-name: "&aSurvival 1"
                    enabled: true

                dimensions:
                  overworld:
                    servers: ["survival-1"]
                """);
        VelocityConfigLoader loader = new VelocityConfigLoader(tempDir, org.mockito.Mockito.mock(Logger.class));

        VelocityConfig config = loader.load();

        assertTrue(config.debugEnabled());
    }

    @Test
    void loadKeepsExplicitServerPermissionOverride() throws Exception {
        Path configPath = tempDir.resolve("config.yml");
        Files.writeString(configPath, """
                settings:
                  plugin-message-channel: "mcggrtp:main"
                  pending-expire-seconds: 30

                cooldowns:
                  enabled: true
                  default-seconds: 300
                  bypass-permission: "mcggrtp.bypass.cooldown"

                servers:
                  skyblock321:
                    display-name: "&bSkyblock 321"
                    enabled: true
                    permission: "mcgg.server.skyblock321"

                dimensions:
                  overworld:
                    servers: ["skyblock321"]
                """);
        VelocityConfigLoader loader = new VelocityConfigLoader(tempDir, org.mockito.Mockito.mock(Logger.class));

        VelocityConfig config = loader.load();

        assertEquals("mcgg.server.skyblock321", config.servers().get("skyblock321").permission());
    }

    @Test
    void loadUsesCustomServerPermissionPrefixWhenPermissionIsOmitted() throws Exception {
        Path configPath = tempDir.resolve("config.yml");
        Files.writeString(configPath, """
                settings:
                  plugin-message-channel: "mcggrtp:main"
                  pending-expire-seconds: 30

                cooldowns:
                  enabled: true
                  default-seconds: 300
                  bypass-permission: "mcggrtp.bypass.cooldown"
                  server-permission-prefix: "mcgg.server."

                servers:
                  skyblock321:
                    display-name: "&bSkyblock 321"
                    enabled: true

                dimensions:
                  overworld:
                    servers: ["skyblock321"]
                """);
        VelocityConfigLoader loader = new VelocityConfigLoader(tempDir, org.mockito.Mockito.mock(Logger.class));

        VelocityConfig config = loader.load();

        assertEquals("mcgg.server.", config.serverPermissionPrefix());
        assertEquals("mcgg.server.skyblock321", config.servers().get("skyblock321").permission());
    }
}
