package me.mcgg.azreyzaako.mcggrtp.paper.rtp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Set;
import me.mcgg.azreyzaako.mcggrtp.paper.config.PaperConfig;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.junit.jupiter.api.Test;

class SafeLocationFinderTest {
    private final SafeLocationFinder finder = new SafeLocationFinder();

    @Test
    void findsSafeLocationInOverworld() {
        World world = world(World.Environment.NORMAL, 0, 256, 64);
        Block feet = passableBlock(Biome.PLAINS, Material.AIR);
        Block head = passableBlock(Biome.PLAINS, Material.AIR);
        Block below = solidBlock(Material.STONE);
        stubColumn(world, 0, 0, 65, below, feet, head);

        var settings = settings(true, false, Set.of(), Set.of(Material.LAVA));
        var location = finder.find(world, settings);

        assertTrue(location.isPresent());
        assertEquals(65.0D, location.get().getY());
    }

    @Test
    void findsSafeLocationInNetherBelowBedrockRoof() {
        World world = world(World.Environment.NETHER, 0, 256, 0);
        Block unsafeFeet = passableBlock(Biome.NETHER_WASTES, Material.AIR);
        Block unsafeHead = passableBlock(Biome.NETHER_WASTES, Material.AIR);
        Block unsafeBelow = solidBlock(Material.LAVA);
        Block safeFeet = passableBlock(Biome.NETHER_WASTES, Material.AIR);
        Block safeHead = passableBlock(Biome.NETHER_WASTES, Material.AIR);
        Block safeBelow = solidBlock(Material.NETHERRACK);
        stubColumn(world, 0, 0, 120, unsafeBelow, unsafeFeet, unsafeHead);
        stubColumn(world, 0, 0, 119, safeBelow, safeFeet, safeHead);

        var settings = settings(true, true, Set.of(), Set.of(Material.LAVA));
        var location = finder.find(world, settings);

        assertTrue(location.isPresent());
        assertEquals(119.0D, location.get().getY());
    }

    @Test
    void rejectsUnsafeFloorOrFeetBlocks() {
        World world = world(World.Environment.NORMAL, 0, 256, 64);
        Block feet = passableBlock(Biome.PLAINS, Material.LAVA);
        Block head = passableBlock(Biome.PLAINS, Material.AIR);
        Block below = solidBlock(Material.STONE);
        stubColumn(world, 0, 0, 65, below, feet, head);

        var settings = settings(true, false, Set.of(), Set.of(Material.LAVA));

        assertTrue(finder.find(world, settings).isEmpty());
    }

    @Test
    void rejectsBlacklistedBiome() {
        World world = world(World.Environment.NORMAL, 0, 256, 64);
        Block feet = passableBlock(Biome.DESERT, Material.AIR);
        Block head = passableBlock(Biome.DESERT, Material.AIR);
        Block below = solidBlock(Material.STONE);
        stubColumn(world, 0, 0, 65, below, feet, head);

        var settings = settings(true, false, Set.of(Biome.DESERT), Set.of(Material.LAVA));

        assertTrue(finder.find(world, settings).isEmpty());
    }

    @Test
    void returnsEmptyWhenWorldIsDisabled() {
        World world = world(World.Environment.NORMAL, 0, 256, 64);

        var settings = settings(false, false, Set.of(), Set.of(Material.LAVA));

        assertTrue(finder.find(world, settings).isEmpty());
    }

    private World world(World.Environment environment, int minHeight, int maxHeight, int highestY) {
        World world = mock(World.class);
        WorldBorder border = mock(WorldBorder.class);
        when(world.getEnvironment()).thenReturn(environment);
        when(world.getMinHeight()).thenReturn(minHeight);
        when(world.getMaxHeight()).thenReturn(maxHeight);
        when(world.getHighestBlockYAt(anyInt(), anyInt())).thenReturn(highestY);
        when(world.getWorldBorder()).thenReturn(border);
        when(border.isInside(any(Location.class))).thenReturn(true);
        Block defaultBlock = solidBlock(Material.BEDROCK);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(defaultBlock);
        return world;
    }

    private void stubColumn(World world, int x, int z, int y, Block below, Block feet, Block head) {
        when(world.getBlockAt(x, y - 1, z)).thenReturn(below);
        when(world.getBlockAt(x, y, z)).thenReturn(feet);
        when(world.getBlockAt(x, y + 1, z)).thenReturn(head);
    }

    private PaperConfig.WorldRtpSettings settings(boolean enabled,
                                                  boolean avoidBedrockRoof,
                                                  Set<Biome> blacklistedBiomes,
                                                  Set<Material> unsafeBlocks) {
        return new PaperConfig.WorldRtpSettings(enabled, 0, 0, 0, 0, 2, avoidBedrockRoof, blacklistedBiomes, unsafeBlocks);
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
