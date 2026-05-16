package me.mcgg.azreyzaako.mcggrtp.paper.listener;

import me.mcgg.azreyzaako.mcggrtp.paper.rtp.RtpWarmupService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class RtpWarmupListener implements Listener {
    private final RtpWarmupService warmupService;

    public RtpWarmupListener(RtpWarmupService warmupService) {
        this.warmupService = warmupService;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        warmupService.cancelIfMoved(event.getPlayer(), event.getTo());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        warmupService.clear(event.getPlayer());
    }
}
