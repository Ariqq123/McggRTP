package me.mcgg.azreyzaako.mcggrtp.paper.rtp;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import me.mcgg.azreyzaako.mcggrtp.common.RtpResult;
import me.mcgg.azreyzaako.mcggrtp.paper.McggRTPPaper;
import me.mcgg.azreyzaako.mcggrtp.paper.MessageBundle;
import me.mcgg.azreyzaako.mcggrtp.paper.config.PaperConfig;
import me.mcgg.azreyzaako.mcggrtp.paper.messaging.PaperMessageBridge;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

public final class RtpTeleportService {
    private static final boolean DEBUG_SEARCH_LOGS = false;

    private final McggRTPPaper plugin;
    private final PaperConfig config;
    private final MessageBundle messages;
    private final PaperMessageBridge messageBridge;
    private final SafeLocationFinder safeLocationFinder;

    public RtpTeleportService(McggRTPPaper plugin, PaperConfig config, MessageBundle messages, PaperMessageBridge messageBridge) {
        this(plugin, config, messages, messageBridge, new SafeLocationFinder());
    }

    RtpTeleportService(McggRTPPaper plugin,
                       PaperConfig config,
                       MessageBundle messages,
                       PaperMessageBridge messageBridge,
                       SafeLocationFinder safeLocationFinder) {
        this.plugin = plugin;
        this.config = config;
        this.messages = messages;
        this.messageBridge = messageBridge;
        this.safeLocationFinder = safeLocationFinder;
    }

    public void beginLocalTeleport(Player player, String worldName, String dimension) {
        doTeleport(player, "", worldName, false);
    }

    public void beginPendingTeleport(Player player, String requestId, String worldName, String dimension) {
        doTeleport(player, requestId, worldName, true);
    }

    private void doTeleport(Player player, String requestId, String worldName, boolean clearPending) {
        World world = plugin.getServer().getWorld(worldName);
        PaperConfig.WorldRtpSettings settings = config.worlds().get(worldName);
        if (world == null || settings == null) {
            player.sendMessage(messages.text("world-unavailable"));
            if (clearPending) {
                messageBridge.sendResult(player, new RtpResult(requestId, player.getUniqueId(), false, "Target world unavailable."));
                messageBridge.sendClearPending(player, requestId);
            }
            return;
        }

        player.sendMessage(messages.text("searching"));
        long startedAt = System.nanoTime();
        SafeLocationFinder.SearchPlan plan = safeLocationFinder.createPlan(world, settings);
        processCandidates(
                player,
                requestId,
                worldName,
                world,
                settings,
                plan,
                plan.generatedFirst(),
                plan.fallback(),
                0,
                0,
                clearPending,
                startedAt
        );
    }

    private void processCandidates(Player player,
                                   String requestId,
                                   String worldName,
                                   World world,
                                   PaperConfig.WorldRtpSettings settings,
                                   SafeLocationFinder.SearchPlan plan,
                                   List<SafeLocationFinder.Candidate> primary,
                                   List<SafeLocationFinder.Candidate> fallback,
                                   int primaryIndex,
                                   int fallbackIndex,
                                   boolean clearPending,
                                   long startedAt) {
        if (!player.isOnline() || !player.isValid()) {
            return;
        }

        SafeLocationFinder.Candidate candidate;
        boolean generatedFirstHit;
        boolean generateChunk;
        int attempts;
        if (primaryIndex < primary.size()) {
            candidate = primary.get(primaryIndex);
            generatedFirstHit = true;
            generateChunk = false;
            attempts = primaryIndex + 1;
        } else if (fallbackIndex < fallback.size()) {
            candidate = fallback.get(fallbackIndex);
            generatedFirstHit = false;
            generateChunk = true;
            attempts = primary.size() + fallbackIndex + 1;
        } else {
            logSearch(worldName, plan.maxAttempts(), System.nanoTime() - startedAt, false);
            player.sendMessage(messages.text("teleport-failed"));
            if (clearPending) {
                messageBridge.sendResult(player, new RtpResult(requestId, player.getUniqueId(), false, "Could not find a safe location."));
                messageBridge.sendClearPending(player, requestId);
            }
            return;
        }

        CompletableFuture<Chunk> chunkFuture = world.getChunkAtAsync(candidate.chunkX(), candidate.chunkZ(), generateChunk);
        chunkFuture.thenAccept(chunk -> plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || !player.isValid()) {
                return;
            }

            Optional<Location> destination = safeLocationFinder.validate(world, settings, plan, candidate);
            if (destination.isPresent()) {
                logSearch(worldName, attempts, System.nanoTime() - startedAt, generatedFirstHit);
                completeTeleport(player, requestId, destination.get(), clearPending);
                return;
            }

            int nextPrimary = generatedFirstHit ? primaryIndex + 1 : primaryIndex;
            int nextFallback = generatedFirstHit ? fallbackIndex : fallbackIndex + 1;
            processCandidates(
                    player,
                    requestId,
                    worldName,
                    world,
                    settings,
                    plan,
                    primary,
                    fallback,
                    nextPrimary,
                    nextFallback,
                    clearPending,
                    startedAt
            );
        }));
    }

    private void completeTeleport(Player player, String requestId, Location destination, boolean clearPending) {
        player.teleportAsync(destination).thenAccept(success -> {
            if (!player.isOnline() || !player.isValid()) {
                return;
            }

            if (success) {
                if (!clearPending) {
                    messageBridge.sendResult(player, new RtpResult("", player.getUniqueId(), true, ""));
                }
                player.playSound(player.getLocation(), plugin.configModel().sounds().teleportSuccess(), 1.0F, 1.0F);
                player.sendMessage(messages.text("teleport-success"));
                if (clearPending) {
                    messageBridge.sendResult(player, new RtpResult(requestId, player.getUniqueId(), true, ""));
                    messageBridge.sendClearPending(player, requestId);
                }
            } else if (clearPending) {
                messageBridge.sendResult(player, new RtpResult(requestId, player.getUniqueId(), false, "Teleport failed."));
                messageBridge.sendClearPending(player, requestId);
            }
        });
    }

    private void logSearch(String worldName, int attempts, long elapsedNanos, boolean generatedFirstHit) {
        if (!DEBUG_SEARCH_LOGS && !plugin.getLogger().isLoggable(Level.FINE)) {
            return;
        }

        double elapsedMillis = elapsedNanos / 1_000_000.0D;
        plugin.getLogger().fine(String.format(
                "RTP search world=%s attempts=%d durationMs=%.3f mode=%s",
                worldName,
                attempts,
                elapsedMillis,
                generatedFirstHit ? "generated-first" : "fallback"
        ));
    }
}
