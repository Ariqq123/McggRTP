package me.mcgg.azreyzaako.mcggrtp.paper.listener;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import me.mcgg.azreyzaako.mcggrtp.paper.McggRTPPaper;
import me.mcgg.azreyzaako.mcggrtp.paper.config.PaperConfig;
import me.mcgg.azreyzaako.mcggrtp.paper.messaging.PaperMessageBridge;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.Sound;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PendingRtpJoinListenerTest {
    @Test
    void joinSchedulesMultiplePendingChecksAfterJoin() {
        McggRTPPaper plugin = mock(McggRTPPaper.class);
        PaperMessageBridge bridge = mock(PaperMessageBridge.class);
        Server server = mock(Server.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        Player player = mock(Player.class);
        PlayerJoinEvent event = mock(PlayerJoinEvent.class);
        PendingRtpJoinListener listener = new PendingRtpJoinListener(plugin, bridge);

        when(plugin.getServer()).thenReturn(server);
        when(plugin.configModel()).thenReturn(config());
        when(server.getScheduler()).thenReturn(scheduler);
        when(event.getPlayer()).thenReturn(player);

        listener.onPlayerJoin(event);

        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        ArgumentCaptor<Long> delayCaptor = ArgumentCaptor.forClass(Long.class);
        verify(scheduler, times(3)).runTaskLater(eq(plugin), task.capture(), delayCaptor.capture());
        org.junit.jupiter.api.Assertions.assertEquals(List.of(1L, 10L, 20L), delayCaptor.getAllValues());
        task.getAllValues().forEach(Runnable::run);
        verify(bridge, times(3)).sendCheckPending(player);
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
                java.util.Map.of(),
                new PaperConfig.NetworkSettings("survival-1", "mcggrtp.server.", java.util.Map.of(), java.util.Map.of()),
                300,
                java.util.Map.of("world", new PaperConfig.WorldRtpSettings(true, 0, 0, 0, 0, 1, false, java.util.Set.of(Biome.PLAINS), java.util.Set.of()))
        );
    }
}
