package net.minestom.server.network.packet.client.common;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.entity.Player;
import net.minestom.server.network.NetworkBuffer;
import net.minestom.server.network.packet.client.ClientPacket;
import org.jetbrains.annotations.NotNull;

import static net.minestom.server.network.NetworkBuffer.LONG;

public record ClientKeepAlivePacket(long id) implements ClientPacket {
    public ClientKeepAlivePacket(@NotNull NetworkBuffer reader) {
        this(reader.read(LONG));
    }

    @Override
    public boolean processImmediately() {
        return true;
    }

    @Override
    public void write(@NotNull NetworkBuffer writer) {
        writer.write(LONG, id);
    }

    @Override
    public void listener(Player player) {
        if (id != player.getLastKeepAlive()) {
            player.kick(Component.text("Bad Keep Alive packet", NamedTextColor.RED));
            return;
        }
        player.refreshAnswerKeepAlive(true);
        // Update latency
        player.refreshLatency((int) (System.currentTimeMillis() - id));
    }
}
