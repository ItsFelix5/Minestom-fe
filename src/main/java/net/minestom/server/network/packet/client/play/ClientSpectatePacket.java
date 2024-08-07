package net.minestom.server.network.packet.client.play;

import net.minestom.server.entity.Entity;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.event.player.PlayerSpectateEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.network.NetworkBuffer;
import net.minestom.server.network.packet.client.ClientPacket;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * The ClientSpectatePacket is sent when the client interacts with their hotbar to switch between entities.
 * Contrary to its name, it is actually used to teleport the player to the entity they are switching to,
 * rather than spectating them.
 */
public record ClientSpectatePacket(@NotNull UUID target) implements ClientPacket {
    public ClientSpectatePacket(@NotNull NetworkBuffer reader) {
        this(reader.read(NetworkBuffer.UUID));
    }

    @Override
    public void write(@NotNull NetworkBuffer writer) {
        writer.write(NetworkBuffer.UUID, target);
    }

    @Override
    public void handle(Player player) {
        // Ignore if the player is not in spectator mode
        if (player.getGameMode() != GameMode.SPECTATOR) return;

        final Entity target = player.getInstance().getEntityByUuid(this.target);

        // Check if the target is valid
        if (target == null || target == player) return;

        // Ignore if they're not attached to any instances
        Instance targetInstance = target.getInstance();
        Instance playerInstance = player.getInstance();
        if (targetInstance == null || playerInstance == null) return;

        // Ignore if they're not in the same instance. Vanilla actually allows for
        // cross-instance spectating, but it's not really a good idea for Minestom.
        if (targetInstance.getUniqueId() != playerInstance.getUniqueId()) return;

        // Despite the name of this packet being spectate, it is sent when the player
        // uses their hotbar to switch between entities, which actually performs a teleport
        // instead of a spectate.
        EventDispatcher.call(new PlayerSpectateEvent(player, target));
    }
}
