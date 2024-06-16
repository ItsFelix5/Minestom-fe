package net.minestom.server.extras;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.MinecraftServer;
import net.minestom.server.network.NetworkBuffer;
import net.minestom.server.network.packet.client.login.ClientLoginStartPacket;
import net.minestom.server.network.packet.server.login.LoginDisconnectPacket;
import net.minestom.server.network.player.GameProfile;
import net.minestom.server.network.player.PlayerSocketConnection;
import org.jetbrains.annotations.NotNull;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static net.minestom.server.network.NetworkBuffer.*;

/**
 * Support for <a href="https://velocitypowered.com/">Velocity</a> modern forwarding.
 * <p>
 * Can be enabled by simply calling {@link #enable(String)}.
 */
public final class VelocityProxy {
    public static final String PLAYER_INFO_CHANNEL = "velocity:player_info";
    private static final int SUPPORTED_FORWARDING_VERSION = 1;
    private static final String MAC_ALGORITHM = "HmacSHA256";
    private static Key key;

    /**
     * Enables velocity modern forwarding.
     *
     * @param secret the forwarding secret,
     *               be sure to do not hardcode it in your code but to retrieve it from a file or anywhere else safe
     */
    public static void enable(@NotNull String secret) {
        VelocityProxy.key = new SecretKeySpec(secret.getBytes(), MAC_ALGORITHM);
        ClientLoginStartPacket.setAuthHandler(VelocityProxy::handleLogin);
    }

    private static void handleLogin(PlayerSocketConnection connection) {
        connection.loginPluginMessageProcessor().request(PLAYER_INFO_CHANNEL, null)
                .thenAccept(response -> {
                    byte[] data = response.getPayload();

                    SocketAddress socketAddress = null;
                    GameProfile gameProfile = null;
                    boolean success = false;
                    if (data != null && data.length > 0) {
                        NetworkBuffer buffer = new NetworkBuffer(ByteBuffer.wrap(data));
                        success = checkIntegrity(buffer);
                        if (success) {
                            // Get the real connection address
                            final InetAddress address;
                            try {
                                address = InetAddress.getByName(buffer.read(STRING));
                            } catch (UnknownHostException e) {
                                MinecraftServer.getExceptionManager().handleException(e);
                                return;
                            }
                            final int port = ((java.net.InetSocketAddress) connection.getRemoteAddress()).getPort();
                            socketAddress = new InetSocketAddress(address, port);
                            gameProfile = new GameProfile(buffer);
                        }
                    }

                    if (success) {
                        connection.setRemoteAddress(socketAddress);
                        connection.UNSAFE_setProfile(gameProfile);
                        MinecraftServer.getConnectionManager().createPlayer(connection, gameProfile.uuid(), gameProfile.name());
                    } else connection.sendPacket(new LoginDisconnectPacket(Component.text("Invalid proxy response!", NamedTextColor.RED)));
                });
    }

    public static boolean checkIntegrity(@NotNull NetworkBuffer buffer) {
        final byte[] signature = new byte[32];
        for (int i = 0; i < signature.length; i++) {
            signature[i] = buffer.read(BYTE);
        }
        final int index = buffer.readIndex();
        final byte[] data = buffer.read(RAW_BYTES);
        buffer.readIndex(index);
        try {
            Mac mac = Mac.getInstance(MAC_ALGORITHM);
            mac.init(key);
            final byte[] mySignature = mac.doFinal(data);
            if (!MessageDigest.isEqual(signature, mySignature)) {
                return false;
            }
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            e.printStackTrace();
        }
        final int version = buffer.read(VAR_INT);
        return version == SUPPORTED_FORWARDING_VERSION;
    }
}
