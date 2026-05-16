package me.mcgg.azreyzaako.mcggrtp.paper;

import java.io.File;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;

public final class MessageBundle {
    private final McggRTPPaper plugin;
    private YamlConfiguration configuration;
    private final LegacyComponentSerializer serializer = LegacyComponentSerializer.legacyAmpersand();

    public MessageBundle(McggRTPPaper plugin) {
        this.plugin = plugin;
        this.configuration = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "messages.yml"));
    }

    public Component text(String key) {
        return serializer.deserialize(prefix() + configuration.getString(key, ""));
    }

    public Component text(String key, String placeholder, String value) {
        String message = configuration.getString(key, "").replace(placeholder, value);
        return serializer.deserialize(prefix() + message);
    }

    public Component text(String key, String firstPlaceholder, String firstValue, String secondPlaceholder, String secondValue) {
        String message = configuration.getString(key, "")
                .replace(firstPlaceholder, firstValue)
                .replace(secondPlaceholder, secondValue);
        return serializer.deserialize(prefix() + message);
    }

    public Component raw(String input) {
        return serializer.deserialize(input);
    }

    public Component rawKey(String key) {
        return serializer.deserialize(configuration.getString(key, ""));
    }

    public Component rawKey(String key, String placeholder, String value) {
        String message = configuration.getString(key, "").replace(placeholder, value);
        return serializer.deserialize(message);
    }

    public List<Component> lore(List<String> input) {
        return input.stream().<Component>map(serializer::deserialize).toList();
    }

    public void reload() {
        configuration = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "messages.yml"));
    }

    private String prefix() {
        return configuration.getString("prefix", "");
    }
}
