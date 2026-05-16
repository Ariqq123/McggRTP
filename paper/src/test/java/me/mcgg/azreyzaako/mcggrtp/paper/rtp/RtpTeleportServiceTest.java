package me.mcgg.azreyzaako.mcggrtp.paper.rtp;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import me.mcgg.azreyzaako.mcggrtp.common.RtpResult;
import me.mcgg.azreyzaako.mcggrtp.paper.McggRTPPaper;
import me.mcgg.azreyzaako.mcggrtp.paper.MessageBundle;
import me.mcgg.azreyzaako.mcggrtp.paper.config.PaperConfig;
import me.mcgg.azreyzaako.mcggrtp.paper.messaging.PaperMessageBridge;
import net.kyori.adventure.text.Component;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RtpTeleportServiceTest {
    private McggRTPPaper plugin;
    private MessageBundle messages;
    private PaperMessageBridge bridge;
    private Player player;
    private Server server;
    private BukkitScheduler scheduler;
    private Logger logger;
    private World world;
    private Chunk chunk;

    @BeforeEach
    void setUp() {
        plugin = mock(McggRTPPaper.class);
        messages = mock(MessageBundle.class);
        bridge = mock(PaperMessageBridge.class);
        player = mock(Player.class);
        server = mock(Server.class);
        scheduler = mock(BukkitScheduler.class);
        logger = mock(Logger.class);
        world = mock(World.class);
        chunk = mock(Chunk.class);

        when(plugin.getServer()).thenReturn(server);
        when(plugin.getLogger()).thenReturn(logger);
        when(logger.isLoggable(any())).thenReturn(false);
        when(server.getScheduler()).thenReturn(scheduler);
        when(server.getWorld("world")).thenReturn(world);
        when(world.getChunkAtAsync(anyInt(), anyInt(), anyBoolean())).thenReturn(CompletableFuture.completedFuture(chunk));
        when(messages.text("searching")).thenReturn(Component.text("searching"));
        when(messages.text("teleport-success")).thenReturn(Component.text("teleport-success"));
        when(messages.text("teleport-failed")).thenReturn(Component.text("teleport-failed"));
        when(plugin.configModel()).thenReturn(config());
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getLocation()).thenReturn(mock(Location.class));
        when(player.isOnline()).thenReturn(true);
        when(player.isValid()).thenReturn(true);
        when(player.teleportAsync(any(Location.class))).thenReturn(CompletableFuture.completedFuture(true));

        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(1);
            runnable.run();
            return null;
        }).when(scheduler).runTask(eq(plugin), any(Runnable.class));
    }

    @Test
    void localTeleportReportsSuccessBackToVelocityAfterGeneratedChunkCandidate() {
        SafeLocationFinder finder = mock(SafeLocationFinder.class);
        SafeLocationFinder.SearchPlan plan = plan(List.of(new SafeLocationFinder.Candidate(0, 0, 0, 0)), List.of(), 1);
        when(finder.createPlan(world, config().worlds().get("world"))).thenReturn(plan);
        when(finder.validate(world, config().worlds().get("world"), plan, new SafeLocationFinder.Candidate(0, 0, 0, 0)))
                .thenReturn(Optional.of(new Location(world, 0.5D, 65.0D, 0.5D)));

        RtpTeleportService service = new RtpTeleportService(plugin, config(), messages, bridge, finder);
        service.beginLocalTeleport(player, "world", "overworld");

        verify(world).getChunkAtAsync(0, 0, false);
        verify(bridge).sendResult(player, new RtpResult("", player.getUniqueId(), true, ""));
    }

    @Test
    void pendingTeleportFallsBackToGeneratedChunkLoadWhenPrimaryCandidatesFail() {
        SafeLocationFinder finder = mock(SafeLocationFinder.class);
        SafeLocationFinder.Candidate primary = new SafeLocationFinder.Candidate(0, 0, 0, 0);
        SafeLocationFinder.Candidate fallback = new SafeLocationFinder.Candidate(16, 16, 1, 1);
        SafeLocationFinder.SearchPlan plan = plan(List.of(primary), List.of(fallback), 2);
        when(finder.createPlan(world, config().worlds().get("world"))).thenReturn(plan);
        when(finder.validate(world, config().worlds().get("world"), plan, primary)).thenReturn(Optional.empty());
        when(finder.validate(world, config().worlds().get("world"), plan, fallback))
                .thenReturn(Optional.of(new Location(world, 16.5D, 70.0D, 16.5D)));

        RtpTeleportService service = new RtpTeleportService(plugin, config(), messages, bridge, finder);
        service.beginPendingTeleport(player, "req-1", "world", "overworld");

        verify(world).getChunkAtAsync(0, 0, false);
        verify(world).getChunkAtAsync(1, 1, true);
        verify(bridge).sendResult(player, new RtpResult("req-1", player.getUniqueId(), true, ""));
        verify(bridge).sendClearPending(player, "req-1");
    }

    @Test
    void pendingTeleportStopsBeforeCompletionWhenPlayerIsNoLongerValid() {
        SafeLocationFinder finder = mock(SafeLocationFinder.class);
        SafeLocationFinder.SearchPlan plan = plan(List.of(new SafeLocationFinder.Candidate(0, 0, 0, 0)), List.of(), 1);
        when(finder.createPlan(world, config().worlds().get("world"))).thenReturn(plan);
        when(player.isOnline()).thenReturn(false);

        RtpTeleportService service = new RtpTeleportService(plugin, config(), messages, bridge, finder);
        service.beginPendingTeleport(player, "req-1", "world", "overworld");

        verify(player, never()).teleportAsync(any(Location.class));
        verify(bridge, never()).sendClearPending(player, "req-1");
    }

    @Test
    void pendingTeleportClearsWhenNoSafeLocationIsFound() {
        SafeLocationFinder finder = mock(SafeLocationFinder.class);
        SafeLocationFinder.Candidate fallback = new SafeLocationFinder.Candidate(16, 16, 1, 1);
        SafeLocationFinder.SearchPlan plan = plan(List.of(), List.of(fallback), 1);
        when(finder.createPlan(world, config().worlds().get("world"))).thenReturn(plan);
        when(finder.validate(world, config().worlds().get("world"), plan, fallback)).thenReturn(Optional.empty());

        RtpTeleportService service = new RtpTeleportService(plugin, config(), messages, bridge, finder);
        service.beginPendingTeleport(player, "req-1", "world", "overworld");

        verify(bridge).sendResult(player, new RtpResult("req-1", player.getUniqueId(), false, "Could not find a safe location."));
        verify(bridge).sendClearPending(player, "req-1");
    }

    private SafeLocationFinder.SearchPlan plan(List<SafeLocationFinder.Candidate> primary,
                                               List<SafeLocationFinder.Candidate> fallback,
                                               int maxAttempts) {
        return new SafeLocationFinder.SearchPlan(
                primary,
                fallback,
                World.Environment.NORMAL,
                0,
                254,
                maxAttempts,
                new Location(world, 0.5D, 0.0D, 0.5D)
        );
    }

    private PaperConfig config() {
        return new PaperConfig(
                new PaperConfig.GuiSettings("&8RTP", 27, true, org.bukkit.Material.BLACK_STAINED_GLASS_PANE, " "),
                new PaperConfig.ServerMenuSettings("&8Choose", 27, org.bukkit.Material.LIME_WOOL, org.bukkit.Material.BARRIER),
                new PaperConfig.SoundSettings(
                        Sound.BLOCK_NOTE_BLOCK_PLING,
                        Sound.BLOCK_NOTE_BLOCK_PLING,
                        Sound.ENTITY_VILLAGER_NO,
                        Sound.ENTITY_ENDERMAN_TELEPORT
                ),
                Map.of(),
                new PaperConfig.NetworkSettings("survival-1", "mcggrtp.server.", Map.of(), Map.of()),
                300,
                Map.of("world", new PaperConfig.WorldRtpSettings(true, 0, 0, 0, 0, 2, false, Set.of(), Set.of(org.bukkit.Material.LAVA)))
        );
    }
}
