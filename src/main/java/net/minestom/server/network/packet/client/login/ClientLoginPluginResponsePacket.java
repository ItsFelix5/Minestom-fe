package net.minestom.server.network.packet.client.login;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.MinecraftServer;
import net.minestom.server.network.NetworkBuffer;
import net.minestom.server.network.packet.client.ClientPacket;
import net.minestom.server.network.packet.server.login.LoginDisconnectPacket;
import net.minestom.server.network.player.PlayerConnection;
import net.minestom.server.network.plugin.LoginPluginMessageProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static net.minestom.server.network.NetworkBuffer.RAW_BYTES;
import static net.minestom.server.network.NetworkBuffer.VAR_INT;

public record ClientLoginPluginResponsePacket(int messageId, byte @Nullable [] data) implements ClientPacket {

    public ClientLoginPluginResponsePacket(@NotNull NetworkBuffer reader) {
        this(reader.read(VAR_INT), reader.readOptional(RAW_BYTES));
    }

    @Override
    public boolean processImmediately() {
        return true;
    }

    @Override
    public void write(@NotNull NetworkBuffer writer) {
        writer.write(VAR_INT, messageId);
        writer.writeOptional(RAW_BYTES, data);
    }

    @Override
    public void handle(PlayerConnection connection) {
        try {
            LoginPluginMessageProcessor messageProcessor = connection.loginPluginMessageProcessor();
            messageProcessor.handleResponse(messageId, data);
        } catch (Throwable t) {
            MinecraftServer.LOGGER.error("Error handling Login Plugin Response", t);
            connection.sendPacket(new LoginDisconnectPacket(Component.text("Error during login!", NamedTextColor.RED)));
            connection.disconnect();
        }
    }
}
