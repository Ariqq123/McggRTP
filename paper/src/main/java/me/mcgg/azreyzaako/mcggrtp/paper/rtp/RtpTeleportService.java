package me.mcgg.azreyzaako.mcggrtp.paper.rtp;

import java.util.Optional;
import java.util.logging.Level;
import me.mcgg.azreyzaako.mcggrtp.common.RtpResult;
import me.mcgg.azreyzaako.mcggrtp.paper.McggRTPPaper;
import me.mcgg.azreyzaako.mcggrtp.paper.MessageBundle;
import me.mcgg.azreyzaako.mcggrtp.paper.config.PaperConfig;
import me.mcgg.azreyzaako.mcggrtp.paper.messaging.PaperMessageBridge;
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
        // Safe location search can be moderately expensive in bad terrain, so it
        // runs off-thread and only the final teleport hops back to the main server thread.
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            long startedAt = System.nanoTime();
            SafeLocationFinder.SearchResult searchResult = safeLocationFinder.search(world, settings);
            Optional<Location> destination = searchResult.location();
            logSearch(worldName, searchResult.attempts(), System.nanoTime() - startedAt);
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!player.isOnline() || !player.isValid()) {
                    return;
                }

                if (destination.isEmpty()) {
                    player.sendMessage(messages.text("teleport-failed"));
                    if (clearPending) {
                        messageBridge.sendResult(player, new RtpResult(requestId, player.getUniqueId(), false, "Could not find a safe location."));
                        messageBridge.sendClearPending(player, requestId);
                    }
                    return;
                }

                player.teleportAsync(destination.get()).thenAccept(success -> {
                    if (success) {
                        // Local RTP still reports back to Velocity so the proxy owns
                        // cooldown state consistently across same-server and cross-server teleports.
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
            });
        });
    }

    private void logSearch(String worldName, int attempts, long elapsedNanos) {
        if (!DEBUG_SEARCH_LOGS && !plugin.getLogger().isLoggable(Level.FINE)) {
            return;
        }

        double elapsedMillis = elapsedNanos / 1_000_000.0D;
        plugin.getLogger().fine(String.format(
                "RTP search world=%s attempts=%d durationMs=%.3f",
                worldName,
                attempts,
                elapsedMillis
        ));
    }
}
