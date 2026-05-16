package me.mcgg.azreyzaako.mcggrtp.paper.rtp;

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
    public Optional<Location> find(World world, PaperConfig.WorldRtpSettings settings) {
        return search(world, settings).location();
    }

    SearchResult search(World world, PaperConfig.WorldRtpSettings settings) {
        if (!settings.enabled()) {
            return new SearchResult(Optional.empty(), 0);
        }

        SearchContext context = SearchContext.create(world, settings);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int attempt = 1; attempt <= context.maxAttempts(); attempt++) {
            int radius = random.nextInt(context.minRadius(), context.maxRadius() + 1);
            double angle = random.nextDouble(0.0D, Math.PI * 2.0D);
            int x = context.centerX() + (int) Math.round(Math.cos(angle) * radius);
            int z = context.centerZ() + (int) Math.round(Math.sin(angle) * radius);

            if (context.environment() == World.Environment.NETHER) {
                Optional<Location> candidate = scanNetherColumn(context, x, z);
                if (candidate.isPresent()) {
                    return new SearchResult(candidate, attempt);
                }
                continue;
            }

            int y = world.getHighestBlockYAt(x, z) + 1;
            if (isSafe(context, x, y, z)) {
                return new SearchResult(Optional.of(toLocation(world, x, y, z)), attempt);
            }
        }
        return new SearchResult(Optional.empty(), context.maxAttempts());
    }

    private Optional<Location> scanNetherColumn(SearchContext context, int x, int z) {
        for (int y = context.netherMaxY(); y > context.minHeight() + 1; y--) {
            if (isSafe(context, x, y, z)) {
                return Optional.of(toLocation(context.world(), x, y, z));
            }
        }
        return Optional.empty();
    }

    private boolean isSafe(SearchContext context, int x, int y, int z) {
        if (!isInsideBorder(context, x, y, z)) {
            return false;
        }

        Block feet = context.world().getBlockAt(x, y, z);
        Block head = context.world().getBlockAt(x, y + 1, z);
        Block below = context.world().getBlockAt(x, y - 1, z);
        Material floorType = below.getType();
        Material feetType = feet.getType();
        Biome biome = feet.getBiome();

        // The search accepts only places where the player can stand immediately
        // after teleporting without being clipped into blocks or dropped into hazards.
        return floorType.isSolid()
                && feet.isPassable()
                && head.isPassable()
                && !context.unsafeBlocks().contains(floorType)
                && !context.unsafeBlocks().contains(feetType)
                && !context.blacklistedBiomes().contains(biome)
                && floorType != Material.BEDROCK;
    }

    private boolean isInsideBorder(SearchContext context, int x, int y, int z) {
        Location probe = context.borderProbe();
        probe.set(x + 0.5D, y, z + 0.5D);
        return context.worldBorder().isInside(probe);
    }

    private Location toLocation(World world, int x, int y, int z) {
        return new Location(world, x + 0.5D, y, z + 0.5D);
    }

    record SearchResult(Optional<Location> location, int attempts) {
    }

    private record SearchContext(
            World world,
            WorldBorder worldBorder,
            World.Environment environment,
            int centerX,
            int centerZ,
            int minRadius,
            int maxRadius,
            int maxAttempts,
            int minHeight,
            int netherMaxY,
            Set<Biome> blacklistedBiomes,
            Set<Material> unsafeBlocks,
            Location borderProbe
    ) {
        private static SearchContext create(World world, PaperConfig.WorldRtpSettings settings) {
            int maxHeight = world.getMaxHeight() - 2;
            int netherMaxY = settings.avoidBedrockRoof() ? Math.min(maxHeight, 120) : maxHeight;
            return new SearchContext(
                    world,
                    world.getWorldBorder(),
                    world.getEnvironment(),
                    settings.centerX(),
                    settings.centerZ(),
                    settings.minRadius(),
                    settings.maxRadius(),
                    settings.maxAttempts(),
                    world.getMinHeight(),
                    netherMaxY,
                    settings.blacklistedBiomes(),
                    settings.unsafeBlocks(),
                    new Location(world, 0.5D, world.getMinHeight(), 0.5D)
            );
        }
    }
}
