package me.mcgg.azreyzaako.mcggrtp.paper.gui;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import me.mcgg.azreyzaako.mcggrtp.common.RtpRequest;
import me.mcgg.azreyzaako.mcggrtp.common.ServerStatusEntry;
import me.mcgg.azreyzaako.mcggrtp.paper.McggRTPPaper;
import me.mcgg.azreyzaako.mcggrtp.paper.MessageBundle;
import me.mcgg.azreyzaako.mcggrtp.paper.config.PaperConfig;
import me.mcgg.azreyzaako.mcggrtp.paper.messaging.PaperMessageBridge;
import me.mcgg.azreyzaako.mcggrtp.paper.rtp.RtpWarmupService;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RtpGuiListenerTest {
    private McggRTPPaper plugin;
    private PaperMessageBridge bridge;
    private MessageBundle messages;
    private RtpWarmupService warmupService;
    private Player player;
    private RtpGuiListener listener;

    @BeforeEach
    void setUp() {
        plugin = mock(McggRTPPaper.class);
        bridge = mock(PaperMessageBridge.class);
        messages = mock(MessageBundle.class);
        warmupService = mock(RtpWarmupService.class);
        player = mock(Player.class);
        listener = new RtpGuiListener(plugin);

        when(plugin.configModel()).thenReturn(config());
        when(plugin.messageBridge()).thenReturn(bridge);
        when(plugin.messages()).thenReturn(messages);
        when(plugin.warmupService()).thenReturn(warmupService);
        when(player.getLocation()).thenReturn(mock(Location.class));
        when(messages.text("no-permission")).thenReturn(Component.text("no-permission"));
        when(messages.text("dimension-unavailable")).thenReturn(Component.text("dimension-unavailable"));
        when(messages.text("server-offline")).thenReturn(Component.text("server-offline"));
        when(messages.text(eq("sending-server"), eq("{server}"), any())).thenReturn(Component.text("sending-server"));
        doNothing().when(player).playSound(any(Location.class), any(Sound.class), eq(1.0F), eq(1.0F));
        doNothing().when(player).closeInventory();
    }

    @Test
    void mainMenuDeniedWithoutDimensionPermission() {
        when(player.hasPermission("mcggrtp.dimension.overworld")).thenReturn(false);

        listener.onInventoryClick(event(mainInventory(), new ItemStack(Material.GRASS_BLOCK), 10));

        verify(player).sendMessage(Component.text("no-permission"));
        verify(bridge, never()).requestServerMenu(any(), any());
    }

    @Test
    void mainMenuRequestsServerMenuWhenAllowed() {
        when(player.hasPermission("mcggrtp.dimension.overworld")).thenReturn(true);

        listener.onInventoryClick(event(mainInventory(), new ItemStack(Material.GRASS_BLOCK), 10));

        verify(bridge).requestServerMenu(player, "overworld");
    }

    @Test
    void mainMenuDeniesDimensionWithoutConfiguredServers() {
        when(plugin.configModel()).thenReturn(configWithoutDimensionTargets());
        when(player.hasPermission("mcggrtp.dimension.overworld")).thenReturn(true);

        listener.onInventoryClick(event(mainInventory(), new ItemStack(Material.BARRIER), 10));

        verify(player).sendMessage(Component.text("dimension-unavailable"));
        verify(bridge, never()).requestServerMenu(any(), any());
    }

    @Test
    void serverMenuStartsLocalTeleportWarmupOnCurrentServer() {
        when(bridge.resolveCurrentServer(player)).thenReturn("survival-1");

        listener.onInventoryClick(event(serverInventory("overworld"), new ItemStack(Material.LIME_WOOL), 12));

        var callback = org.mockito.ArgumentCaptor.forClass(Runnable.class);
        verify(warmupService).begin(eq(player), eq(5), callback.capture());
        callback.getValue().run();
        verify(bridge).checkCooldownThenLocalTeleport(player, "world", "overworld");
        verify(bridge, never()).sendCreatePending(any(), any());
    }

    @Test
    void serverMenuCreatesPendingRequestForOtherServer() {
        when(bridge.resolveCurrentServer(player)).thenReturn("survival-1");
        when(player.hasPermission("mcggrtp.server.survival-2")).thenReturn(true);

        listener.onInventoryClick(event(serverInventory("overworld"), new ItemStack(Material.LIME_WOOL), 13));

        var callback = org.mockito.ArgumentCaptor.forClass(Runnable.class);
        verify(warmupService).begin(eq(player), eq(5), callback.capture());
        callback.getValue().run();
        verify(bridge).sendCreatePending(eq(player), any(RtpRequest.class));
        verify(player).sendMessage(Component.text("sending-server"));
    }

    @Test
    void serverMenuDeniesOfflineServer() {
        when(bridge.cachedStatus("overworld", "survival-2")).thenReturn(new ServerStatusEntry("survival-2", "&aSurvival 2", false, 0));

        listener.onInventoryClick(event(serverInventory("overworld"), new ItemStack(Material.BARRIER), 13));

        verify(player).sendMessage(Component.text("server-offline"));
        verify(bridge, never()).sendCreatePending(any(), any());
        verify(bridge, never()).checkCooldownThenLocalTeleport(any(), any(), any());
    }

    @Test
    void serverMenuDeniesWithoutServerPermission() {
        when(player.hasPermission("mcggrtp.server.survival-2")).thenReturn(false);

        listener.onInventoryClick(event(serverInventory("overworld"), new ItemStack(Material.LIME_WOOL), 13));

        verify(player).sendMessage(Component.text("no-permission"));
        verify(bridge, never()).sendCreatePending(any(), any());
    }

    @Test
    void serverMenuBackButtonReopensMainMenu() {
        listener.onInventoryClick(event(serverInventory("overworld"), new ItemStack(Material.ARROW), 22));

        verify(bridge).openMainMenu(player);
        verify(warmupService, never()).begin(any(), anyInt(), any());
    }

    @Test
    void serverMenuIgnoresFillerSlotWithoutServerMapping() {
        listener.onInventoryClick(event(serverInventory("overworld"), new ItemStack(Material.BLACK_STAINED_GLASS_PANE), 0));

        verifyNoInteractions(bridge);
        verifyNoInteractions(warmupService);
    }

    private InventoryClickEvent event(Inventory inventory, ItemStack currentItem, int slot) {
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getInventory()).thenReturn(inventory);
        when(event.getCurrentItem()).thenReturn(currentItem);
        when(event.getSlot()).thenReturn(slot);
        return event;
    }

    private Inventory mainInventory() {
        Inventory inventory = mock(Inventory.class);
        when(inventory.getHolder()).thenReturn(new MenuHolder(new MenuContext(MenuContext.MenuType.MAIN, "")));
        return inventory;
    }

    private Inventory serverInventory(String dimension) {
        Inventory inventory = mock(Inventory.class);
        Map<Integer, String> slotMap = new LinkedHashMap<>();
        slotMap.put(12, "survival-1");
        slotMap.put(13, "survival-2");
        when(inventory.getHolder()).thenReturn(new MenuHolder(new MenuContext(MenuContext.MenuType.SERVER, dimension, slotMap, 22)));
        return inventory;
    }

    private PaperConfig config() {
        return new PaperConfig(
                new PaperConfig.DebugSettings(false),
                new PaperConfig.GuiSettings("&8RTP", 27, true, Material.BLACK_STAINED_GLASS_PANE, " "),
                new PaperConfig.ServerMenuSettings("&8Choose", 27, Material.LIME_WOOL, Material.BARRIER),
                new PaperConfig.SoundSettings(
                        Sound.BLOCK_NOTE_BLOCK_PLING,
                        Sound.BLOCK_NOTE_BLOCK_PLING,
                        Sound.ENTITY_VILLAGER_NO,
                        Sound.ENTITY_ENDERMAN_TELEPORT
                ),
                Map.of(
                        "overworld",
                        new PaperConfig.DimensionOption(
                                "overworld",
                                10,
                                "&aOverworld",
                                Material.GRASS_BLOCK,
                                "world",
                                "mcggrtp.dimension.overworld",
                                5,
                                List.of("&7Teleport")
                        )
                ),
                new PaperConfig.NetworkSettings(
                        "survival-1",
                        "mcggrtp.server.",
                        Map.of("overworld", List.of("survival-1", "survival-2")),
                        Map.of(
                                "survival-1", new PaperConfig.NetworkServer("survival-1", "&aSurvival 1", ""),
                                "survival-2", new PaperConfig.NetworkServer("survival-2", "&aSurvival 2", "mcggrtp.server.survival-2")
                        )
                ),
                300,
                Map.of("world", new PaperConfig.WorldRtpSettings(true, 0, 0, 0, 0, 1, false, Set.of(Biome.PLAINS), Set.of()))
        );
    }

    private PaperConfig configWithoutDimensionTargets() {
        return new PaperConfig(
                config().debug(),
                config().gui(),
                config().serverMenu(),
                config().sounds(),
                config().dimensions(),
                new PaperConfig.NetworkSettings(
                        "survival-1",
                        "mcggrtp.server.",
                        Map.of("overworld", List.of()),
                        config().network().servers()
                ),
                config().cooldownSeconds(),
                config().worlds()
        );
    }
}
