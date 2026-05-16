package me.mcgg.azreyzaako.mcggrtp.paper.rtp;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
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

    @BeforeEach
    void setUp() {
        plugin = mock(McggRTPPaper.class);
        messages = mock(MessageBundle.class);
        bridge = mock(PaperMessageBridge.class);
        player = mock(Player.class);
        server = mock(Server.class);
        scheduler = mock(BukkitScheduler.class);

        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);
        when(messages.text("searching")).thenReturn(Component.text("searching"));
        when(messages.text("teleport-success")).thenReturn(Component.text("teleport-success"));
        when(plugin.configModel()).thenReturn(config());
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getLocation()).thenReturn(mock(Location.class));

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
                new PaperConfig.NetworkSettings("survival-1", Map.of(), Map.of()),
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
        when(world.getEnvironment()).thenReturn(World.Environment.NORMAL);
        when(world.getHighestBlockYAt(0, 0)).thenReturn(64);
        when(world.getWorldBorder()).thenReturn(border);
        when(border.isInside(any(Location.class))).thenReturn(true);
        when(world.getBlockAt(any(Location.class))).thenReturn(feet);
        when(feet.getRelative(0, 1, 0)).thenReturn(head);
        when(feet.getRelative(0, -1, 0)).thenReturn(below);
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
