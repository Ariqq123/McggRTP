package me.mcgg.azreyzaako.mcggrtp.paper.rtp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Set;
import me.mcgg.azreyzaako.mcggrtp.paper.config.PaperConfig;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.block.Biome;
import org.junit.jupiter.api.Test;

class SafeLocationFinderTest {
    private final SafeLocationFinder finder = new SafeLocationFinder();

    @Test
    void findsSafeLocationInOverworld() {
        World world = mock(World.class);
        WorldBorder border = mock(WorldBorder.class);
        Block feet = passableBlock(Biome.PLAINS, Material.AIR);
        Block head = passableBlock(Biome.PLAINS, Material.AIR);
        Block below = solidBlock(Material.STONE);
        when(world.getEnvironment()).thenReturn(World.Environment.NORMAL);
        when(world.getHighestBlockYAt(0, 0)).thenReturn(64);
        when(world.getWorldBorder()).thenReturn(border);
        when(border.isInside(org.mockito.ArgumentMatchers.any(Location.class))).thenReturn(true);
        when(world.getBlockAt(any(Location.class))).thenReturn(feet);
        when(feet.getRelative(0, 1, 0)).thenReturn(head);
        when(feet.getRelative(0, -1, 0)).thenReturn(below);

        var settings = settings(false, Set.of(), Set.of(Material.LAVA));
        var location = finder.find(world, settings);

        assertTrue(location.isPresent());
        assertEquals(65.0D, location.get().getY());
    }

    @Test
    void findsSafeLocationInNetherBelowBedrockRoof() {
        World world = mock(World.class);
        WorldBorder border = mock(WorldBorder.class);
        when(world.getEnvironment()).thenReturn(World.Environment.NETHER);
        when(world.getMaxHeight()).thenReturn(256);
        when(world.getMinHeight()).thenReturn(0);
        when(world.getWorldBorder()).thenReturn(border);
        when(border.isInside(org.mockito.ArgumentMatchers.any(Location.class))).thenReturn(true);
        Block unsafeFeet = unsafeFeet(Material.LAVA);
        Block safeFeet = passableBlock(Biome.NETHER_WASTES, Material.AIR);
        Block safeHead = passableBlock(Biome.NETHER_WASTES, Material.AIR);
        Block safeBelow = solidBlock(Material.NETHERRACK);
        when(world.getBlockAt(any(Location.class))).thenAnswer(invocation -> {
            Location location = invocation.getArgument(0);
            return location.getBlockY() == 119 ? safeFeet : unsafeFeet;
        });
        when(safeFeet.getRelative(0, 1, 0)).thenReturn(safeHead);
        when(safeFeet.getRelative(0, -1, 0)).thenReturn(safeBelow);

        var settings = settings(true, Set.of(), Set.of(Material.LAVA));
        var location = finder.find(world, settings);

        assertTrue(location.isPresent());
        assertEquals(119.0D, location.get().getY());
    }

    @Test
    void findsSafeLocationInEndLikeNormalWorld() {
        World world = mock(World.class);
        WorldBorder border = mock(WorldBorder.class);
        Block feet = passableBlock(Biome.END_HIGHLANDS, Material.AIR);
        Block head = passableBlock(Biome.END_HIGHLANDS, Material.AIR);
        Block below = solidBlock(Material.END_STONE);
        when(world.getEnvironment()).thenReturn(World.Environment.THE_END);
        when(world.getHighestBlockYAt(0, 0)).thenReturn(70);
        when(world.getWorldBorder()).thenReturn(border);
        when(border.isInside(org.mockito.ArgumentMatchers.any(Location.class))).thenReturn(true);
        when(world.getBlockAt(any(Location.class))).thenReturn(feet);
        when(feet.getRelative(0, 1, 0)).thenReturn(head);
        when(feet.getRelative(0, -1, 0)).thenReturn(below);

        var settings = settings(false, Set.of(), Set.of(Material.LAVA));
        var location = finder.find(world, settings);

        assertTrue(location.isPresent());
        assertEquals(71.0D, location.get().getY());
    }

    private PaperConfig.WorldRtpSettings settings(boolean avoidBedrockRoof, Set<Biome> blacklistedBiomes, Set<Material> unsafeBlocks) {
        return new PaperConfig.WorldRtpSettings(true, 0, 0, 0, 0, 2, avoidBedrockRoof, blacklistedBiomes, unsafeBlocks);
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

    private Block unsafeFeet(Material type) {
        Block block = mock(Block.class);
        Block head = passableBlock(Biome.NETHER_WASTES, Material.AIR);
        Block below = solidBlock(type);
        when(block.isPassable()).thenReturn(true);
        when(block.getBiome()).thenReturn(Biome.NETHER_WASTES);
        when(block.getType()).thenReturn(Material.AIR);
        when(block.getRelative(0, 1, 0)).thenReturn(head);
        when(block.getRelative(0, -1, 0)).thenReturn(below);
        return block;
    }
}
