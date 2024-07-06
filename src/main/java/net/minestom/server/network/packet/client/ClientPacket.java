package net.minestom.server.network.packet.client;

import net.minestom.server.entity.Player;
import net.minestom.server.network.NetworkBuffer;
import net.minestom.server.network.player.PlayerConnection;
import org.jetbrains.annotations.ApiStatus;

/**
 * Represents a packet received from a client.
 * <p>
 * Packets are value-based, and should therefore not be reliant on identity.
 */
public interface ClientPacket extends NetworkBuffer.Writer {
    default void handle(PlayerConnection connection){
        handle(connection.getPlayer());
    }

    default void handle(Player player){}

    /**
     * Determines whether this packet should be processed immediately
     * or wait until the next server tick.
     * @return true if this packet should process immediately
     */
    @ApiStatus.Internal
    default boolean processImmediately() {
        return false;
    }
}
