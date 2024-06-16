package net.minestom.server.extras;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.MinecraftServer;
import net.minestom.server.extras.mojangAuth.MojangCrypt;
import net.minestom.server.network.packet.client.login.ClientEncryptionResponsePacket;
import net.minestom.server.network.packet.client.login.ClientLoginStartPacket;
import net.minestom.server.network.packet.server.login.EncryptionRequestPacket;
import net.minestom.server.network.packet.server.login.LoginDisconnectPacket;
import net.minestom.server.network.player.GameProfile;
import net.minestom.server.network.player.PlayerSocketConnection;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class MojangAuth {
    private static volatile boolean enabled = false;
    private static volatile KeyPair keyPair;

    /**
     * Enables mojang authentication on the server.
     * <p>
     * Be aware that enabling a proxy will make Mojang authentication ignored.
     */
    public static void init() {
        MojangAuth.enabled = true;
        // Generate necessary fields...
        MojangAuth.keyPair = MojangCrypt.generateKeyPair();
        ClientLoginStartPacket.setAuthHandler(MojangAuth::handleLogin);
    }

    private static void handleLogin(PlayerSocketConnection connection) {
        if (MinecraftServer.getConnectionManager().getOnlinePlayerByUsername(connection.getLoginUsername()) != null) {
            connection.sendPacket(new LoginDisconnectPacket(Component.text("You are already on this server", NamedTextColor.RED)));
            connection.disconnect();
            return;
        }

        final byte[] publicKey = MojangAuth.getKeyPair().getPublic().getEncoded();
        byte[] nonce = new byte[4];
        ThreadLocalRandom.current().nextBytes(nonce);
        connection.setNonce(nonce);
        connection.sendPacket(new EncryptionRequestPacket("", publicKey, nonce, true));
    }

    private static final Gson GSON = new Gson();
    public static void handleEncryption(PlayerSocketConnection connection, ClientEncryptionResponsePacket packet) {
        if(!enabled) return;
        final String loginUsername = connection.getLoginUsername();
        if (loginUsername == null || loginUsername.isEmpty()) return; // Shouldn't happen

        final boolean hasPublicKey = connection.playerPublicKey() != null;
        final boolean verificationFailed = hasPublicKey || !Arrays.equals(connection.getNonce(),
                MojangCrypt.decryptUsingKey(MojangAuth.getKeyPair().getPrivate(), packet.encryptedVerifyToken()));

        if (verificationFailed) {
            MinecraftServer.LOGGER.error("Encryption failed for {}", loginUsername);
            return;
        }

        final byte[] digestedData = MojangCrypt.digestData("", MojangAuth.getKeyPair().getPublic(), MojangCrypt.decryptByteToSecretKey(MojangAuth.getKeyPair().getPrivate(), packet.sharedSecret()));
        if (digestedData == null) {
            // Incorrect key, probably because of the client
            MinecraftServer.LOGGER.error("Connection {} failed initializing encryption.", connection.getRemoteAddress());
            connection.disconnect();
            return;
        }
        // Query Mojang's session server.
        final String serverId = new BigInteger(digestedData).toString(16);
        final String username = URLEncoder.encode(loginUsername, StandardCharsets.UTF_8);

        final String url = String.format("https://sessionserver.mojang.com/session/minecraft/hasJoined?username=%s&serverId=%s", username, serverId);
        // TODO: Add ability to add ip query tag. See: https://wiki.vg/Protocol_Encryption#Authentication

        final HttpClient client = HttpClient.newHttpClient();
        final HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
        client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).whenComplete((response, throwable) -> {
            final boolean ok = throwable == null && response.statusCode() == 200 && response.body() != null && !response.body().isEmpty();

            if (!ok) {
                if (throwable != null) MinecraftServer.getExceptionManager().handleException(throwable);

                if (connection.getPlayer() != null) connection.getPlayer().kick(Component.text("Failed to contact Mojang's Session Servers (Are they down?)"));
                else connection.disconnect();
                return;
            }
            try {
                final JsonObject gameProfile = GSON.fromJson(response.body(), JsonObject.class);
                connection.setEncryptionKey(MojangCrypt.decryptByteToSecretKey(MojangAuth.getKeyPair().getPrivate(), packet.sharedSecret()));
                UUID profileUUID = java.util.UUID.fromString(gameProfile.get("id").getAsString()
                        .replaceFirst("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5"));
                final String profileName = gameProfile.get("name").getAsString();

                MinecraftServer.LOGGER.info("UUID of player {} is {}", loginUsername, profileUUID);
                MinecraftServer.getConnectionManager().createPlayer(connection, profileUUID, profileName);
                List<GameProfile.Property> propertyList = new ArrayList<>();
                for (JsonElement element : gameProfile.get("properties").getAsJsonArray()) {
                    JsonObject object = element.getAsJsonObject();
                    propertyList.add(new GameProfile.Property(object.get("name").getAsString(), object.get("value").getAsString(), object.get("signature").getAsString()));
                }
                connection.UNSAFE_setProfile(new GameProfile(profileUUID, profileName, propertyList));
            } catch (Exception e) {
                MinecraftServer.getExceptionManager().handleException(e);
            }
        });
    }

    public static @Nullable KeyPair getKeyPair() {
        return keyPair;
    }
}
