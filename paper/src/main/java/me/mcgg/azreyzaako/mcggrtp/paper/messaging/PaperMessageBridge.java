package me.mcgg.azreyzaako.mcggrtp.paper.messaging;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import me.mcgg.azreyzaako.mcggrtp.common.CommandAck;
import me.mcgg.azreyzaako.mcggrtp.common.CooldownCheck;
import me.mcgg.azreyzaako.mcggrtp.common.CooldownResponse;
import me.mcgg.azreyzaako.mcggrtp.common.MessageCodec;
import me.mcgg.azreyzaako.mcggrtp.common.McggRTPChannels;
import me.mcgg.azreyzaako.mcggrtp.common.MessageType;
import me.mcgg.azreyzaako.mcggrtp.common.PendingRtpResponse;
import me.mcgg.azreyzaako.mcggrtp.common.ReloadConfigRequest;
import me.mcgg.azreyzaako.mcggrtp.common.RtpRequest;
import me.mcgg.azreyzaako.mcggrtp.common.RtpResult;
import me.mcgg.azreyzaako.mcggrtp.common.ServerStatusEntry;
import me.mcgg.azreyzaako.mcggrtp.common.ServerStatusRequest;
import me.mcgg.azreyzaako.mcggrtp.common.ServerStatusResponse;
import me.mcgg.azreyzaako.mcggrtp.paper.McggRTPPaper;
import me.mcgg.azreyzaako.mcggrtp.paper.gui.RtpMenus;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

public final class PaperMessageBridge implements PluginMessageListener {
    private final McggRTPPaper plugin;
    private final Map<String, PendingLocalTeleport> pendingTeleports = new ConcurrentHashMap<>();
    private final Map<String, Consumer<CommandAck>> commandCallbacks = new ConcurrentHashMap<>();
    private final Map<String, Consumer<ServerStatusResponse>> serverStatusCallbacks = new ConcurrentHashMap<>();
    private final Map<String, ServerStatusEntry> statusCache = new ConcurrentHashMap<>();

    public PaperMessageBridge(McggRTPPaper plugin) {
        this.plugin = plugin;
    }

    public void sendCreatePending(Player player, RtpRequest request) {
        send(player, MessageCodec.encodeCreatePendingRtp(request));
    }

    public void sendCheckPending(Player player) {
        send(player, MessageCodec.encodeCheckPendingRtp(player.getUniqueId(), resolveCurrentServer(player)));
    }

    public void sendClearPending(Player player, String requestId) {
        send(player, MessageCodec.encodeClearPendingRtp(requestId, player.getUniqueId()));
    }

    public void sendResult(Player player, RtpResult result) {
        send(player, MessageCodec.encodeRtpResult(result));
    }

    public void sendReloadConfig(Player player) {
        String requestId = nextRequestId();
        commandCallbacks.put(requestId, ack -> player.sendMessage(plugin.messages().raw(ack.message())));
        send(player, MessageCodec.encodeReloadConfig(new ReloadConfigRequest(requestId, player.getUniqueId())));
    }

    public void openMainMenu(Player player) {
        RtpMenus.openMainMenu(player, plugin.configModel(), plugin.messages());
    }

    public void requestServerMenu(Player player, String dimension) {
        String requestId = nextRequestId();
        serverStatusCallbacks.put(requestId, response -> {
            cacheStatuses(response.dimension(), response.entries());
            RtpMenus.openServerMenu(player, plugin.configModel(), plugin.messages(), response.dimension(), response.entries());
        });
        send(player, MessageCodec.encodeServerStatusRequest(
                new ServerStatusRequest(requestId, player.getUniqueId(), dimension)
        ));
    }

    public void checkCooldownThenLocalTeleport(Player player, String worldName, String dimension) {
        String requestId = nextRequestId();
        pendingTeleports.put(requestId, new PendingLocalTeleport(player.getUniqueId(), worldName, dimension));
        send(player, MessageCodec.encodeCheckCooldown(
                new CooldownCheck(requestId, player.getUniqueId())
        ));
    }

    public ServerStatusEntry cachedStatus(String dimension, String serverId) {
        return statusCache.get(cacheKey(dimension, serverId));
    }

    public String resolveCurrentServer(Player player) {
        // Paper only knows the local backend instance, not the proxy alias,
        // so the Velocity-facing server id must come from plugin config.
        return plugin.configModel().network().currentServer();
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!channel.equals(McggRTPChannels.MAIN)) {
            return;
        }

        MessageType type = MessageCodec.peekType(message);
        switch (type) {
            case PENDING_RTP_RESPONSE -> handlePendingResponse(player, message);
            case COOLDOWN_RESPONSE -> handleCooldownResponse(player, message);
            case SERVER_STATUS_RESPONSE -> handleServerStatusResponse(message);
            case COMMAND_ACK -> handleCommandAck(message);
            default -> {
                return;
            }
        }
    }

    private void handlePendingResponse(Player player, byte[] message) {
        PendingRtpResponse response = MessageCodec.decodePendingRtpResponse(message);
        if (response.hasPending()) {
            plugin.teleportService().beginPendingTeleport(player, response.requestId(), response.targetWorld(), response.dimension());
        }
    }

    private void handleCooldownResponse(Player player, byte[] message) {
        CooldownResponse response = MessageCodec.decodeCooldownResponse(message);
        PendingLocalTeleport pending = pendingTeleports.remove(response.requestId());
        if (pending == null || !pending.playerUuid().equals(player.getUniqueId())) {
            return;
        }
        if (response.active()) {
            player.playSound(player.getLocation(), plugin.configModel().sounds().denied(), 1.0F, 1.0F);
            player.sendMessage(plugin.messages().text("cooldown", "{time}", String.valueOf(response.remainingSeconds())));
            return;
        }
        plugin.teleportService().beginLocalTeleport(player, pending.worldName(), pending.dimension());
    }

    private void handleServerStatusResponse(byte[] message) {
        ServerStatusResponse response = MessageCodec.decodeServerStatusResponse(message);
        Consumer<ServerStatusResponse> callback = serverStatusCallbacks.remove(response.requestId());
        if (callback != null) {
            callback.accept(response);
        }
    }

    private void handleCommandAck(byte[] message) {
        CommandAck ack = MessageCodec.decodeCommandAck(message);
        Consumer<CommandAck> callback = commandCallbacks.remove(ack.requestId());
        if (callback != null) {
            callback.accept(ack);
        }
    }

    private void cacheStatuses(String dimension, List<ServerStatusEntry> entries) {
        for (ServerStatusEntry entry : entries) {
            statusCache.put(cacheKey(dimension, entry.serverId()), entry);
        }
    }

    private String nextRequestId() {
        return UUID.randomUUID().toString();
    }

    private void send(Player player, byte[] payload) {
        player.sendPluginMessage(plugin, McggRTPChannels.MAIN, payload);
    }

    private String cacheKey(String dimension, String serverId) {
        return dimension + ":" + serverId;
    }

    private record PendingLocalTeleport(UUID playerUuid, String worldName, String dimension) {
    }
}
