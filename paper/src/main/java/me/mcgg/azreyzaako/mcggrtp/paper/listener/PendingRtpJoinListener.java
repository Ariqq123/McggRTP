package me.mcgg.azreyzaako.mcggrtp.paper.listener;

import me.mcgg.azreyzaako.mcggrtp.paper.McggRTPPaper;
import me.mcgg.azreyzaako.mcggrtp.paper.messaging.PaperMessageBridge;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public final class PendingRtpJoinListener implements Listener {
    private static final long[] PENDING_CHECK_DELAYS = {1L, 10L, 20L};

    private final McggRTPPaper plugin;
    private final PaperMessageBridge messageBridge;

    public PendingRtpJoinListener(McggRTPPaper plugin, PaperMessageBridge messageBridge) {
        this.plugin = plugin;
        this.messageBridge = messageBridge;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Real proxy/backend handoff can race the first join tick, so Paper
        // probes a few times after join instead of assuming one delayed check
        // is always enough for pending cross-server RTP completion.
        plugin.debug("Scheduling pending RTP checks for player=%s server=%s delays=%s",
                event.getPlayer().getUniqueId(),
                plugin.configModel().network().currentServer(),
                java.util.Arrays.toString(PENDING_CHECK_DELAYS));
        for (long delay : PENDING_CHECK_DELAYS) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> messageBridge.sendCheckPending(event.getPlayer()), delay);
        }
    }
}
