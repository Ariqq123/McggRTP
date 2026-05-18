package me.mcgg.azreyzaako.mcggrtp.paper.messaging;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Set;
import me.mcgg.azreyzaako.mcggrtp.common.CooldownResponse;
import me.mcgg.azreyzaako.mcggrtp.common.MessageCodec;
import me.mcgg.azreyzaako.mcggrtp.common.PendingRtpResponse;
import me.mcgg.azreyzaako.mcggrtp.paper.McggRTPPaper;
import me.mcgg.azreyzaako.mcggrtp.paper.MessageBundle;
import me.mcgg.azreyzaako.mcggrtp.paper.config.PaperConfig;
import me.mcgg.azreyzaako.mcggrtp.paper.rtp.RtpTeleportService;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PaperMessageBridgeTest {
    private McggRTPPaper plugin;
    private Player player;
    private RtpTeleportService teleportService;
    private MessageBundle messages;
    private PaperMessageBridge bridge;

    @BeforeEach
    void setUp() {
        plugin = mock(McggRTPPaper.class);
        player = mock(Player.class);
        teleportService = mock(RtpTeleportService.class);
        messages = mock(MessageBundle.class);
        when(plugin.teleportService()).thenReturn(teleportService);
        when(plugin.messages()).thenReturn(messages);
        when(messages.text(eq("cooldown"), eq("{time}"), any())).thenReturn(Component.text("cooldown"));
        when(plugin.configModel()).thenReturn(config());
        when(player.getLocation()).thenReturn(mock(Location.class));
        bridge = new PaperMessageBridge(plugin);
    }

    @Test
    void pendingResponseStartsTeleportOnJoin() {
        bridge.onPluginMessageReceived("mcggrtp:main", player, MessageCodec.encodePendingRtpResponse(
                new PendingRtpResponse(true, "req-1", "world", "overworld")
        ));

        verify(plugin.teleportService()).beginPendingTeleport(player, "req-1", "world", "overworld");
    }

    @Test
    void duplicatePendingResponsesOnlyStartTeleportOnce() {
        byte[] payload = MessageCodec.encodePendingRtpResponse(
                new PendingRtpResponse(true, "req-1", "world", "overworld")
        );

        bridge.onPluginMessageReceived("mcggrtp:main", player, payload);
        bridge.onPluginMessageReceived("mcggrtp:main", player, payload);

        verify(plugin.teleportService(), times(1)).beginPendingTeleport(player, "req-1", "world", "overworld");
    }

    @Test
    void inactiveCooldownResponseStartsLocalTeleport() {
        java.util.UUID playerId = java.util.UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);
        doNothing().when(player).sendPluginMessage(eq(plugin), eq("mcggrtp:main"), any());
        bridge.checkCooldownThenLocalTeleport(player, "world", "overworld");

        var captor = org.mockito.ArgumentCaptor.forClass(byte[].class);
        verify(player).sendPluginMessage(eq(plugin), eq("mcggrtp:main"), captor.capture());
        var request = MessageCodec.decodeCheckCooldown(captor.getValue());

        bridge.onPluginMessageReceived("mcggrtp:main", player, MessageCodec.encodeCooldownResponse(
                new CooldownResponse(request.requestId(), false, 0)
        ));

        verify(plugin.teleportService()).beginLocalTeleport(player, "world", "overworld");
    }

    @Test
    void activeCooldownResponseSendsCooldownMessage() {
        java.util.UUID playerId = java.util.UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);
        doNothing().when(player).sendPluginMessage(eq(plugin), eq("mcggrtp:main"), any());
        bridge.checkCooldownThenLocalTeleport(player, "world", "overworld");

        var captor = org.mockito.ArgumentCaptor.forClass(byte[].class);
        verify(player).sendPluginMessage(eq(plugin), eq("mcggrtp:main"), captor.capture());
        var request = MessageCodec.decodeCheckCooldown(captor.getValue());

        bridge.onPluginMessageReceived("mcggrtp:main", player, MessageCodec.encodeCooldownResponse(
                new CooldownResponse(request.requestId(), true, 20)
        ));

        verify(player).sendMessage(any(Component.class));
    }

    private PaperConfig config() {
        return new PaperConfig(
                new PaperConfig.DebugSettings(false),
                new PaperConfig.GuiSettings("&8RTP", 27, true, Material.BLACK_STAINED_GLASS_PANE, " "),
                new PaperConfig.ServerMenuSettings("&8Choose", 27, Material.LIME_WOOL, Material.BARRIER),
                new PaperConfig.SoundSettings(Sound.BLOCK_NOTE_BLOCK_PLING, Sound.BLOCK_NOTE_BLOCK_PLING, Sound.ENTITY_VILLAGER_NO, Sound.ENTITY_ENDERMAN_TELEPORT),
                Map.of(),
                new PaperConfig.NetworkSettings("survival-1", "mcggrtp.server.", Map.of(), Map.of()),
                300,
                8,
                new PaperConfig.AdaptiveThrottleSettings(false, 1, 18.5D, 80.0D, 0, 25),
                new PaperConfig.LocationPoolSettings(false, 0, 100, 1, false),
                Map.of("world", new PaperConfig.WorldRtpSettings(true, 0, 0, 0, 0, 1, false, Set.of(Biome.PLAINS), Set.of()))
        );
    }
}
