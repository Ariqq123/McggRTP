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
    void createPlanPrioritizesGeneratedChunks() {
        World world = world(World.Environment.NORMAL, 0, 256, 64);
        when(world.isChunkGenerated(anyInt(), anyInt())).thenAnswer(invocation -> {
            int chunkX = invocation.getArgument(0);
            int chunkZ = invocation.getArgument(1);
            return chunkX == 0 && chunkZ == 0;
        });

        SafeLocationFinder.SearchPlan plan = finder.createPlan(world, settings(true, false, 8, 32, 64, Set.of(), Set.of(Material.LAVA)));

        assertTrue(plan.generatedFirst().stream().allMatch(candidate -> candidate.chunkX() == 0 && candidate.chunkZ() == 0));
        assertTrue(plan.fallback().stream().allMatch(candidate -> candidate.chunkX() != 0 || candidate.chunkZ() != 0));
    }

    @Test
    void validatesSafeLocationInOverworld() {
        World world = world(World.Environment.NORMAL, 0, 256, 64);
        Block feet = passableBlock(Biome.PLAINS, Material.AIR);
        Block head = passableBlock(Biome.PLAINS, Material.AIR);
        Block below = solidBlock(Material.STONE);
        stubColumn(world, 0, 0, 65, below, feet, head);

        SafeLocationFinder.SearchPlan plan = finder.createPlan(world, settings(true, false, 1, 0, 0, Set.of(), Set.of(Material.LAVA)));
        var location = finder.validate(world, settings(true, false, 1, 0, 0, Set.of(), Set.of(Material.LAVA)), plan, new SafeLocationFinder.Candidate(0, 0, 0, 0));

        assertTrue(location.isPresent());
        assertEquals(65.0D, location.get().getY());
    }

    @Test
    void validatesSafeLocationInNetherBelowBedrockRoof() {
        World world = world(World.Environment.NETHER, 0, 256, 0);
        Block unsafeFeet = passableBlock(Biome.NETHER_WASTES, Material.AIR);
        Block unsafeHead = passableBlock(Biome.NETHER_WASTES, Material.AIR);
        Block unsafeBelow = solidBlock(Material.LAVA);
        Block safeFeet = passableBlock(Biome.NETHER_WASTES, Material.AIR);
        Block safeHead = passableBlock(Biome.NETHER_WASTES, Material.AIR);
        Block safeBelow = solidBlock(Material.NETHERRACK);
        stubColumn(world, 0, 0, 120, unsafeBelow, unsafeFeet, unsafeHead);
        stubColumn(world, 0, 0, 119, safeBelow, safeFeet, safeHead);

        SafeLocationFinder.SearchPlan plan = finder.createPlan(world, settings(true, true, 1, 0, 0, Set.of(), Set.of(Material.LAVA)));
        var location = finder.validate(world, settings(true, true, 1, 0, 0, Set.of(), Set.of(Material.LAVA)), plan, new SafeLocationFinder.Candidate(0, 0, 0, 0));

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

        SafeLocationFinder.SearchPlan plan = finder.createPlan(world, settings(true, false, 1, 0, 0, Set.of(), Set.of(Material.LAVA)));

        assertTrue(finder.validate(world, settings(true, false, 1, 0, 0, Set.of(), Set.of(Material.LAVA)), plan, new SafeLocationFinder.Candidate(0, 0, 0, 0)).isEmpty());
    }

    @Test
    void rejectsBlacklistedBiome() {
        World world = world(World.Environment.NORMAL, 0, 256, 64);
        Block feet = passableBlock(Biome.DESERT, Material.AIR);
        Block head = passableBlock(Biome.DESERT, Material.AIR);
        Block below = solidBlock(Material.STONE);
        stubColumn(world, 0, 0, 65, below, feet, head);

        SafeLocationFinder.SearchPlan plan = finder.createPlan(world, settings(true, false, 1, 0, 0, Set.of(Biome.DESERT), Set.of(Material.LAVA)));

        assertTrue(finder.validate(world, settings(true, false, 1, 0, 0, Set.of(Biome.DESERT), Set.of(Material.LAVA)), plan, new SafeLocationFinder.Candidate(0, 0, 0, 0)).isEmpty());
    }

    @Test
    void createPlanReturnsNoCandidatesWhenWorldIsDisabled() {
        World world = world(World.Environment.NORMAL, 0, 256, 64);

        SafeLocationFinder.SearchPlan plan = finder.createPlan(world, settings(false, false, 2, 0, 0, Set.of(), Set.of(Material.LAVA)));

        assertTrue(plan.generatedFirst().isEmpty());
        assertTrue(plan.fallback().isEmpty());
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
        when(world.isChunkGenerated(anyInt(), anyInt())).thenReturn(true);
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
                                                  int maxAttempts,
                                                  int minRadius,
                                                  int maxRadius,
                                                  Set<Biome> blacklistedBiomes,
                                                  Set<Material> unsafeBlocks) {
        return new PaperConfig.WorldRtpSettings(enabled, 0, 0, minRadius, maxRadius, maxAttempts, avoidBedrockRoof, blacklistedBiomes, unsafeBlocks);
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
