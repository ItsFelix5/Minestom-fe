package net.minestom.server.network.packet.client.login;

import net.minestom.server.MinecraftServer;
import net.minestom.server.network.NetworkBuffer;
import net.minestom.server.network.packet.client.ClientPacket;
import net.minestom.server.network.player.PlayerConnection;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public record ClientLoginAcknowledgedPacket() implements ClientPacket {

    public ClientLoginAcknowledgedPacket(@NotNull NetworkBuffer buffer) {
        this();
    }

    @Override
    public boolean processImmediately() {
        return true;
    }

    @Override
    public void write(@NotNull NetworkBuffer writer) {
    }

    @Override
    public void handle(PlayerConnection connection) {
        MinecraftServer.getConnectionManager().doConfiguration(Objects.requireNonNull(connection.getPlayer()), true);
    }
}
