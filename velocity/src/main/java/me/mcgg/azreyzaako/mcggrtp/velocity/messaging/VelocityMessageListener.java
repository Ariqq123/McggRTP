package me.mcgg.azreyzaako.mcggrtp.velocity.messaging;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import me.mcgg.azreyzaako.mcggrtp.common.CommandAck;
import me.mcgg.azreyzaako.mcggrtp.common.CooldownCheck;
import me.mcgg.azreyzaako.mcggrtp.common.CooldownResponse;
import me.mcgg.azreyzaako.mcggrtp.common.MessageCodec;
import me.mcgg.azreyzaako.mcggrtp.common.MessageType;
import me.mcgg.azreyzaako.mcggrtp.common.PendingRtp;
import me.mcgg.azreyzaako.mcggrtp.common.PendingRtpResponse;
import me.mcgg.azreyzaako.mcggrtp.common.ReloadConfigRequest;
import me.mcgg.azreyzaako.mcggrtp.common.RtpRequest;
import me.mcgg.azreyzaako.mcggrtp.common.RtpResult;
import me.mcgg.azreyzaako.mcggrtp.common.ServerStatusEntry;
import me.mcgg.azreyzaako.mcggrtp.common.ServerStatusRequest;
import me.mcgg.azreyzaako.mcggrtp.common.ServerStatusResponse;
import me.mcgg.azreyzaako.mcggrtp.velocity.McggRTPVelocity;
import me.mcgg.azreyzaako.mcggrtp.velocity.manager.CooldownManager;
import me.mcgg.azreyzaako.mcggrtp.velocity.manager.PendingRtpManager;
import me.mcgg.azreyzaako.mcggrtp.velocity.server.ServerTransferService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.slf4j.Logger;

public final class VelocityMessageListener {
    private final McggRTPVelocity plugin;
    private final ProxyServer proxyServer;
    private final Logger logger;
    private final PendingRtpManager pendingRtpManager;
    private final CooldownManager cooldownManager;
    private final ServerTransferService serverTransferService;
    private final LegacyComponentSerializer serializer = LegacyComponentSerializer.legacyAmpersand();

    public VelocityMessageListener(
            McggRTPVelocity plugin,
            ProxyServer proxyServer,
            Logger logger,
            PendingRtpManager pendingRtpManager,
            CooldownManager cooldownManager,
            ServerTransferService serverTransferService
    ) {
        this.plugin = plugin;
        this.proxyServer = proxyServer;
        this.logger = logger;
        this.pendingRtpManager = pendingRtpManager;
        this.cooldownManager = cooldownManager;
        this.serverTransferService = serverTransferService;
    }

    public void handle(byte[] payload) {
        MessageType type = MessageCodec.peekType(payload);
        switch (type) {
            case CREATE_PENDING_RTP -> handleCreatePending(payload);
            case CHECK_PENDING_RTP -> handleCheckPending(payload);
            case CLEAR_PENDING_RTP -> handleClearPending(payload);
            case RTP_RESULT -> handleResult(payload);
            case CHECK_COOLDOWN -> handleCheckCooldown(payload);
            case REQUEST_SERVER_STATUS -> handleServerStatusRequest(payload);
            case RELOAD_CONFIG -> handleReloadConfig(payload);
            default -> logger.warn("Ignoring unsupported message type {}", type);
        }
    }

    private void handleCreatePending(byte[] payload) {
        RtpRequest request = MessageCodec.decodeCreatePendingRtp(payload);
        Optional<Player> playerOptional = proxyServer.getPlayer(request.playerUuid());
        if (playerOptional.isEmpty()) {
            return;
        }

        Player player = playerOptional.get();
        if (!player.hasPermission(plugin.config().bypassPermission())) {
            CooldownManager.CooldownState cooldownState = cooldownManager.getState(player.getUniqueId());
            if (cooldownState.active()) {
                player.sendMessage(text("&cYou must wait &e" + cooldownState.remainingSeconds() + "&c seconds before using RTP again."));
                return;
            }
        }

        if (!serverTransferService.serverExists(request.targetServer())) {
            player.sendMessage(text("&cThat server is not available."));
            return;
        }

        pendingRtpManager.put(new PendingRtp(
                request.requestId(),
                request.playerUuid(),
                request.targetServer(),
                request.targetWorld(),
                request.dimension(),
                System.currentTimeMillis()
        ));
        cooldownManager.markUsed(player.getUniqueId());
        player.sendMessage(text("&7Sending you to &e" + request.targetServer() + "&7..."));

        serverTransferService.connect(player, request.targetServer()).thenAccept(success -> {
            if (!success) {
                pendingRtpManager.clear(player.getUniqueId(), request.requestId());
                player.sendMessage(text("&cCould not connect you to that server."));
            }
        });
    }

    private void handleCheckPending(byte[] payload) {
        MessageCodec.CheckPendingRtp request = MessageCodec.decodeCheckPendingRtp(payload);
        PendingRtpResponse response = pendingRtpManager.findFor(request.playerUuid(), request.currentServer())
                .map(pending -> new PendingRtpResponse(true, pending.requestId(), pending.targetWorld(), pending.dimension()))
                .orElseGet(PendingRtpResponse::empty);

        proxyServer.getPlayer(request.playerUuid()).ifPresent(player -> plugin.sendToPaper(player, MessageCodec.encodePendingRtpResponse(response)));
    }

    private void handleClearPending(byte[] payload) {
        MessageCodec.ClearPendingRtp clearPending = MessageCodec.decodeClearPendingRtp(payload);
        pendingRtpManager.clear(clearPending.playerUuid(), clearPending.requestId());
    }

    private void handleResult(byte[] payload) {
        RtpResult result = MessageCodec.decodeRtpResult(payload);
        pendingRtpManager.clear(result.playerUuid(), result.requestId());
        if (result.success()) {
            cooldownManager.markUsed(result.playerUuid());
        }
        proxyServer.getPlayer(result.playerUuid()).ifPresent(player -> {
            if (result.success()) {
                player.sendMessage(text("&aTeleported you to a random location."));
            } else if (!result.reason().isBlank()) {
                player.sendMessage(text("&c" + result.reason()));
            }
        });
    }

    private void handleCheckCooldown(byte[] payload) {
        CooldownCheck check = MessageCodec.decodeCheckCooldown(payload);
        CooldownManager.CooldownState state = cooldownManager.getState(check.playerUuid());
        proxyServer.getPlayer(check.playerUuid()).ifPresent(player -> plugin.sendToPaper(player,
                MessageCodec.encodeCooldownResponse(new CooldownResponse(check.requestId(), state.active(), state.remainingSeconds()))));
    }

    private void handleServerStatusRequest(byte[] payload) {
        ServerStatusRequest request = MessageCodec.decodeServerStatusRequest(payload);
        List<String> configuredServers = plugin.config().dimensions().getOrDefault(request.dimension(), List.of());
        List<CompletableFuture<ServerStatusEntry>> entryFutures = new ArrayList<>(configuredServers.size());
        for (String serverId : configuredServers) {
            var configured = plugin.config().servers().get(serverId);
            if (configured == null) {
                continue;
            }

            Optional<RegisteredServer> server = serverTransferService.findServer(serverId);
            if (!configured.enabled() || server.isEmpty()) {
                entryFutures.add(CompletableFuture.completedFuture(
                        new ServerStatusEntry(serverId, configured.displayName(), false, 0)
                ));
                continue;
            }

            RegisteredServer registeredServer = server.get();
            int playerCount = registeredServer.getPlayersConnected().size();
            // A registered server can still be down, so the menu status uses a ping
            // result rather than assuming the proxy entry is live.
            entryFutures.add(registeredServer.ping()
                    .handle((ping, throwable) -> new ServerStatusEntry(
                            serverId,
                            configured.displayName(),
                            throwable == null,
                            throwable == null ? playerCount : 0
                    )));
        }

        CompletableFuture.allOf(entryFutures.toArray(CompletableFuture[]::new))
                .thenRun(() -> {
                    List<ServerStatusEntry> entries = entryFutures.stream()
                            .map(CompletableFuture::join)
                            .toList();
                    proxyServer.getPlayer(request.playerUuid()).ifPresent(player -> plugin.sendToPaper(player,
                            MessageCodec.encodeServerStatusResponse(new ServerStatusResponse(
                                    request.requestId(),
                                    request.dimension(),
                                    List.copyOf(entries)
                            ))));
                });
    }

    private void handleReloadConfig(byte[] payload) {
        ReloadConfigRequest request = MessageCodec.decodeReloadConfig(payload);
        try {
            plugin.reloadPluginState();
            proxyServer.getPlayer(request.playerUuid()).ifPresent(player -> plugin.sendToPaper(player,
                    MessageCodec.encodeCommandAck(new CommandAck(request.requestId(), true, "&aVelocity config reloaded."))));
        } catch (RuntimeException exception) {
            logger.error("Failed to reload McggRTP Velocity config", exception);
            proxyServer.getPlayer(request.playerUuid()).ifPresent(player -> plugin.sendToPaper(player,
                    MessageCodec.encodeCommandAck(new CommandAck(request.requestId(), false, "&cVelocity reload failed. Check proxy logs."))));
        }
    }

    private Component text(String input) {
        return serializer.deserialize(input);
    }
}
