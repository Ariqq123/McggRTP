package me.mcgg.azreyzaako.mcggrtp.paper.gui;

import java.util.List;
import me.mcgg.azreyzaako.mcggrtp.common.ServerStatusEntry;
import me.mcgg.azreyzaako.mcggrtp.paper.MessageBundle;
import me.mcgg.azreyzaako.mcggrtp.paper.config.PaperConfig;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class RtpMenus {
    private RtpMenus() {
    }

    public static void openMainMenu(Player player, PaperConfig config, MessageBundle messages) {
        MenuHolder holder = new MenuHolder(new MenuContext(MenuContext.MenuType.MAIN, ""));
        Inventory inventory = Bukkit.createInventory(holder, config.gui().size(), messages.raw(config.gui().title()));
        fill(inventory, config, messages);

        for (PaperConfig.DimensionOption dimension : config.dimensions().values()) {
            boolean allowed = dimension.permission().isBlank() || player.hasPermission(dimension.permission());
            Material material = allowed
                    ? (dimension.material() == null ? Material.STONE : dimension.material())
                    : Material.BARRIER;
            ItemStack item = new ItemStack(material);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(messages.raw(dimension.displayName()));
            meta.lore(allowed ? messages.lore(dimension.lore()) : List.of(messages.rawKey("dimension-locked")));
            item.setItemMeta(meta);
            inventory.setItem(dimension.slot(), item);
        }

        player.playSound(player.getLocation(), config.sounds().menuOpen(), 1.0F, 1.0F);
        player.openInventory(inventory);
    }

    public static void openServerMenu(Player player, PaperConfig config, MessageBundle messages, String dimension, List<ServerStatusEntry> statuses) {
        MenuHolder holder = new MenuHolder(new MenuContext(MenuContext.MenuType.SERVER, dimension));
        String title = config.serverMenu().title().replace("{dimension}", dimension);
        Inventory inventory = Bukkit.createInventory(holder, config.serverMenu().size(), messages.raw(title));
        fill(inventory, config, messages);

        List<String> servers = config.network().dimensions().getOrDefault(dimension, List.of());
        for (int index = 0; index < servers.size() && index < inventory.getSize(); index++) {
            String serverId = servers.get(index);
            PaperConfig.NetworkServer server = config.network().servers().get(serverId);
            if (server == null) {
                continue;
            }
            ServerStatusEntry status = statuses.stream()
                    .filter(entry -> entry.serverId().equals(serverId))
                    .findFirst()
                    .orElse(new ServerStatusEntry(serverId, server.displayName(), false, 0));

            boolean allowed = server.permission().isBlank() || player.hasPermission(server.permission());
            Material material = status.online()
                    ? (config.serverMenu().onlineMaterial() == null ? Material.LIME_WOOL : config.serverMenu().onlineMaterial())
                    : (config.serverMenu().offlineMaterial() == null ? Material.BARRIER : config.serverMenu().offlineMaterial());
            ItemStack item = new ItemStack(material);
            ItemMeta meta = item.getItemMeta();
            String displayName = allowed ? status.displayName() : "&7" + stripColor(status.displayName());
            meta.displayName(messages.raw(displayName));
            if (status.online() && allowed) {
                meta.lore(List.of(
                        messages.rawKey("server-player-count", "{count}", String.valueOf(status.playerCount())),
                        messages.rawKey("server-click", "{server}", serverId),
                        messages.rawKey("server-open-lore")
                ));
            } else if (!status.online()) {
                meta.lore(List.of(
                        messages.rawKey("server-player-count", "{count}", String.valueOf(status.playerCount())),
                        messages.rawKey("server-unavailable-lore")
                ));
            } else {
                meta.lore(List.of(
                        messages.rawKey("server-player-count", "{count}", String.valueOf(status.playerCount())),
                        messages.rawKey("server-locked")
                ));
            }
            item.setItemMeta(meta);
            inventory.setItem(index, item);
        }

        player.playSound(player.getLocation(), config.sounds().menuOpen(), 1.0F, 1.0F);
        player.openInventory(inventory);
    }

    private static void fill(Inventory inventory, PaperConfig config, MessageBundle messages) {
        if (!config.gui().fillerEnabled()) {
            return;
        }

        ItemStack filler = new ItemStack(config.gui().fillerMaterial() == null ? Material.BLACK_STAINED_GLASS_PANE : config.gui().fillerMaterial());
        ItemMeta meta = filler.getItemMeta();
        meta.displayName(messages.raw(config.gui().fillerName()));
        filler.setItemMeta(meta);

        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }
    }

    private static String stripColor(String input) {
        return input.replaceAll("&[0-9a-fk-or]", "");
    }
}
