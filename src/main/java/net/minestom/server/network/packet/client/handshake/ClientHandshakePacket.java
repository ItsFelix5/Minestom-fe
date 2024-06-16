package net.minestom.server.network.packet.client.handshake;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.MinecraftServer;
import net.minestom.server.network.ConnectionState;
import net.minestom.server.network.NetworkBuffer;
import net.minestom.server.network.packet.client.ClientPacket;
import net.minestom.server.network.packet.server.login.LoginDisconnectPacket;
import net.minestom.server.network.player.PlayerConnection;
import net.minestom.server.network.player.PlayerSocketConnection;
import org.jetbrains.annotations.NotNull;

import static net.minestom.server.network.NetworkBuffer.*;

public record ClientHandshakePacket(int protocolVersion, @NotNull String serverAddress,
                                    int serverPort, @NotNull Intent intent) implements ClientPacket {

    public ClientHandshakePacket {
        if (serverAddress.length() > 255) throw new IllegalArgumentException("Server address too long: " + serverAddress.length());
    }

    public ClientHandshakePacket(@NotNull NetworkBuffer reader) {
        this(reader.read(VAR_INT), reader.read(STRING),
                reader.read(UNSIGNED_SHORT),
                // Not a readEnum call because the indices are not 0-based
                Intent.fromId(reader.read(VAR_INT)));
    }

    @Override
    public void write(@NotNull NetworkBuffer writer) {
        writer.write(VAR_INT, protocolVersion);
        if (serverAddress.length() > 255) {
            throw new IllegalArgumentException("serverAddress is " + serverAddress.length() + " characters long, maximum allowed is 255.");
        }
        writer.write(STRING, serverAddress);
        writer.write(UNSIGNED_SHORT, serverPort);
        // Not a writeEnum call because the indices are not 0-based
        writer.write(VAR_INT, intent.id());
    }

    /**
     * Text sent if a player tries to connect with an invalid version of the client
     */
    private static final Component INVALID_VERSION_TEXT = Component.text("Invalid Version, please use " + MinecraftServer.VERSION_NAME, NamedTextColor.RED);

    @Override
    public void listener(PlayerConnection connection) {
        switch (intent) {
            case STATUS -> connection.setConnectionState(ConnectionState.STATUS);
            case LOGIN -> {
                connection.setConnectionState(ConnectionState.LOGIN);
                if (protocolVersion != MinecraftServer.PROTOCOL_VERSION) { // Incorrect client version
                    connection.sendPacket(new LoginDisconnectPacket(INVALID_VERSION_TEXT));
                    connection.disconnect();
                }
            }
            case TRANSFER -> throw new UnsupportedOperationException("Transfer intent is not supported");
            default -> {
                // Unexpected error
            }
        }

        // Give to the connection the server info that the client used
        if (connection instanceof PlayerSocketConnection socketConnection) socketConnection.refreshServerInformation(serverAddress, serverPort, protocolVersion);
    }

    public enum Intent {
        STATUS,
        LOGIN,
        TRANSFER;

        public static @NotNull Intent fromId(int id) {
            return switch (id) {
                case 1 -> STATUS;
                case 2 -> LOGIN;
                case 3 -> TRANSFER;
                default -> throw new IllegalArgumentException("Unknown connection intent: " + id);
            };
        }

        public int id() {
            return ordinal() + 1;
        }
    }

}
