package net.minestom.server.network.packet.client.play;

import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.network.NetworkBuffer;
import net.minestom.server.network.packet.client.ClientPacket;
import org.jetbrains.annotations.NotNull;

public record ClientConfigurationAckPacket() implements ClientPacket {
    public ClientConfigurationAckPacket(@NotNull NetworkBuffer buffer) {
        this();
    }

    @Override
    public void write(@NotNull NetworkBuffer writer) {
    }

    @Override
    public void handle(Player player) {
        MinecraftServer.getConnectionManager().doConfiguration(player, false);
    }
}
