package me.mcgg.azreyzaako.mcggrtp.paper.gui;

public record MenuContext(
        MenuType type,
        String dimension
) {
    public enum MenuType {
        MAIN,
        SERVER
    }
}
