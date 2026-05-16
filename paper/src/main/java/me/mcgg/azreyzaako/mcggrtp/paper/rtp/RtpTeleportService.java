package me.mcgg.azreyzaako.mcggrtp.paper.rtp;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
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
    private final McggRTPPaper plugin;
    private final PaperConfig config;
    private final MessageBundle messages;
    private final PaperMessageBridge messageBridge;
    private final SafeLocationFinder safeLocationFinder;
    private final int maxConcurrentSearches;
    private final Deque<TeleportJob> queuedSearches = new ArrayDeque<>();
    private int activeSearches;

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
        this.maxConcurrentSearches = Math.max(1, config.maxConcurrentSearches());
    }

    public void beginLocalTeleport(Player player, String worldName, String dimension) {
        submit(new TeleportJob(player, "", worldName, false));
    }

    public void beginPendingTeleport(Player player, String requestId, String worldName, String dimension) {
        submit(new TeleportJob(player, requestId, worldName, true));
    }

    private void submit(TeleportJob job) {
        int queuePosition = 0;
        boolean startImmediately = false;
        synchronized (this) {
            if (activeSearches < maxConcurrentSearches) {
                activeSearches++;
                startImmediately = true;
            } else {
                queuedSearches.addLast(job);
                queuePosition = queuedSearches.size();
            }
        }

        if (startImmediately) {
            plugin.debug("Starting RTP job immediately player=%s requestId=%s active=%d limit=%d",
                    job.player().getUniqueId(),
                    job.requestId(),
                    activeSearches,
                    maxConcurrentSearches);
            doTeleport(job);
            return;
        }

        plugin.debug("Queueing RTP job player=%s requestId=%s position=%d active=%d limit=%d",
                job.player().getUniqueId(),
                job.requestId(),
                queuePosition,
                activeSearches,
                maxConcurrentSearches);
        if (job.player().isOnline() && job.player().isValid()) {
            job.player().sendMessage(messages.text("search-queued", "{position}", String.valueOf(queuePosition)));
        }
    }

    private void doTeleport(TeleportJob job) {
        Player player = job.player();
        String requestId = job.requestId();
        String worldName = job.worldName();
        boolean clearPending = job.clearPending();
        World world = plugin.getServer().getWorld(worldName);
        PaperConfig.WorldRtpSettings settings = config.worlds().get(worldName);
        plugin.debug("Starting RTP search player=%s requestId=%s world=%s clearPending=%s",
                player.getUniqueId(),
                requestId,
                worldName,
                clearPending);
        if (world == null || settings == null) {
            plugin.debug("Aborting RTP search player=%s requestId=%s world=%s reason=world-unavailable",
                    player.getUniqueId(),
                    requestId,
                    worldName);
            player.sendMessage(messages.text("world-unavailable"));
            if (clearPending) {
                messageBridge.sendResult(player, new RtpResult(requestId, player.getUniqueId(), false, "Target world unavailable."));
                messageBridge.sendClearPending(player, requestId);
            }
            finishJob(job);
            return;
        }

        player.sendMessage(messages.text("searching"));
        long startedAt = System.nanoTime();
        SafeLocationFinder.SearchPlan plan = safeLocationFinder.createPlan(world, settings);
        processCandidates(
                job,
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

    private void processCandidates(TeleportJob job,
                                   World world,
                                   PaperConfig.WorldRtpSettings settings,
                                   SafeLocationFinder.SearchPlan plan,
                                   List<SafeLocationFinder.Candidate> primary,
                                   List<SafeLocationFinder.Candidate> fallback,
                                   int primaryIndex,
                                   int fallbackIndex,
                                   boolean clearPending,
                                   long startedAt) {
        Player player = job.player();
        String requestId = job.requestId();
        String worldName = job.worldName();
        if (!player.isOnline() || !player.isValid()) {
            plugin.debug("Stopping RTP candidate processing player=%s requestId=%s reason=player-offline-or-invalid",
                    player.getUniqueId(),
                    requestId);
            finishJob(job);
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
            plugin.debug("RTP search exhausted player=%s requestId=%s world=%s attempts=%d",
                    player.getUniqueId(),
                    requestId,
                    worldName,
                    plan.maxAttempts());
            player.sendMessage(messages.text("teleport-failed"));
            if (clearPending) {
                messageBridge.sendResult(player, new RtpResult(requestId, player.getUniqueId(), false, "Could not find a safe location."));
                messageBridge.sendClearPending(player, requestId);
            }
            finishJob(job);
            return;
        }

        CompletableFuture<Chunk> chunkFuture = world.getChunkAtAsync(candidate.chunkX(), candidate.chunkZ(), generateChunk);
        plugin.debug("Loading RTP chunk player=%s requestId=%s world=%s chunkX=%d chunkZ=%d generate=%s attempt=%d",
                player.getUniqueId(),
                requestId,
                worldName,
                candidate.chunkX(),
                candidate.chunkZ(),
                generateChunk,
                attempts);
        chunkFuture.whenComplete((chunk, throwable) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (throwable != null) {
                plugin.debug("RTP chunk load failed player=%s requestId=%s world=%s error=%s",
                        player.getUniqueId(),
                        requestId,
                        worldName,
                        throwable.getClass().getSimpleName());
                player.sendMessage(messages.text("teleport-failed"));
                if (clearPending) {
                    messageBridge.sendResult(player, new RtpResult(requestId, player.getUniqueId(), false, "Chunk load failed."));
                    messageBridge.sendClearPending(player, requestId);
                }
                finishJob(job);
                return;
            }
            if (!player.isOnline() || !player.isValid()) {
                plugin.debug("Aborting RTP completion player=%s requestId=%s reason=player-offline-or-invalid-after-chunk-load",
                        player.getUniqueId(),
                        requestId);
                finishJob(job);
                return;
            }

            Optional<Location> destination = safeLocationFinder.validate(world, settings, plan, candidate);
            if (destination.isPresent()) {
                logSearch(worldName, attempts, System.nanoTime() - startedAt, generatedFirstHit);
                plugin.debug("Validated RTP destination player=%s requestId=%s world=%s x=%.1f y=%.1f z=%.1f attempt=%d mode=%s",
                        player.getUniqueId(),
                        requestId,
                        worldName,
                        destination.get().getX(),
                        destination.get().getY(),
                        destination.get().getZ(),
                        attempts,
                        generatedFirstHit ? "generated-first" : "fallback");
                completeTeleport(job, destination.get());
                return;
            }

            int nextPrimary = generatedFirstHit ? primaryIndex + 1 : primaryIndex;
            int nextFallback = generatedFirstHit ? fallbackIndex : fallbackIndex + 1;
            processCandidates(
                    job,
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

    private void completeTeleport(TeleportJob job, Location destination) {
        Player player = job.player();
        String requestId = job.requestId();
        boolean clearPending = job.clearPending();
        player.teleportAsync(destination).thenAccept(success -> {
            if (!player.isOnline() || !player.isValid()) {
                plugin.debug("Discarding teleport completion player=%s requestId=%s reason=player-offline-or-invalid-after-teleport",
                        player.getUniqueId(),
                        requestId);
                finishJob(job);
                return;
            }

            plugin.debug("Teleport completion player=%s requestId=%s success=%s x=%.1f y=%.1f z=%.1f",
                    player.getUniqueId(),
                    requestId,
                    success,
                    destination.getX(),
                    destination.getY(),
                    destination.getZ());
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
            } else {
                if (clearPending) {
                    messageBridge.sendResult(player, new RtpResult(requestId, player.getUniqueId(), false, "Teleport failed."));
                    messageBridge.sendClearPending(player, requestId);
                }
            }
            finishJob(job);
        }).exceptionally(throwable -> {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                plugin.debug("Teleport future failed player=%s requestId=%s error=%s",
                        player.getUniqueId(),
                        requestId,
                        throwable.getClass().getSimpleName());
                if (clearPending && player.isOnline()) {
                    messageBridge.sendResult(player, new RtpResult(requestId, player.getUniqueId(), false, "Teleport failed."));
                    messageBridge.sendClearPending(player, requestId);
                }
                finishJob(job);
            });
            return null;
        });
    }

    private void logSearch(String worldName, int attempts, long elapsedNanos, boolean generatedFirstHit) {
        if (!plugin.debugEnabled()) {
            return;
        }

        double elapsedMillis = elapsedNanos / 1_000_000.0D;
        plugin.debug(
                "RTP search timing world=%s attempts=%d durationMs=%.3f mode=%s",
                worldName,
                attempts,
                elapsedMillis,
                generatedFirstHit ? "generated-first" : "fallback"
        );
    }

    private void finishJob(TeleportJob completedJob) {
        TeleportJob nextJob = null;
        int remainingQueue;
        synchronized (this) {
            activeSearches = Math.max(0, activeSearches - 1);
            while (!queuedSearches.isEmpty()) {
                TeleportJob candidate = queuedSearches.removeFirst();
                if (candidate.player().isOnline() && candidate.player().isValid()) {
                    activeSearches++;
                    nextJob = candidate;
                    break;
                }
            }
            remainingQueue = queuedSearches.size();
        }

        plugin.debug("Finished RTP job player=%s requestId=%s active=%d queued=%d",
                completedJob.player().getUniqueId(),
                completedJob.requestId(),
                activeSearches,
                remainingQueue);
        if (nextJob != null) {
            plugin.debug("Dequeued RTP job player=%s requestId=%s active=%d queued=%d",
                    nextJob.player().getUniqueId(),
                    nextJob.requestId(),
                    activeSearches,
                    remainingQueue);
            doTeleport(nextJob);
        }
    }

    private record TeleportJob(Player player, String requestId, String worldName, boolean clearPending) {
    }
}
