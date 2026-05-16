package me.mcgg.azreyzaako.mcggrtp.paper.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import me.mcgg.azreyzaako.mcggrtp.common.ServerStatusEntry;
import me.mcgg.azreyzaako.mcggrtp.paper.McggRTPPaper;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class RtpMenusTest {
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
    void mainMenuShowsBarrierForLockedDimension() {
        PlayerMock player = server.addPlayer("viewer");
        player.addAttachment(plugin, "mcggrtp.dimension.nether", false);

        RtpMenus.openMainMenu(player, plugin.configModel(), plugin.messages());

        ItemStack netherItem = player.getOpenInventory().getTopInventory().getItem(13);
        assertEquals(Material.BARRIER, netherItem.getType());
        assertEquals("Nether", plain.serialize(netherItem.getItemMeta().displayName()));
        assertEquals("You do not have permission for this dimension.", plain.serialize(netherItem.getItemMeta().lore().getFirst()));
        assertInstanceOf(MenuHolder.class, player.getOpenInventory().getTopInventory().getHolder());
    }

    @Test
    void serverMenuShowsOnlineLockedAndOfflineStatesWithCounts() {
        PlayerMock player = server.addPlayer("viewer");
        assertEquals("mcggrtp.server.survival-2", plugin.configModel().network().servers().get("survival-2").permission());
        player.addAttachment(plugin, "mcggrtp.server.survival-2", false);

        RtpMenus.openServerMenu(
                player,
                plugin.configModel(),
                plugin.messages(),
                "overworld",
                List.of(
                        new ServerStatusEntry("survival-1", "&aSurvival 1", true, 4),
                        new ServerStatusEntry("survival-2", "&aSurvival 2", true, 2),
                        new ServerStatusEntry("survival-3", "&aSurvival 3", false, 0)
                )
        );

        ItemStack online = player.getOpenInventory().getTopInventory().getItem(0);
        assertEquals(Material.LIME_WOOL, online.getType());
        assertTrue(loreLines(online).contains("Players online: 4"));
        assertTrue(loreLines(online).contains("Click to RTP via survival-1"));

        ItemStack locked = player.getOpenInventory().getTopInventory().getItem(1);
        assertEquals(Material.LIME_WOOL, locked.getType());
        assertEquals("Survival 2", plain.serialize(locked.getItemMeta().displayName()));
        assertTrue(loreLines(locked).contains("You do not have permission for this server."));

        ItemStack offline = player.getOpenInventory().getTopInventory().getItem(2);
        assertEquals(Material.BARRIER, offline.getType());
        assertTrue(loreLines(offline).contains("This server is offline."));
    }

    private List<String> loreLines(ItemStack itemStack) {
        return itemStack.getItemMeta().lore().stream().map(plain::serialize).toList();
    }
}
