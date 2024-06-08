package net.minestom.server.listener.preplay;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.MinecraftServer;
import net.minestom.server.network.ConnectionState;
import net.minestom.server.network.packet.client.handshake.ClientHandshakePacket;
import net.minestom.server.network.packet.server.login.LoginDisconnectPacket;
import net.minestom.server.network.player.PlayerConnection;
import net.minestom.server.network.player.PlayerSocketConnection;
import org.jetbrains.annotations.NotNull;

public final class HandshakeListener {
    /**
     * Text sent if a player tries to connect with an invalid version of the client
     */
    private static final Component INVALID_VERSION_TEXT = Component.text("Invalid Version, please use " + MinecraftServer.VERSION_NAME, NamedTextColor.RED);

    public static void listener(@NotNull ClientHandshakePacket packet, @NotNull PlayerConnection connection) {
        String address = packet.serverAddress();
        switch (packet.intent()) {
            case STATUS -> connection.setConnectionState(ConnectionState.STATUS);
            case LOGIN -> {
                connection.setConnectionState(ConnectionState.LOGIN);
                if (packet.protocolVersion() != MinecraftServer.PROTOCOL_VERSION) disconnect(connection, INVALID_VERSION_TEXT); // Incorrect client version
            }
            case TRANSFER -> throw new UnsupportedOperationException("Transfer intent is not supported in HandshakeListener");
            default -> {
                // Unexpected error
            }
        }

        // Give to the connection the server info that the client used
        if (connection instanceof PlayerSocketConnection socketConnection) socketConnection.refreshServerInformation(address, packet.serverPort(), packet.protocolVersion());
    }

    private static void disconnect(@NotNull PlayerConnection connection, @NotNull Component reason) {
        connection.sendPacket(new LoginDisconnectPacket(reason));
        connection.disconnect();
    }
}
