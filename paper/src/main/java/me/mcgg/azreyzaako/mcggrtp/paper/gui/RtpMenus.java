package me.mcgg.azreyzaako.mcggrtp.paper.gui;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
            int serverCount = config.network().dimensions().getOrDefault(dimension.key(), List.of()).size();
            boolean allowed = dimension.permission().isBlank() || player.hasPermission(dimension.permission());
            boolean available = serverCount > 0;
            Material material = allowed && available
                    ? (dimension.material() == null ? Material.STONE : dimension.material())
                    : Material.BARRIER;
            List<net.kyori.adventure.text.Component> lore = !allowed
                    ? List.of(messages.rawKey("dimension-locked"))
                    : !available
                    ? List.of(messages.rawKey("dimension-unavailable-lore"))
                    : dimensionLore(dimension, serverCount, messages);
            inventory.setItem(dimension.slot(), menuItem(material, messages.raw(dimension.displayName()), lore));
        }

        player.playSound(player.getLocation(), config.sounds().menuOpen(), 1.0F, 1.0F);
        player.openInventory(inventory);
    }

    public static void openServerMenu(Player player, PaperConfig config, MessageBundle messages, String dimension, List<ServerStatusEntry> statuses) {
        PaperConfig.DimensionOption dimensionOption = config.dimensions().get(dimension);
        String dimensionLabel = dimensionOption == null ? dimension : stripColor(dimensionOption.displayName());
        String title = config.serverMenu().title().replace("{dimension}", dimensionLabel);
        int size = config.serverMenu().size();
        int backSlot = size >= 5 ? size - 5 : -1;
        List<String> servers = config.network().dimensions().getOrDefault(dimension, List.of());
        Map<Integer, String> slotMap = assignServerSlots(size, servers, backSlot);

        MenuHolder holder = new MenuHolder(new MenuContext(MenuContext.MenuType.SERVER, dimension, Map.copyOf(slotMap), backSlot));
        Inventory inventory = Bukkit.createInventory(holder, size, messages.raw(title));
        fill(inventory, config, messages);
        addBackButton(inventory, backSlot, messages);

        if (servers.isEmpty()) {
            inventory.setItem(centerSlot(size), menuItem(Material.BARRIER, messages.rawKey("server-menu-empty"), List.of(messages.rawKey("server-menu-empty-lore"))));
        }

        for (int index = 0; index < servers.size(); index++) {
            String serverId = servers.get(index);
            PaperConfig.NetworkServer server = config.network().servers().get(serverId);
            if (server == null) {
                continue;
            }
            Integer slot = slotForServer(slotMap, serverId);
            if (slot == null) {
                continue;
            }
            ServerStatusEntry status = statuses.stream()
                    .filter(entry -> entry.serverId().equals(serverId))
                    .findFirst()
                    .orElse(new ServerStatusEntry(serverId, server.displayName(), false, 0));

            boolean allowed = server.permission().isBlank() || player.hasPermission(server.permission());
            boolean current = serverId.equalsIgnoreCase(config.network().currentServer());
            Material material = status.online()
                    ? (config.serverMenu().onlineMaterial() == null ? Material.LIME_WOOL : config.serverMenu().onlineMaterial())
                    : (config.serverMenu().offlineMaterial() == null ? Material.BARRIER : config.serverMenu().offlineMaterial());
            net.kyori.adventure.text.Component displayName = current
                    ? messages.rawKey("server-current-name", "{server}", status.displayName())
                    : messages.raw(allowed ? status.displayName() : "&7" + stripColor(status.displayName()));
            List<net.kyori.adventure.text.Component> lore;
            if (!status.online()) {
                lore = List.of(
                        messages.rawKey("server-player-count", "{count}", String.valueOf(status.playerCount())),
                        messages.rawKey("server-unavailable-lore")
                );
            } else if (!allowed) {
                lore = List.of(
                        messages.rawKey("server-player-count", "{count}", String.valueOf(status.playerCount())),
                        messages.rawKey("server-locked")
                );
            } else if (current) {
                lore = List.of(
                        messages.rawKey("server-player-count", "{count}", String.valueOf(status.playerCount())),
                        messages.rawKey("server-current-lore"),
                        messages.rawKey("server-click", "{server}", serverId)
                );
            } else {
                lore = List.of(
                        messages.rawKey("server-player-count", "{count}", String.valueOf(status.playerCount())),
                        messages.rawKey("server-open-lore"),
                        messages.rawKey("server-click", "{server}", serverId)
                );
            }
            inventory.setItem(slot, menuItem(material, displayName, lore));
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

    private static void addBackButton(Inventory inventory, int slot, MessageBundle messages) {
        if (slot < 0 || slot >= inventory.getSize()) {
            return;
        }
        inventory.setItem(slot, menuItem(
                Material.ARROW,
                messages.rawKey("menu-back-name"),
                List.of(messages.rawKey("menu-back-lore"))
        ));
    }

    private static List<net.kyori.adventure.text.Component> dimensionLore(PaperConfig.DimensionOption dimension, int serverCount, MessageBundle messages) {
        java.util.ArrayList<net.kyori.adventure.text.Component> lore = new java.util.ArrayList<>(messages.lore(dimension.lore()));
        lore.add(messages.rawKey("dimension-server-count", "{count}", String.valueOf(serverCount)));
        lore.add(messages.rawKey("dimension-warmup", "{time}", String.valueOf(dimension.warmupSeconds())));
        lore.add(messages.rawKey("dimension-click-lore"));
        return List.copyOf(lore);
    }

    private static ItemStack menuItem(Material material, net.kyori.adventure.text.Component name, List<net.kyori.adventure.text.Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name);
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static Map<Integer, String> assignServerSlots(int inventorySize, List<String> servers, int reservedSlot) {
        Map<Integer, String> slots = new LinkedHashMap<>();
        List<Integer> primaryRow = centeredRowSlots(inventorySize, 1, List.of(10, 11, 12, 13, 14, 15, 16), Math.min(servers.size(), 7));
        int assigned = assignSlots(slots, primaryRow, servers, 0);

        if (assigned < servers.size()) {
            List<Integer> overflowRow = centeredRowSlots(inventorySize, 2, List.of(19, 20, 21, 23, 24, 25), Math.min(servers.size() - assigned, 6));
            assigned = assignSlots(slots, overflowRow, servers, assigned);
        }

        if (assigned < servers.size()) {
            List<Integer> fallbackSlots = preferredServerSlots(inventorySize, reservedSlot);
            for (int slot : fallbackSlots) {
                if (assigned >= servers.size()) {
                    break;
                }
                if (!slots.containsKey(slot)) {
                    slots.put(slot, servers.get(assigned++));
                }
            }
        }
        return slots;
    }

    private static int assignSlots(Map<Integer, String> target, List<Integer> slots, List<String> servers, int startIndex) {
        int assigned = startIndex;
        for (int slot : slots) {
            if (assigned >= servers.size()) {
                break;
            }
            target.put(slot, servers.get(assigned++));
        }
        return assigned;
    }

    private static Integer slotForServer(Map<Integer, String> slotMap, String serverId) {
        return slotMap.entrySet().stream()
                .filter(entry -> entry.getValue().equals(serverId))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    private static List<Integer> preferredServerSlots(int inventorySize, int reservedSlot) {
        // Favor the inner slots first so small server lists stay centered and the
        // back button can live on the bottom row without colliding with targets.
        List<Integer> preferred = new java.util.ArrayList<>();
        int rows = Math.max(1, inventorySize / 9);
        for (int row = 1; row < rows; row++) {
            for (int column = 1; column < 8; column++) {
                int slot = row * 9 + column;
                if (slot < inventorySize && slot != reservedSlot) {
                    preferred.add(slot);
                }
            }
        }
        if (preferred.isEmpty()) {
            for (int slot = 0; slot < inventorySize; slot++) {
                if (slot != reservedSlot) {
                    preferred.add(slot);
                }
            }
        }
        return preferred;
    }

    private static List<Integer> centeredRowSlots(int inventorySize, int row, List<Integer> candidates, int count) {
        if (count <= 0 || inventorySize < (row + 1) * 9) {
            return List.of();
        }
        int startIndex = Math.max(0, (candidates.size() - count) / 2);
        return candidates.subList(startIndex, Math.min(candidates.size(), startIndex + count));
    }

    private static int centerSlot(int inventorySize) {
        int middleRow = Math.max(0, (inventorySize / 9) / 2);
        return Math.min(inventorySize - 1, middleRow * 9 + 4);
    }

    private static String stripColor(String input) {
        return input.replaceAll("&[0-9a-fk-or]", "");
    }
}
