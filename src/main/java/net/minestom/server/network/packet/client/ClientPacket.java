package net.minestom.server.network.packet.client;

import net.minestom.server.entity.Player;
import net.minestom.server.network.NetworkBuffer;
import net.minestom.server.network.player.PlayerConnection;

/**
 * Represents a packet received from a client.
 * <p>
 * Packets are value-based, and should therefore not be reliant on identity.
 */
public interface ClientPacket extends NetworkBuffer.Writer {
    default void listener(PlayerConnection connection){
        listener(connection.getPlayer());
    }

    default void listener(Player player){}
}
