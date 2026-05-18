package me.mcgg.azreyzaako.mcggrtp.paper.rtp;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private final PaperConfig.AdaptiveThrottleSettings throttleSettings;
    private final PaperConfig.LocationPoolSettings locationPoolSettings;
    private final Deque<TeleportJob> queuedSearches = new ArrayDeque<>();
    private final Map<String, Deque<Location>> locationPools = new HashMap<>();
    private final Map<String, Boolean> poolRefills = new HashMap<>();
    private final RtpMetrics metrics = new RtpMetrics();
    private int activeSearches;
    private int adaptiveConcurrentSearches;

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
        this.throttleSettings = config.adaptiveThrottle();
        this.locationPoolSettings = config.locationPool();
        this.adaptiveConcurrentSearches = this.maxConcurrentSearches;
        startLocationPoolRefill();
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
        int currentLimit;
        synchronized (this) {
            currentLimit = currentSearchLimit();
            if (activeSearches < currentLimit) {
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
                    currentLimit);
            doTeleport(job);
            return;
        }

        plugin.debug("Queueing RTP job player=%s requestId=%s position=%d active=%d limit=%d",
                job.player().getUniqueId(),
                job.requestId(),
                queuePosition,
                activeSearches,
                currentLimit);
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
        Optional<Location> pooledDestination = takePooledLocation(worldName);
        if (pooledDestination.isPresent()) {
            plugin.debug("Using pooled RTP destination player=%s requestId=%s world=%s remainingPool=%d",
                    player.getUniqueId(),
                    requestId,
                    worldName,
                    poolSize(worldName));
            recordSearch(job, 0, System.nanoTime() - job.startedAtNanos(), true, true);
            completeTeleport(job, pooledDestination.get());
            return;
        }

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
            recordSearch(job, plan.maxAttempts(), System.nanoTime() - startedAt, false, false);
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
                recordSearch(job, attempts, System.nanoTime() - startedAt, generatedFirstHit, true);
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

    private void recordSearch(TeleportJob job, int attempts, long elapsedNanos, boolean generatedFirstHit, boolean success) {
        long queueWaitNanos = Math.max(0L, job.startedAtNanos() - job.queuedAtNanos());
        double elapsedMillis = elapsedNanos / 1_000_000.0D;
        double queueWaitMillis = queueWaitNanos / 1_000_000.0D;
        int completed = metrics.record(elapsedMillis, queueWaitMillis, attempts, generatedFirstHit, success);
        adjustAdaptiveLimit();

        plugin.debug(
                "RTP search timing world=%s attempts=%d durationMs=%.3f queueWaitMs=%.3f mode=%s success=%s limit=%d",
                job.worldName(),
                attempts,
                elapsedMillis,
                queueWaitMillis,
                generatedFirstHit ? "generated-first" : "fallback",
                success,
                currentSearchLimit()
        );
        if (completed % throttleSettings.metricsLogInterval() == 0) {
            plugin.debug(
                    "RTP metrics completed=%d success=%d failure=%d avgSearchMs=%.3f avgQueueMs=%.3f avgAttempts=%.2f generatedFirstRatio=%.2f active=%d queued=%d limit=%d maxLimit=%d",
                    completed,
                    metrics.successCount(),
                    metrics.failureCount(),
                    metrics.averageSearchMillis(),
                    metrics.averageQueueMillis(),
                    metrics.averageAttempts(),
                    metrics.generatedFirstRatio(),
                    activeSearches,
                    queuedSearches.size(),
                    currentSearchLimit(),
                    maxConcurrentSearches
            );
        }
    }

    private void finishJob(TeleportJob completedJob) {
        TeleportJob nextJob = null;
        int remainingQueue;
        synchronized (this) {
            activeSearches = Math.max(0, activeSearches - 1);
            int currentLimit = currentSearchLimit();
            while (!queuedSearches.isEmpty()) {
                TeleportJob candidate = queuedSearches.removeFirst();
                if (activeSearches >= currentLimit) {
                    queuedSearches.addFirst(candidate);
                    break;
                }
                if (candidate.player().isOnline() && candidate.player().isValid()) {
                    activeSearches++;
                    nextJob = candidate.withStartedAt(System.nanoTime());
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
            startQueuedJob(nextJob);
        }
    }

    private int currentSearchLimit() {
        return throttleSettings.enabled() ? adaptiveConcurrentSearches : maxConcurrentSearches;
    }

    private void startLocationPoolRefill() {
        if (!locationPoolSettings.enabled() || locationPoolSettings.targetSize() <= 0) {
            return;
        }
        plugin.getServer().getScheduler().runTaskTimer(
                plugin,
                this::refillLocationPools,
                Math.min(20, locationPoolSettings.refillIntervalTicks()),
                locationPoolSettings.refillIntervalTicks()
        );
    }

    private void refillLocationPools() {
        if (!locationPoolSettings.enabled()) {
            return;
        }
        synchronized (this) {
            if (activeSearches > 0 || !queuedSearches.isEmpty()) {
                return;
            }
        }
        for (Map.Entry<String, PaperConfig.WorldRtpSettings> entry : config.worlds().entrySet()) {
            String worldName = entry.getKey();
            PaperConfig.WorldRtpSettings settings = entry.getValue();
            World world = plugin.getServer().getWorld(worldName);
            if (world == null || settings == null || !settings.enabled()) {
                continue;
            }
            synchronized (this) {
                if (poolSize(worldName) >= locationPoolSettings.targetSize() || poolRefills.getOrDefault(worldName, false)) {
                    continue;
                }
                poolRefills.put(worldName, true);
            }
            SafeLocationFinder.SearchPlan plan = safeLocationFinder.createPlan(world, settings);
            refillFromCandidates(worldName, world, settings, plan, plan.generatedFirst(), plan.fallback(), 0, 0);
        }
    }

    private void refillFromCandidates(String worldName,
                                      World world,
                                      PaperConfig.WorldRtpSettings settings,
                                      SafeLocationFinder.SearchPlan plan,
                                      List<SafeLocationFinder.Candidate> primary,
                                      List<SafeLocationFinder.Candidate> fallback,
                                      int primaryIndex,
                                      int fallbackIndex) {
        if (poolSize(worldName) >= locationPoolSettings.targetSize()) {
            finishPoolRefill(worldName);
            return;
        }

        SafeLocationFinder.Candidate candidate;
        boolean generateChunk;
        int attempts;
        if (primaryIndex < primary.size()) {
            candidate = primary.get(primaryIndex);
            generateChunk = false;
            attempts = primaryIndex + 1;
        } else if (locationPoolSettings.allowGenerateNewChunks()
                && fallbackIndex < fallback.size()
                && primary.size() + fallbackIndex < locationPoolSettings.maxRefillAttempts()) {
            candidate = fallback.get(fallbackIndex);
            generateChunk = true;
            attempts = primary.size() + fallbackIndex + 1;
        } else {
            plugin.debug("RTP location pool refill exhausted world=%s attempts=%d size=%d target=%d",
                    worldName,
                    Math.min(plan.maxAttempts(), locationPoolSettings.maxRefillAttempts()),
                    poolSize(worldName),
                    locationPoolSettings.targetSize());
            finishPoolRefill(worldName);
            return;
        }

        world.getChunkAtAsync(candidate.chunkX(), candidate.chunkZ(), generateChunk)
                .whenComplete((chunk, throwable) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (throwable != null) {
                        plugin.debug("RTP location pool chunk load failed world=%s chunkX=%d chunkZ=%d error=%s",
                                worldName,
                                candidate.chunkX(),
                                candidate.chunkZ(),
                                throwable.getClass().getSimpleName());
                        finishPoolRefill(worldName);
                        return;
                    }

                    Optional<Location> destination = safeLocationFinder.validate(world, settings, plan, candidate);
                    if (destination.isPresent()) {
                        addPooledLocation(worldName, destination.get());
                        plugin.debug("Added pooled RTP location world=%s attempts=%d size=%d target=%d",
                                worldName,
                                attempts,
                                poolSize(worldName),
                                locationPoolSettings.targetSize());
                        finishPoolRefill(worldName);
                        return;
                    }

                    int nextPrimary = primaryIndex < primary.size() ? primaryIndex + 1 : primaryIndex;
                    int nextFallback = primaryIndex < primary.size() ? fallbackIndex : fallbackIndex + 1;
                    refillFromCandidates(worldName, world, settings, plan, primary, fallback, nextPrimary, nextFallback);
                }));
    }

    private Optional<Location> takePooledLocation(String worldName) {
        synchronized (this) {
            Deque<Location> pool = locationPools.get(worldName);
            if (pool == null || pool.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(pool.removeFirst());
        }
    }

    private void addPooledLocation(String worldName, Location location) {
        synchronized (this) {
            Deque<Location> pool = locationPools.computeIfAbsent(worldName, ignored -> new ArrayDeque<>());
            if (pool.size() < locationPoolSettings.targetSize()) {
                pool.addLast(location);
            }
        }
    }

    private int poolSize(String worldName) {
        Deque<Location> pool = locationPools.get(worldName);
        return pool == null ? 0 : pool.size();
    }

    private void finishPoolRefill(String worldName) {
        synchronized (this) {
            poolRefills.put(worldName, false);
        }
    }

    private void startQueuedJob(TeleportJob nextJob) {
        int delayTicks = throttleSettings.queueStartDelayTicks();
        if (delayTicks <= 0) {
            doTeleport(nextJob);
            return;
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> doTeleport(nextJob), delayTicks);
    }

    private void adjustAdaptiveLimit() {
        if (!throttleSettings.enabled()) {
            return;
        }

        ServerHealth health = sampleServerHealth();
        boolean unhealthy = health.tps() < throttleSettings.minTps() || health.mspt() > throttleSettings.maxMspt();
        synchronized (this) {
            if (unhealthy && adaptiveConcurrentSearches > throttleSettings.minConcurrentSearches()) {
                adaptiveConcurrentSearches--;
                plugin.debug("Adaptive RTP throttle reduced limit=%d tps=%.2f mspt=%.2f",
                        adaptiveConcurrentSearches,
                        health.tps(),
                        health.mspt());
            } else if (!unhealthy && adaptiveConcurrentSearches < maxConcurrentSearches) {
                adaptiveConcurrentSearches++;
                plugin.debug("Adaptive RTP throttle increased limit=%d tps=%.2f mspt=%.2f",
                        adaptiveConcurrentSearches,
                        health.tps(),
                        health.mspt());
            }
        }
    }

    private ServerHealth sampleServerHealth() {
        return new ServerHealth(readFirstTps(), readMspt());
    }

    private double readFirstTps() {
        try {
            Object result = plugin.getServer().getClass().getMethod("getTPS").invoke(plugin.getServer());
            if (result instanceof double[] tps && tps.length > 0) {
                return tps[0];
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Older test/server APIs may not expose TPS.
        }
        return 20.0D;
    }

    private double readMspt() {
        try {
            Object result = plugin.getServer().getClass().getMethod("getAverageTickTime").invoke(plugin.getServer());
            if (result instanceof Number number) {
                return number.doubleValue();
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Older test/server APIs may not expose MSPT.
        }
        return 0.0D;
    }

    private record ServerHealth(double tps, double mspt) {
    }

    private record TeleportJob(Player player, String requestId, String worldName, boolean clearPending, long queuedAtNanos, long startedAtNanos) {
        TeleportJob(Player player, String requestId, String worldName, boolean clearPending) {
            this(player, requestId, worldName, clearPending, System.nanoTime(), System.nanoTime());
        }

        TeleportJob withStartedAt(long startedAtNanos) {
            return new TeleportJob(player, requestId, worldName, clearPending, queuedAtNanos, startedAtNanos);
        }
    }

    private static final class RtpMetrics {
        private int completed;
        private int success;
        private int generatedFirst;
        private long totalAttempts;
        private double totalSearchMillis;
        private double totalQueueMillis;

        int record(double searchMillis, double queueMillis, int attempts, boolean generatedFirstHit, boolean succeeded) {
            completed++;
            if (succeeded) {
                success++;
            }
            if (generatedFirstHit) {
                generatedFirst++;
            }
            totalAttempts += attempts;
            totalSearchMillis += searchMillis;
            totalQueueMillis += queueMillis;
            return completed;
        }

        int successCount() {
            return success;
        }

        int failureCount() {
            return completed - success;
        }

        double averageSearchMillis() {
            return completed == 0 ? 0.0D : totalSearchMillis / completed;
        }

        double averageQueueMillis() {
            return completed == 0 ? 0.0D : totalQueueMillis / completed;
        }

        double averageAttempts() {
            return completed == 0 ? 0.0D : (double) totalAttempts / completed;
        }

        double generatedFirstRatio() {
            return completed == 0 ? 0.0D : (double) generatedFirst / completed;
        }
    }
}
