package me.mcgg.azreyzaako.mcggrtp.velocity.server;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class ServerTransferService {
    private final ProxyServer proxyServer;

    public ServerTransferService(ProxyServer proxyServer) {
        this.proxyServer = proxyServer;
    }

    public boolean serverExists(String name) {
        return proxyServer.getServer(name).isPresent();
    }

    public Optional<RegisteredServer> findServer(String name) {
        return proxyServer.getServer(name);
    }

    public CompletableFuture<Boolean> connect(Player player, String targetServer) {
        Optional<RegisteredServer> server = proxyServer.getServer(targetServer);
        if (server.isEmpty()) {
            return CompletableFuture.completedFuture(false);
        }
        return player.createConnectionRequest(server.get()).connect().thenApply(result -> result.isSuccessful());
    }
}
