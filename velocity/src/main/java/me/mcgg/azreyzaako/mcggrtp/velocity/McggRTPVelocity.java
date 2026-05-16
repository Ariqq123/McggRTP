package me.mcgg.azreyzaako.mcggrtp.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import java.nio.file.Path;
import java.time.Clock;
import me.mcgg.azreyzaako.mcggrtp.common.McggRTPChannels;
import me.mcgg.azreyzaako.mcggrtp.velocity.config.VelocityConfig;
import me.mcgg.azreyzaako.mcggrtp.velocity.config.VelocityConfigLoader;
import me.mcgg.azreyzaako.mcggrtp.velocity.manager.CooldownManager;
import me.mcgg.azreyzaako.mcggrtp.velocity.manager.PendingRtpManager;
import me.mcgg.azreyzaako.mcggrtp.velocity.messaging.VelocityMessageListener;
import me.mcgg.azreyzaako.mcggrtp.velocity.server.ServerTransferService;
import org.slf4j.Logger;

@Plugin(
        id = "mcggrtp",
        name = "McggRTP-Velocity",
        version = "1.0.1",
        authors = {"azreyzaako"}
)
public final class McggRTPVelocity {
    private final ProxyServer proxyServer;
    private final Logger logger;
    private final Path dataDirectory;
    private final ChannelIdentifier channelIdentifier;
    private VelocityConfig config;
    private PendingRtpManager pendingRtpManager;
    private CooldownManager cooldownManager;
    private ServerTransferService serverTransferService;
    private VelocityMessageListener messageListener;

    @Inject
    public McggRTPVelocity(ProxyServer proxyServer, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxyServer = proxyServer;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
        this.channelIdentifier = MinecraftChannelIdentifier.from(McggRTPChannels.MAIN);
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        reloadPluginState();

        proxyServer.getChannelRegistrar().register(channelIdentifier);
        logger.info("McggRTP Velocity enabled on channel {}", McggRTPChannels.MAIN);
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        proxyServer.getChannelRegistrar().unregister(channelIdentifier);
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!event.getIdentifier().equals(channelIdentifier)) {
            return;
        }

        // The proxy is the trust boundary for pending RTP state, so we always
        // consume matching messages here instead of letting other listeners forward them.
        event.setResult(PluginMessageEvent.ForwardResult.handled());
        messageListener.handle(event.getData());
    }

    public void sendToPaper(Player player, byte[] payload) {
        player.getCurrentServer().ifPresent(connection -> connection.sendPluginMessage(channelIdentifier, payload));
    }

    public Logger logger() {
        return logger;
    }

    public boolean debugEnabled() {
        return config != null && config.debugEnabled();
    }

    public void debug(String message, Object... args) {
        if (!debugEnabled()) {
            return;
        }
        logger.info("[debug] {}", String.format(message, args));
    }

    public VelocityConfig config() {
        return config;
    }

    public void reloadPluginState() {
        VelocityConfigLoader configLoader = new VelocityConfigLoader(dataDirectory, logger);
        this.config = configLoader.load();
        this.pendingRtpManager = new PendingRtpManager(Clock.systemUTC(), config.pendingExpireSeconds());
        this.cooldownManager = new CooldownManager(Clock.systemUTC(), config.cooldownEnabled(), config.defaultCooldownSeconds());
        this.serverTransferService = new ServerTransferService(proxyServer);
        this.messageListener = new VelocityMessageListener(
                this,
                proxyServer,
                logger,
                pendingRtpManager,
                cooldownManager,
                serverTransferService
        );
        debug("Reloaded Velocity config: channel=%s debug=%s servers=%d dimensions=%d",
                config.channel(),
                config.debugEnabled(),
                config.servers().size(),
                config.dimensions().size());
    }
}
