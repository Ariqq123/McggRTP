package me.mcgg.azreyzaako.mcggrtp.paper.rtp;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import me.mcgg.azreyzaako.mcggrtp.paper.config.PaperConfig;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;

public final class SafeLocationFinder {
    public SearchPlan createPlan(World world, PaperConfig.WorldRtpSettings settings) {
        if (!settings.enabled()) {
            return new SearchPlan(
                    List.of(),
                    List.of(),
                    world.getEnvironment(),
                    world.getMinHeight(),
                    world.getMaxHeight() - 2,
                    0,
                    new Location(world, 0.5D, world.getMinHeight(), 0.5D)
            );
        }

        ThreadLocalRandom random = ThreadLocalRandom.current();
        List<Candidate> generatedFirst = new ArrayList<>(settings.maxAttempts());
        List<Candidate> fallback = new ArrayList<>(settings.maxAttempts());
        for (int attempt = 0; attempt < settings.maxAttempts(); attempt++) {
            int radius = random.nextInt(settings.minRadius(), settings.maxRadius() + 1);
            double angle = random.nextDouble(0.0D, Math.PI * 2.0D);
            int x = settings.centerX() + (int) Math.round(Math.cos(angle) * radius);
            int z = settings.centerZ() + (int) Math.round(Math.sin(angle) * radius);
            Candidate candidate = new Candidate(x, z, x >> 4, z >> 4);
            if (world.isChunkGenerated(candidate.chunkX(), candidate.chunkZ())) {
                generatedFirst.add(candidate);
            } else {
                fallback.add(candidate);
            }
        }

        return new SearchPlan(
                List.copyOf(generatedFirst),
                List.copyOf(fallback),
                world.getEnvironment(),
                world.getMinHeight(),
                settings.avoidBedrockRoof() ? Math.min(world.getMaxHeight() - 2, 120) : world.getMaxHeight() - 2,
                settings.maxAttempts(),
                new Location(world, 0.5D, world.getMinHeight(), 0.5D)
        );
    }

    public Optional<Location> validate(World world, PaperConfig.WorldRtpSettings settings, SearchPlan plan, Candidate candidate) {
        return plan.environment() == World.Environment.NETHER
                ? scanNetherColumn(world, settings, plan, candidate)
                : findSurfaceLocation(world, settings, plan, candidate);
    }

    private Optional<Location> findSurfaceLocation(World world,
                                                   PaperConfig.WorldRtpSettings settings,
                                                   SearchPlan plan,
                                                   Candidate candidate) {
        int y = world.getHighestBlockYAt(candidate.x(), candidate.z()) + 1;
        if (isSafe(world, settings, plan, candidate.x(), y, candidate.z())) {
            return Optional.of(toLocation(world, candidate.x(), y, candidate.z()));
        }
        return Optional.empty();
    }

    private Optional<Location> scanNetherColumn(World world,
                                                PaperConfig.WorldRtpSettings settings,
                                                SearchPlan plan,
                                                Candidate candidate) {
        for (int y = plan.netherMaxY(); y > plan.minHeight() + 1; y--) {
            if (isSafe(world, settings, plan, candidate.x(), y, candidate.z())) {
                return Optional.of(toLocation(world, candidate.x(), y, candidate.z()));
            }
        }
        return Optional.empty();
    }

    private boolean isSafe(World world, PaperConfig.WorldRtpSettings settings, SearchPlan plan, int x, int y, int z) {
        if (!isInsideBorder(world.getWorldBorder(), plan, x, y, z)) {
            return false;
        }

        Block feet = world.getBlockAt(x, y, z);
        Block head = world.getBlockAt(x, y + 1, z);
        Block below = world.getBlockAt(x, y - 1, z);
        Material floorType = below.getType();
        Material feetType = feet.getType();
        Biome biome = feet.getBiome();

        return floorType.isSolid()
                && feet.isPassable()
                && head.isPassable()
                && !settings.unsafeBlocks().contains(floorType)
                && !settings.unsafeBlocks().contains(feetType)
                && !settings.blacklistedBiomes().contains(biome)
                && floorType != Material.BEDROCK;
    }

    private boolean isInsideBorder(WorldBorder worldBorder, SearchPlan plan, int x, int y, int z) {
        Location probe = plan.borderProbe();
        probe.set(x + 0.5D, y, z + 0.5D);
        return worldBorder.isInside(probe);
    }

    private Location toLocation(World world, int x, int y, int z) {
        return new Location(world, x + 0.5D, y, z + 0.5D);
    }

    public record Candidate(int x, int z, int chunkX, int chunkZ) {
    }

    public record SearchPlan(
            List<Candidate> generatedFirst,
            List<Candidate> fallback,
            World.Environment environment,
            int minHeight,
            int netherMaxY,
            int maxAttempts,
            Location borderProbe
    ) {
    }
}
