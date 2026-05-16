package me.mcgg.azreyzaako.mcggrtp.paper.rtp;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import me.mcgg.azreyzaako.mcggrtp.paper.McggRTPPaper;
import me.mcgg.azreyzaako.mcggrtp.paper.MessageBundle;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RtpWarmupServiceTest {
    private McggRTPPaper plugin;
    private MessageBundle messages;
    private BukkitScheduler scheduler;
    private Player player;
    private World world;
    private RtpWarmupService service;

    @BeforeEach
    void setUp() {
        plugin = mock(McggRTPPaper.class);
        messages = mock(MessageBundle.class);
        scheduler = mock(BukkitScheduler.class);
        player = mock(Player.class);
        world = mock(World.class);
        service = new RtpWarmupService(plugin);

        Server server = mock(Server.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);
        when(plugin.messages()).thenReturn(messages);
        when(messages.text(eq("warmup-started"), eq("{time}"), any())).thenReturn(Component.text("warmup-started"));
        when(messages.text("warmup-cancelled")).thenReturn(Component.text("warmup-cancelled"));
        when(player.getUniqueId()).thenReturn(java.util.UUID.randomUUID());
        when(player.getLocation()).thenReturn(new Location(world, 10.0, 64.0, 10.0, 0.0F, 0.0F));
    }

    @Test
    void zeroSecondWarmupRunsImmediately() {
        boolean[] ran = {false};

        service.begin(player, 0, () -> ran[0] = true);

        assertTrue(ran[0]);
        verify(scheduler, never()).runTaskLater(any(), any(Runnable.class), anyLong());
        assertFalse(service.hasWarmup(player.getUniqueId()));
    }

    @Test
    void movementCancelsPendingWarmup() {
        BukkitTask task = mock(BukkitTask.class);
        when(scheduler.runTaskLater(eq(plugin), any(Runnable.class), eq(100L))).thenReturn(task);

        service.begin(player, 5, () -> {
        });
        service.cancelIfMoved(player, new Location(world, 11.0, 64.0, 10.0, 0.0F, 0.0F));

        verify(task).cancel();
        verify(player).sendMessage(Component.text("warmup-cancelled"));
        assertFalse(service.hasWarmup(player.getUniqueId()));
    }

    @Test
    void lookOnlyMovementDoesNotCancelWarmupAndScheduledActionStillRuns() {
        BukkitTask task = mock(BukkitTask.class);
        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        when(scheduler.runTaskLater(eq(plugin), runnableCaptor.capture(), eq(100L))).thenReturn(task);
        boolean[] ran = {false};

        service.begin(player, 5, () -> ran[0] = true);
        service.cancelIfMoved(player, new Location(world, 10.0, 64.0, 10.0, 90.0F, 20.0F));

        verify(task, never()).cancel();
        assertTrue(service.hasWarmup(player.getUniqueId()));

        runnableCaptor.getValue().run();

        assertTrue(ran[0]);
        assertFalse(service.hasWarmup(player.getUniqueId()));
    }
}
