package me.mcgg.azreyzaako.mcggrtp.paper;

import me.mcgg.azreyzaako.mcggrtp.common.McggRTPChannels;
import me.mcgg.azreyzaako.mcggrtp.paper.command.RtpCommand;
import me.mcgg.azreyzaako.mcggrtp.paper.config.PaperConfig;
import me.mcgg.azreyzaako.mcggrtp.paper.config.PaperConfigLoader;
import me.mcgg.azreyzaako.mcggrtp.paper.gui.RtpGuiListener;
import me.mcgg.azreyzaako.mcggrtp.paper.listener.PendingRtpJoinListener;
import me.mcgg.azreyzaako.mcggrtp.paper.messaging.PaperMessageBridge;
import me.mcgg.azreyzaako.mcggrtp.paper.rtp.RtpTeleportService;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public class McggRTPPaper extends JavaPlugin {
    private PaperConfig configModel;
    private MessageBundle messages;
    private PaperMessageBridge messageBridge;
    private RtpTeleportService teleportService;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("messages.yml", false);
        reloadPluginState();
        this.messages = new MessageBundle(this);
        this.messageBridge = new PaperMessageBridge(this);
        this.teleportService = new RtpTeleportService(this, configModel, messages, messageBridge);

        getServer().getMessenger().registerOutgoingPluginChannel(this, McggRTPChannels.MAIN);
        getServer().getMessenger().registerIncomingPluginChannel(this, McggRTPChannels.MAIN, messageBridge);

        registerRtpCommand(new RtpCommand(this));

        getServer().getPluginManager().registerEvents(new RtpGuiListener(this), this);
        getServer().getPluginManager().registerEvents(new PendingRtpJoinListener(this, messageBridge), this);
        getLogger().info("McggRTP Paper enabled");
    }

    @Override
    public void onDisable() {
        getServer().getMessenger().unregisterIncomingPluginChannel(this);
        getServer().getMessenger().unregisterOutgoingPluginChannel(this);
    }

    public PaperConfig configModel() {
        return configModel;
    }

    public MessageBundle messages() {
        return messages;
    }

    public PaperMessageBridge messageBridge() {
        return messageBridge;
    }

    public RtpTeleportService teleportService() {
        return teleportService;
    }

    public void reloadPluginState() {
        PaperConfigLoader configLoader = new PaperConfigLoader(this);
        this.configModel = configLoader.load();
        if (this.messages != null) {
            this.messages.reload();
        }
    }

    private void registerRtpCommand(RtpCommand command) {
        try {
            // Paper plugins use runtime command registration instead of YAML lookup.
            JavaPlugin.class.getMethod("registerCommand", String.class, String.class, io.papermc.paper.command.brigadier.BasicCommand.class)
                    .invoke(this, "rtp", "Open the McggRTP menu.", command);
            return;
        } catch (ReflectiveOperationException ignored) {
            // MockBukkit still exposes the classic Bukkit command path used by the tests.
        }

        PluginCommand pluginCommand = getCommand("rtp");
        if (pluginCommand != null) {
            pluginCommand.setExecutor(command);
        }
    }
}
