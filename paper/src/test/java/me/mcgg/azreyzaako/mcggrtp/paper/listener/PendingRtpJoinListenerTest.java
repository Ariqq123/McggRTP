package me.mcgg.azreyzaako.mcggrtp.paper.listener;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import me.mcgg.azreyzaako.mcggrtp.paper.McggRTPPaper;
import me.mcgg.azreyzaako.mcggrtp.paper.messaging.PaperMessageBridge;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PendingRtpJoinListenerTest {
    @Test
    void joinSchedulesPendingCheckOneTickLater() {
        McggRTPPaper plugin = mock(McggRTPPaper.class);
        PaperMessageBridge bridge = mock(PaperMessageBridge.class);
        Server server = mock(Server.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        Player player = mock(Player.class);
        PlayerJoinEvent event = mock(PlayerJoinEvent.class);
        PendingRtpJoinListener listener = new PendingRtpJoinListener(plugin, bridge);

        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);
        when(event.getPlayer()).thenReturn(player);

        listener.onPlayerJoin(event);

        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runTaskLater(eq(plugin), task.capture(), eq(1L));
        task.getValue().run();
        verify(bridge).sendCheckPending(player);
    }
}
