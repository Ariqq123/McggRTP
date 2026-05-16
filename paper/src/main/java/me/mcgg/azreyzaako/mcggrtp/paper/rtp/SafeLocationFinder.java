package me.mcgg.azreyzaako.mcggrtp.paper.rtp;

import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import me.mcgg.azreyzaako.mcggrtp.paper.config.PaperConfig;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Biome;

public final class SafeLocationFinder {
    public Optional<Location> find(World world, PaperConfig.WorldRtpSettings settings) {
        if (!settings.enabled()) {
            return Optional.empty();
        }

        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < settings.maxAttempts(); attempt++) {
            Location candidate = randomCandidate(world, settings, random);
            if (candidate == null || !world.getWorldBorder().isInside(candidate)) {
                continue;
            }
            if (isSafe(candidate, settings)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    private Location randomCandidate(World world, PaperConfig.WorldRtpSettings settings, ThreadLocalRandom random) {
        int radius = random.nextInt(settings.minRadius(), settings.maxRadius() + 1);
        double angle = random.nextDouble(0.0, Math.PI * 2);
        int x = settings.centerX() + (int) Math.round(Math.cos(angle) * radius);
        int z = settings.centerZ() + (int) Math.round(Math.sin(angle) * radius);

        if (world.getEnvironment() == World.Environment.NETHER) {
            return scanNetherColumn(world, x, z, settings);
        }

        int y = world.getHighestBlockYAt(x, z) + 1;
        return new Location(world, x + 0.5D, y, z + 0.5D);
    }

    private Location scanNetherColumn(World world, int x, int z, PaperConfig.WorldRtpSettings settings) {
        int maxY = settings.avoidBedrockRoof() ? Math.min(world.getMaxHeight() - 2, 120) : world.getMaxHeight() - 2;
        for (int y = maxY; y > world.getMinHeight() + 1; y--) {
            Location location = new Location(world, x + 0.5D, y, z + 0.5D);
            if (isSafe(location, settings)) {
                return location;
            }
        }
        return null;
    }

    private boolean isSafe(Location location, PaperConfig.WorldRtpSettings settings) {
        Block feet = location.getBlock();
        Block head = feet.getRelative(0, 1, 0);
        Block below = feet.getRelative(0, -1, 0);
        Biome biome = feet.getBiome();

        // The search accepts only places where the player can stand immediately
        // after teleporting without being clipped into blocks or dropped into hazards.
        return below.getType().isSolid()
                && feet.isPassable()
                && head.isPassable()
                && !settings.unsafeBlocks().contains(below.getType())
                && !settings.unsafeBlocks().contains(feet.getType())
                && !settings.blacklistedBiomes().contains(biome)
                && below.getType() != Material.BEDROCK;
    }
}
