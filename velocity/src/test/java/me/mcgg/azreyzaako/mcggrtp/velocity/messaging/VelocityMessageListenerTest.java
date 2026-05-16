package me.mcgg.azreyzaako.mcggrtp.velocity.messaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.velocitypowered.api.proxy.ConnectionRequestBuilder;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerPing;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import me.mcgg.azreyzaako.mcggrtp.common.CommandAck;
import me.mcgg.azreyzaako.mcggrtp.common.CooldownCheck;
import me.mcgg.azreyzaako.mcggrtp.common.CooldownResponse;
import me.mcgg.azreyzaako.mcggrtp.common.MessageCodec;
import me.mcgg.azreyzaako.mcggrtp.common.PendingRtp;
import me.mcgg.azreyzaako.mcggrtp.common.PendingRtpResponse;
import me.mcgg.azreyzaako.mcggrtp.common.ReloadConfigRequest;
import me.mcgg.azreyzaako.mcggrtp.common.RtpRequest;
import me.mcgg.azreyzaako.mcggrtp.common.RtpResult;
import me.mcgg.azreyzaako.mcggrtp.common.ServerStatusResponse;
import me.mcgg.azreyzaako.mcggrtp.velocity.McggRTPVelocity;
import me.mcgg.azreyzaako.mcggrtp.velocity.config.VelocityConfig;
import me.mcgg.azreyzaako.mcggrtp.velocity.manager.CooldownManager;
import me.mcgg.azreyzaako.mcggrtp.velocity.manager.PendingRtpManager;
import me.mcgg.azreyzaako.mcggrtp.velocity.server.ServerTransferService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.slf4j.Logger;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VelocityMessageListenerTest {
    @Mock
    private ProxyServer proxyServer;
    @Mock
    private Logger logger;
    @Mock
    private ServerTransferService transferService;
    @Mock
    private McggRTPVelocity plugin;
    @Mock
    private Player player;

    private PendingRtpManager pendingRtpManager;
    private CooldownManager cooldownManager;
    private VelocityMessageListener listener;
    private VelocityConfig config;

    @BeforeEach
    void setUp() {
        pendingRtpManager = new PendingRtpManager(Clock.systemUTC(), 30);
        cooldownManager = new CooldownManager(Clock.systemUTC(), true, 300);
        config = new VelocityConfig(
                "mcggrtp:main",
                30,
                true,
                300,
                "mcggrtp.bypass.cooldown",
                "mcggrtp.server.",
                java.util.Map.of(
                        "survival-1", new VelocityConfig.NetworkServer("survival-1", "&aSurvival 1", true, "mcggrtp.server.survival-1"),
                        "survival-2", new VelocityConfig.NetworkServer("survival-2", "&aSurvival 2", true, "mcggrtp.server.survival-2"),
                        "survival-3", new VelocityConfig.NetworkServer("survival-3", "&aSurvival 3", false, "mcggrtp.server.survival-3")
                ),
                java.util.Map.of(
                        "overworld", List.of("survival-1", "survival-2", "survival-3"),
                        "nether", List.of("survival-1", "survival-2"),
                        "end", List.of("survival-1")
                )
        );
        when(plugin.config()).thenReturn(config);
        listener = new VelocityMessageListener(plugin, proxyServer, logger, pendingRtpManager, cooldownManager, transferService);
    }

    @Test
    void createPendingStoresAndAllowsCrossServerLookup() {
        UUID playerId = UUID.randomUUID();
        RtpRequest request = new RtpRequest("req-1", playerId, "survival-2", "world", "overworld");
        when(proxyServer.getPlayer(playerId)).thenReturn(Optional.of(player));
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.hasPermission("mcggrtp.bypass.cooldown")).thenReturn(false);
        when(transferService.serverExists("survival-2")).thenReturn(true);
        when(transferService.connect(player, "survival-2")).thenReturn(CompletableFuture.completedFuture(true));

        listener.handle(MessageCodec.encodeCreatePendingRtp(request));

        assertTrue(pendingRtpManager.findFor(playerId, "survival-2").isPresent());
        verify(player).sendMessage(any());
    }

    @Test
    void createPendingRejectsOfflineServer() {
        UUID playerId = UUID.randomUUID();
        RtpRequest request = new RtpRequest("req-1", playerId, "survival-9", "world", "overworld");
        when(proxyServer.getPlayer(playerId)).thenReturn(Optional.of(player));
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.hasPermission("mcggrtp.bypass.cooldown")).thenReturn(false);
        when(transferService.serverExists("survival-9")).thenReturn(false);

        listener.handle(MessageCodec.encodeCreatePendingRtp(request));

        assertFalse(pendingRtpManager.findFor(playerId, "survival-9").isPresent());
        verify(player).sendMessage(any());
    }

    @Test
    void checkPendingRespondsWithTargetWorld() {
        UUID playerId = UUID.randomUUID();
        pendingRtpManager.put(new PendingRtp("req-1", playerId, "survival-2", "world_nether", "nether", System.currentTimeMillis()));
        when(proxyServer.getPlayer(playerId)).thenReturn(Optional.of(player));

        listener.handle(MessageCodec.encodeCheckPendingRtp(playerId, "survival-2"));

        ArgumentCaptor<byte[]> payloadCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(plugin).sendToPaper(eq(player), payloadCaptor.capture());
        PendingRtpResponse response = MessageCodec.decodePendingRtpResponse(payloadCaptor.getValue());
        assertTrue(response.hasPending());
        assertEquals("world_nether", response.targetWorld());
        assertEquals("nether", response.dimension());
    }

    @Test
    void rtpResultSuccessClearsPendingAndStartsCooldown() {
        UUID playerId = UUID.randomUUID();
        pendingRtpManager.put(new PendingRtp("req-1", playerId, "survival-2", "world", "overworld", System.currentTimeMillis()));
        when(proxyServer.getPlayer(playerId)).thenReturn(Optional.of(player));

        listener.handle(MessageCodec.encodeRtpResult(new RtpResult("req-1", playerId, true, "")));

        assertFalse(pendingRtpManager.findFor(playerId, "survival-2").isPresent());
        assertTrue(cooldownManager.getState(playerId).active());
    }

    @Test
    void cooldownCheckReturnsRemainingSeconds() {
        UUID playerId = UUID.randomUUID();
        cooldownManager.markUsed(playerId);
        when(proxyServer.getPlayer(playerId)).thenReturn(Optional.of(player));

        listener.handle(MessageCodec.encodeCheckCooldown(new CooldownCheck("cooldown-1", playerId)));

        ArgumentCaptor<byte[]> payloadCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(plugin).sendToPaper(eq(player), payloadCaptor.capture());
        CooldownResponse response = MessageCodec.decodeCooldownResponse(payloadCaptor.getValue());
        assertTrue(response.active());
        assertTrue(response.remainingSeconds() > 0);
    }

    @Test
    void serverStatusRequestReturnsCountsAndOfflineStates() {
        UUID playerId = UUID.randomUUID();
        RegisteredServer survival1 = mock(RegisteredServer.class);
        RegisteredServer survival2 = mock(RegisteredServer.class);
        when(proxyServer.getPlayer(playerId)).thenReturn(Optional.of(player));
        when(transferService.findServer("survival-1")).thenReturn(Optional.of(survival1));
        when(transferService.findServer("survival-2")).thenReturn(Optional.of(survival2));
        when(transferService.findServer("survival-3")).thenReturn(Optional.empty());
        when(survival1.getPlayersConnected()).thenReturn(List.of(player, mock(Player.class)));
        when(survival2.getPlayersConnected()).thenReturn(List.of(player));
        when(survival1.ping()).thenReturn(CompletableFuture.completedFuture(mock(ServerPing.class)));
        when(survival2.ping()).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("offline")));

        listener.handle(MessageCodec.encodeServerStatusRequest(
                new me.mcgg.azreyzaako.mcggrtp.common.ServerStatusRequest("status-1", playerId, "overworld")
        ));

        ArgumentCaptor<byte[]> payloadCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(plugin).sendToPaper(eq(player), payloadCaptor.capture());
        ServerStatusResponse response = MessageCodec.decodeServerStatusResponse(payloadCaptor.getValue());
        assertEquals(3, response.entries().size());
        assertTrue(response.entries().stream().anyMatch(entry -> entry.serverId().equals("survival-1") && entry.online() && entry.playerCount() == 2));
        assertTrue(response.entries().stream().anyMatch(entry -> entry.serverId().equals("survival-2") && !entry.online() && entry.playerCount() == 0));
        assertTrue(response.entries().stream().anyMatch(entry -> entry.serverId().equals("survival-3") && !entry.online() && entry.playerCount() == 0));
    }

    @Test
    void reloadConfigSendsSuccessAck() {
        UUID playerId = UUID.randomUUID();
        when(proxyServer.getPlayer(playerId)).thenReturn(Optional.of(player));
        doNothing().when(plugin).reloadPluginState();

        listener.handle(MessageCodec.encodeReloadConfig(new ReloadConfigRequest("reload-1", playerId)));

        ArgumentCaptor<byte[]> payloadCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(plugin).sendToPaper(eq(player), payloadCaptor.capture());
        CommandAck ack = MessageCodec.decodeCommandAck(payloadCaptor.getValue());
        assertTrue(ack.success());
    }

    @Test
    void reloadConfigSendsFailureAckWhenReloadThrows() {
        UUID playerId = UUID.randomUUID();
        when(proxyServer.getPlayer(playerId)).thenReturn(Optional.of(player));
        doThrow(new IllegalStateException("boom")).when(plugin).reloadPluginState();

        listener.handle(MessageCodec.encodeReloadConfig(new ReloadConfigRequest("reload-1", playerId)));

        ArgumentCaptor<byte[]> payloadCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(plugin).sendToPaper(eq(player), payloadCaptor.capture());
        CommandAck ack = MessageCodec.decodeCommandAck(payloadCaptor.getValue());
        assertFalse(ack.success());
    }

    @Test
    void pendingRequestExpiresForDisconnectedFlow() throws InterruptedException {
        MutableClock clock = new MutableClock();
        PendingRtpManager expiringManager = new PendingRtpManager(clock, 1);
        VelocityMessageListener expiringListener = new VelocityMessageListener(plugin, proxyServer, logger, expiringManager, cooldownManager, transferService);
        UUID playerId = UUID.randomUUID();
        when(proxyServer.getPlayer(playerId)).thenReturn(Optional.of(player));
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.hasPermission("mcggrtp.bypass.cooldown")).thenReturn(false);
        when(transferService.serverExists("survival-2")).thenReturn(true);
        when(transferService.connect(player, "survival-2")).thenReturn(CompletableFuture.completedFuture(true));

        expiringListener.handle(MessageCodec.encodeCreatePendingRtp(new RtpRequest("req-1", playerId, "survival-2", "world", "overworld")));
        clock.advanceMillis(2_000);

        assertFalse(expiringManager.findFor(playerId, "survival-2").isPresent());
    }

    private static final class MutableClock extends Clock {
        private long currentMillis = System.currentTimeMillis();

        @Override
        public java.time.ZoneId getZone() {
            return java.time.ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public java.time.Instant instant() {
            return java.time.Instant.ofEpochMilli(currentMillis);
        }

        void advanceMillis(long millis) {
            currentMillis += millis;
        }
    }
}
