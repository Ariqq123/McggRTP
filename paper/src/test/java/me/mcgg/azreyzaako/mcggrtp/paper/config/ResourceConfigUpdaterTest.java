package me.mcgg.azreyzaako.mcggrtp.paper.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.File;
import me.mcgg.azreyzaako.mcggrtp.paper.McggRTPPaper;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

class ResourceConfigUpdaterTest {
    private final PlainTextComponentSerializer plain = PlainTextComponentSerializer.plainText();
    private ServerMock server;
    private McggRTPPaper plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(McggRTPPaper.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void reloadMergesMissingConfigAndMessageKeysWithoutOverwritingCustomValues() throws Exception {
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        File messagesFile = new File(plugin.getDataFolder(), "messages.yml");

        YamlConfiguration customConfig = new YamlConfiguration();
        customConfig.set("gui.title", "&1Custom RTP");
        customConfig.set("gui.size", 27);
        customConfig.set("gui.filler.enabled", false);
        customConfig.set("main-menu.overworld.slot", 11);
        customConfig.set("main-menu.overworld.display-name", "&2Custom Overworld");
        customConfig.set("main-menu.overworld.material", "GRASS_BLOCK");
        customConfig.set("main-menu.overworld.world-name", "world");
        customConfig.set("main-menu.overworld.permission", "mcggrtp.dimension.overworld");
        customConfig.set("main-menu.overworld.warmup-seconds", 9);
        customConfig.set("main-menu.overworld.lore", java.util.List.of("&7Custom lore"));
        customConfig.set("network.current-server", "custom-1");
        customConfig.set("network.dimensions.overworld.servers", java.util.List.of("custom-1"));
        customConfig.set("network.servers.custom-1.display-name", "&aCustom 1");
        customConfig.save(configFile);

        YamlConfiguration customMessages = new YamlConfiguration();
        customMessages.set("prefix", "&6[Custom] ");
        customMessages.set("no-permission", "&4Denied");
        customMessages.save(messagesFile);

        plugin.reloadPluginState();

        YamlConfiguration updatedConfig = YamlConfiguration.loadConfiguration(configFile);
        YamlConfiguration updatedMessages = YamlConfiguration.loadConfiguration(messagesFile);

        assertEquals("&1Custom RTP", updatedConfig.getString("gui.title"));
        assertEquals("custom-1", updatedConfig.getString("network.current-server"));
        assertEquals(9, updatedConfig.getInt("main-menu.overworld.warmup-seconds"));
        assertTrue(updatedConfig.contains("main-menu.nether.display-name"));
        assertEquals("LIME_WOOL", updatedConfig.getString("server-menu.online-material"));
        assertEquals("BARRIER", updatedConfig.getString("server-menu.offline-material"));
        assertTrue(updatedConfig.contains("sounds.menu-open"));
        assertTrue(updatedConfig.contains("rtp.worlds.world.max-attempts"));
        assertFalse(updatedConfig.getBoolean("gui.filler.enabled"));

        assertEquals("&6[Custom] ", updatedMessages.getString("prefix"));
        assertEquals("&4Denied", updatedMessages.getString("no-permission"));
        assertEquals("&7This is your current server.", updatedMessages.getString("server-current-lore"));
        assertEquals("&eBack", updatedMessages.getString("menu-back-name"));

        assertEquals("Custom RTP", plain.serialize(plugin.messages().raw(plugin.getConfig().getString("gui.title"))));
        assertEquals("&2Custom Overworld", plugin.configModel().dimensions().get("overworld").displayName());
        assertTrue(plugin.configModel().dimensions().containsKey("nether"));
        assertEquals(Material.LIME_WOOL, plugin.configModel().serverMenu().onlineMaterial());
        assertEquals("[Custom] Denied", plain.serialize(plugin.messages().text("no-permission")));
        assertNotNull(plugin.messages().text("menu-back-name"));
    }

    @Test
    void updaterReportsCreatedAndMergedResourceChanges() throws Exception {
        File messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        String original = java.nio.file.Files.readString(messagesFile.toPath());

        assertTrue(messagesFile.delete());
        ResourceConfigUpdater.UpdateResult created = ResourceConfigUpdater.updateYamlResource(plugin, "messages.yml");
        assertTrue(created.created());
        assertTrue(created.changed());
        assertEquals("messages.yml", created.resourceName());

        YamlConfiguration customMessages = YamlConfiguration.loadConfiguration(messagesFile);
        customMessages.set("menu-back-name", null);
        customMessages.save(messagesFile);

        ResourceConfigUpdater.UpdateResult merged = ResourceConfigUpdater.updateYamlResource(plugin, "messages.yml");
        assertFalse(merged.created());
        assertTrue(merged.changed());
        assertEquals(1, merged.addedKeys());

        ResourceConfigUpdater.UpdateResult unchanged = ResourceConfigUpdater.updateYamlResource(plugin, "messages.yml");
        assertFalse(unchanged.changed());
        assertEquals(0, unchanged.addedKeys());

        java.nio.file.Files.writeString(messagesFile.toPath(), original);
    }
}
