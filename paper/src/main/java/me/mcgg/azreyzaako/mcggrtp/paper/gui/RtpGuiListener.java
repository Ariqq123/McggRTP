package me.mcgg.azreyzaako.mcggrtp.paper.gui;

import java.util.List;
import java.util.UUID;
import me.mcgg.azreyzaako.mcggrtp.common.RtpRequest;
import me.mcgg.azreyzaako.mcggrtp.paper.McggRTPPaper;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

public final class RtpGuiListener implements Listener {
    private final McggRTPPaper plugin;

    public RtpGuiListener(McggRTPPaper plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getInventory().getHolder() instanceof MenuHolder holder)) {
            return;
        }

        event.setCancelled(true);
        if (event.getCurrentItem() == null) {
            return;
        }

        if (holder.context().type() == MenuContext.MenuType.MAIN) {
            handleMainMenuClick(player, event.getSlot());
            return;
        }

        handleServerMenuClick(player, holder.context().dimension(), event.getSlot());
    }

    private void handleMainMenuClick(Player player, int slot) {
        plugin.configModel().dimensions().values().stream()
                .filter(option -> option.slot() == slot)
                .findFirst()
                .ifPresent(option -> {
                    if (!option.permission().isBlank() && !player.hasPermission(option.permission())) {
                        player.playSound(player.getLocation(), plugin.configModel().sounds().denied(), 1.0F, 1.0F);
                        player.sendMessage(plugin.messages().text("no-permission"));
                        return;
                    }
                    player.playSound(player.getLocation(), plugin.configModel().sounds().menuClick(), 1.0F, 1.0F);
                    plugin.messageBridge().requestServerMenu(player, option.key());
                });
    }

    private void handleServerMenuClick(Player player, String dimension, int slot) {
        List<String> servers = plugin.configModel().network().dimensions().getOrDefault(dimension, List.of());
        if (slot < 0 || slot >= servers.size()) {
            return;
        }

        String targetServer = servers.get(slot);
        var server = plugin.configModel().network().servers().get(targetServer);
        if (server == null) {
            return;
        }
        var status = plugin.messageBridge().cachedStatus(dimension, targetServer);
        if (status != null && !status.online()) {
            player.playSound(player.getLocation(), plugin.configModel().sounds().denied(), 1.0F, 1.0F);
            player.sendMessage(plugin.messages().text("server-offline"));
            return;
        }
        if (!server.permission().isBlank() && !player.hasPermission(server.permission())) {
            player.playSound(player.getLocation(), plugin.configModel().sounds().denied(), 1.0F, 1.0F);
            player.sendMessage(plugin.messages().text("no-permission"));
            return;
        }

        var dimensionOption = plugin.configModel().dimensions().get(dimension);
        if (dimensionOption == null) {
            return;
        }

        player.closeInventory();
        player.playSound(player.getLocation(), plugin.configModel().sounds().menuClick(), 1.0F, 1.0F);
        plugin.warmupService().begin(player, dimensionOption.warmupSeconds(), () -> completeSelection(player, dimension, dimensionOption.worldName(), targetServer));
    }

    private void completeSelection(Player player, String dimension, String worldName, String targetServer) {
        String currentServer = plugin.messageBridge().resolveCurrentServer(player);
        if (targetServer.equalsIgnoreCase(currentServer)) {
            plugin.messageBridge().checkCooldownThenLocalTeleport(player, worldName, dimension);
            return;
        }

        String requestId = UUID.randomUUID().toString();
        plugin.messageBridge().sendCreatePending(player, new RtpRequest(
                requestId,
                player.getUniqueId(),
                targetServer,
                worldName,
                dimension
        ));
        player.sendMessage(plugin.messages().text("sending-server", "{server}", targetServer));
    }
}
