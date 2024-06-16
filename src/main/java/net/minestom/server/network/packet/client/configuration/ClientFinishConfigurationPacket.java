package net.minestom.server.network.packet.client.configuration;

import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.network.NetworkBuffer;
import net.minestom.server.network.packet.client.ClientPacket;
import org.jetbrains.annotations.NotNull;

public record ClientFinishConfigurationPacket() implements ClientPacket {

    public ClientFinishConfigurationPacket(@NotNull NetworkBuffer buffer) {
        this();
    }

    @Override
    public void write(@NotNull NetworkBuffer writer) {
    }

    @Override
    public void listener(Player player) {
        MinecraftServer.getConnectionManager().transitionConfigToPlay(player);
    }
}
