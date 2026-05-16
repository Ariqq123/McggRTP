package me.mcgg.azreyzaako.mcggrtp.paper.gui;

import java.util.Map;
import java.util.Optional;

public record MenuContext(
        MenuType type,
        String dimension,
        Map<Integer, String> serverSlots,
        int backSlot
) {
    public MenuContext(MenuType type, String dimension) {
        this(type, dimension, Map.of(), -1);
    }

    public Optional<String> serverAt(int slot) {
        return Optional.ofNullable(serverSlots.get(slot));
    }

    public boolean isBackSlot(int slot) {
        return backSlot >= 0 && backSlot == slot;
    }

    public enum MenuType {
        MAIN,
        SERVER
    }
}
