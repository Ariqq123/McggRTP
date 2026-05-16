package me.mcgg.azreyzaako.mcggrtp.paper.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class MenuHolder implements InventoryHolder {
    private final MenuContext context;

    public MenuHolder(MenuContext context) {
        this.context = context;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }

    public MenuContext context() {
        return context;
    }
}
