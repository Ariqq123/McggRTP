package me.mcgg.azreyzaako.mcggrtp.paper.rtp;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
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

    @BeforeEach
    void setUp() {
        plugin = mock(McggRTPPaper.class);
        messages = mock(MessageBundle.class);
        bridge = mock(PaperMessageBridge.class);
        player = mock(Player.class);
        server = mock(Server.class);
        scheduler = mock(BukkitScheduler.class);
        logger = mock(Logger.class);

        when(plugin.getServer()).thenReturn(server);
        when(plugin.getLogger()).thenReturn(logger);
        when(logger.isLoggable(any())).thenReturn(false);
        when(server.getScheduler()).thenReturn(scheduler);
        when(messages.text("searching")).thenReturn(Component.text("searching"));
        when(messages.text("teleport-success")).thenReturn(Component.text("teleport-success"));
        when(messages.text("teleport-failed")).thenReturn(Component.text("teleport-failed"));
        when(plugin.configModel()).thenReturn(config());
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getLocation()).thenReturn(mock(Location.class));
        when(player.isOnline()).thenReturn(true);
        when(player.isValid()).thenReturn(true);

        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(1);
            runnable.run();
            return null;
        }).when(scheduler).runTaskAsynchronously(eq(plugin), any(Runnable.class));

        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(1);
            runnable.run();
            return null;
        }).when(scheduler).runTask(eq(plugin), any(Runnable.class));
    }

    @Test
    void localTeleportReportsSuccessBackToVelocity() {
        World world = safeWorld();
        when(server.getWorld("world")).thenReturn(world);
        when(player.teleportAsync(any(Location.class))).thenReturn(CompletableFuture.completedFuture(true));

        RtpTeleportService service = new RtpTeleportService(plugin, config(), messages, bridge);
        service.beginLocalTeleport(player, "world", "overworld");

        verify(bridge).sendResult(player, new RtpResult("", player.getUniqueId(), true, ""));
    }

    @Test
    void pendingTeleportStopsBeforeCompletionWhenPlayerIsNoLongerValid() {
        World world = safeWorld();
        when(server.getWorld("world")).thenReturn(world);
        when(player.isOnline()).thenReturn(false);

        RtpTeleportService service = new RtpTeleportService(plugin, config(), messages, bridge);
        service.beginPendingTeleport(player, "req-1", "world", "overworld");

        verify(player, never()).teleportAsync(any(Location.class));
        verify(player, never()).sendMessage(messages.text("teleport-success"));
        verify(player, never()).sendMessage(messages.text("teleport-failed"));
        verify(bridge, never()).sendClearPending(player, "req-1");
    }

    @Test
    void pendingTeleportClearsWhenNoSafeLocationIsFound() {
        when(server.getWorld("world")).thenReturn(mock(World.class));
        SafeLocationFinder finder = mock(SafeLocationFinder.class);
        when(finder.search(any(World.class), any(PaperConfig.WorldRtpSettings.class)))
                .thenReturn(new SafeLocationFinder.SearchResult(Optional.empty(), 1));

        RtpTeleportService service = new RtpTeleportService(plugin, config(), messages, bridge, finder);
        service.beginPendingTeleport(player, "req-1", "world", "overworld");

        verify(bridge).sendResult(player, new RtpResult("req-1", player.getUniqueId(), false, "Could not find a safe location."));
        verify(bridge).sendClearPending(player, "req-1");
    }

    private PaperConfig config() {
        return new PaperConfig(
                new PaperConfig.GuiSettings("&8RTP", 27, true, Material.BLACK_STAINED_GLASS_PANE, " "),
                new PaperConfig.ServerMenuSettings("&8Choose", 27, Material.LIME_WOOL, Material.BARRIER),
                new PaperConfig.SoundSettings(
                        Sound.BLOCK_NOTE_BLOCK_PLING,
                        Sound.BLOCK_NOTE_BLOCK_PLING,
                        Sound.ENTITY_VILLAGER_NO,
                        Sound.ENTITY_ENDERMAN_TELEPORT
                ),
                Map.of(),
                new PaperConfig.NetworkSettings("survival-1", "mcggrtp.server.", Map.of(), Map.of()),
                300,
                Map.of("world", new PaperConfig.WorldRtpSettings(true, 0, 0, 0, 0, 1, false, Set.of(), Set.of(Material.LAVA)))
        );
    }

    private World safeWorld() {
        World world = mock(World.class);
        WorldBorder border = mock(WorldBorder.class);
        Block feet = passableBlock(Biome.PLAINS, Material.AIR);
        Block head = passableBlock(Biome.PLAINS, Material.AIR);
        Block below = solidBlock(Material.STONE);
        Block defaultBlock = solidBlock(Material.BEDROCK);
        when(world.getEnvironment()).thenReturn(World.Environment.NORMAL);
        when(world.getMinHeight()).thenReturn(0);
        when(world.getMaxHeight()).thenReturn(256);
        when(world.getHighestBlockYAt(anyInt(), anyInt())).thenReturn(64);
        when(world.getWorldBorder()).thenReturn(border);
        when(border.isInside(any(Location.class))).thenReturn(true);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(defaultBlock);
        when(world.getBlockAt(0, 64, 0)).thenReturn(below);
        when(world.getBlockAt(0, 65, 0)).thenReturn(feet);
        when(world.getBlockAt(0, 66, 0)).thenReturn(head);
        return world;
    }

    private Block passableBlock(Biome biome, Material type) {
        Block block = mock(Block.class);
        when(block.isPassable()).thenReturn(true);
        when(block.getBiome()).thenReturn(biome);
        when(block.getType()).thenReturn(type);
        return block;
    }

    private Block solidBlock(Material type) {
        Block block = mock(Block.class);
        when(block.getType()).thenReturn(type);
        return block;
    }
}
