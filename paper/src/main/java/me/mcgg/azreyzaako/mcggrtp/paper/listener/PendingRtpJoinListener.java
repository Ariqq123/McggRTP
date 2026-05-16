package me.mcgg.azreyzaako.mcggrtp.paper.listener;

import me.mcgg.azreyzaako.mcggrtp.paper.McggRTPPaper;
import me.mcgg.azreyzaako.mcggrtp.paper.messaging.PaperMessageBridge;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public final class PendingRtpJoinListener implements Listener {
    private final McggRTPPaper plugin;
    private final PaperMessageBridge messageBridge;

    public PendingRtpJoinListener(McggRTPPaper plugin, PaperMessageBridge messageBridge) {
        this.plugin = plugin;
        this.messageBridge = messageBridge;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Delay one tick so the backend connection is fully ready before Paper
        // tries to send the pending-check plugin message back through Velocity.
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> messageBridge.sendCheckPending(event.getPlayer()), 1L);
    }
}
