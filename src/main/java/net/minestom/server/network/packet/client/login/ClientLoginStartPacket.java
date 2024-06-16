package net.minestom.server.network.packet.client.login;

import net.kyori.adventure.text.Component;
import net.minestom.server.MinecraftServer;
import net.minestom.server.network.NetworkBuffer;
import net.minestom.server.network.packet.client.ClientPacket;
import net.minestom.server.network.packet.server.login.LoginDisconnectPacket;
import net.minestom.server.network.player.PlayerConnection;
import net.minestom.server.network.player.PlayerSocketConnection;
import net.minestom.server.utils.async.AsyncUtils;
import net.minestom.server.utils.validate.Check;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.function.Consumer;

import static net.minestom.server.network.NetworkBuffer.STRING;
import static net.minestom.server.network.NetworkBuffer.UUID;

public record ClientLoginStartPacket(@NotNull String username,
                                     @NotNull UUID profileId) implements ClientPacket {

    public ClientLoginStartPacket(@NotNull NetworkBuffer reader) {
        this(reader.read(STRING), reader.read(UUID));
    }

    @Override
    public void write(@NotNull NetworkBuffer writer) {
        if (username.length() > 16)
            throw new IllegalArgumentException("Username is not allowed to be longer than 16 characters");
        writer.write(STRING, username);
        writer.write(UUID, profileId);
    }

    private static Consumer<PlayerSocketConnection> authHandler;
    public static void setAuthHandler(Consumer<PlayerSocketConnection> handler) {
        Check.stateCondition(MinecraftServer.process().isAlive(), "The server has already been started!");
        Check.stateCondition(authHandler == null, "The auth handler is already set!");
        authHandler = handler;
    }

    @Override
    public void listener(PlayerConnection connection) {
        if(connection instanceof PlayerSocketConnection socketConnection) {
            socketConnection.UNSAFE_setLoginUsername(username);
            if(authHandler != null) {
                authHandler.accept(socketConnection);
                return;
            }
        }
        AsyncUtils.runAsync(() -> {
            try {
                MinecraftServer.getConnectionManager().createPlayer(connection, MinecraftServer.getConnectionManager().getPlayerConnectionUuid(connection, username), username);
            } catch (Exception exception) {
                connection.sendPacket(new LoginDisconnectPacket(Component.text(exception.getClass().getSimpleName() + ": " + exception.getMessage())));
                connection.disconnect();
            }
        });
    }
}
