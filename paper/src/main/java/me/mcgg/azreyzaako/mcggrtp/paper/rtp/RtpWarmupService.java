package me.mcgg.azreyzaako.mcggrtp.paper.rtp;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import me.mcgg.azreyzaako.mcggrtp.paper.McggRTPPaper;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

public final class RtpWarmupService {
    private final McggRTPPaper plugin;
    private final Map<UUID, ActiveWarmup> activeWarmups = new ConcurrentHashMap<>();

    public RtpWarmupService(McggRTPPaper plugin) {
        this.plugin = plugin;
    }

    public void begin(Player player, int warmupSeconds, Runnable action) {
        cancel(player.getUniqueId());
        if (warmupSeconds <= 0) {
            action.run();
            return;
        }

        UUID playerId = player.getUniqueId();
        Location startLocation = player.getLocation().clone();
        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            ActiveWarmup warmup = activeWarmups.remove(playerId);
            if (warmup == null) {
                return;
            }
            warmup.action().run();
        }, warmupSeconds * 20L);

        activeWarmups.put(playerId, new ActiveWarmup(startLocation, task, action));
        player.sendMessage(plugin.messages().text("warmup-started", "{time}", Integer.toString(warmupSeconds)));
    }

    public void cancelIfMoved(Player player, Location to) {
        ActiveWarmup warmup = activeWarmups.get(player.getUniqueId());
        if (warmup == null || to == null) {
            return;
        }
        if (changedBlock(warmup.startLocation(), to)) {
            cancel(player.getUniqueId());
            player.sendMessage(plugin.messages().text("warmup-cancelled"));
        }
    }

    public void clear(Player player) {
        cancel(player.getUniqueId());
    }

    public void clearAll() {
        activeWarmups.keySet().forEach(this::cancel);
    }

    boolean hasWarmup(UUID playerId) {
        return activeWarmups.containsKey(playerId);
    }

    private void cancel(UUID playerId) {
        ActiveWarmup warmup = activeWarmups.remove(playerId);
        if (warmup == null) {
            return;
        }
        warmup.task().cancel();
    }

    private boolean changedBlock(Location start, Location current) {
        return !start.getWorld().equals(current.getWorld())
                || start.getBlockX() != current.getBlockX()
                || start.getBlockY() != current.getBlockY()
                || start.getBlockZ() != current.getBlockZ();
    }

    private record ActiveWarmup(Location startLocation, BukkitTask task, Runnable action) {
    }
}
