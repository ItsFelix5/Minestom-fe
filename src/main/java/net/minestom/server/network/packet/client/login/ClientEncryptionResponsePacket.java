package net.minestom.server.network.packet.client.login;

import net.minestom.server.extras.MojangAuth;
import net.minestom.server.network.NetworkBuffer;
import net.minestom.server.network.packet.client.ClientPacket;
import net.minestom.server.network.player.PlayerConnection;
import net.minestom.server.network.player.PlayerSocketConnection;
import net.minestom.server.utils.async.AsyncUtils;
import org.jetbrains.annotations.NotNull;

import static net.minestom.server.network.NetworkBuffer.BYTE_ARRAY;

public record ClientEncryptionResponsePacket(byte[] sharedSecret,
                                             byte[] encryptedVerifyToken) implements ClientPacket {

    public ClientEncryptionResponsePacket(@NotNull NetworkBuffer reader) {
        this(reader.read(BYTE_ARRAY), reader.read(BYTE_ARRAY));
    }

    @Override
    public boolean processImmediately() {
        return true;
    }

    @Override
    public void write(@NotNull NetworkBuffer writer) {
        writer.write(BYTE_ARRAY, sharedSecret);
        writer.write(BYTE_ARRAY, encryptedVerifyToken);
    }

    @Override
    public void handle(PlayerConnection connection) {
        // Encryption is only support for socket connection
        if (connection instanceof PlayerSocketConnection socketConnection) AsyncUtils.runAsync(() -> MojangAuth.handleEncryption(socketConnection, this));
    }
}
