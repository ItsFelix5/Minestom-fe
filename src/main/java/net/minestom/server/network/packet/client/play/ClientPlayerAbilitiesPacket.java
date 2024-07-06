package net.minestom.server.network.packet.client.play;

import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.event.player.PlayerStartFlyingEvent;
import net.minestom.server.event.player.PlayerStopFlyingEvent;
import net.minestom.server.network.NetworkBuffer;
import net.minestom.server.network.packet.client.ClientPacket;
import org.jetbrains.annotations.NotNull;

import static net.minestom.server.network.NetworkBuffer.BYTE;

public record ClientPlayerAbilitiesPacket(byte flags) implements ClientPacket {
    public ClientPlayerAbilitiesPacket(@NotNull NetworkBuffer reader) {
        this(reader.read(BYTE));
    }

    @Override
    public void write(@NotNull NetworkBuffer writer) {
        writer.write(BYTE, flags);
    }

    @Override
    public void handle(Player player) {
        if (player.isAllowFlying() || player.getGameMode() == GameMode.CREATIVE) {
            final boolean isFlying = (flags & 0x2) > 0;

            player.refreshFlying(isFlying);

            if (isFlying) EventDispatcher.call(new PlayerStartFlyingEvent(player));
            else EventDispatcher.call(new PlayerStopFlyingEvent(player));
        }
    }
}
