package net.minestom.server.network.packet.client.common;

import net.minestom.server.event.EventDispatcher;
import net.minestom.server.event.server.ClientPingServerEvent;
import net.minestom.server.network.ConnectionState;
import net.minestom.server.network.NetworkBuffer;
import net.minestom.server.network.packet.client.ClientPacket;
import net.minestom.server.network.packet.server.common.PingResponsePacket;
import net.minestom.server.network.player.PlayerConnection;
import net.minestom.server.timer.Scheduler;
import org.jetbrains.annotations.NotNull;

import static net.minestom.server.network.NetworkBuffer.LONG;

public record ClientPingRequestPacket(long number) implements ClientPacket {
    public ClientPingRequestPacket(@NotNull NetworkBuffer reader) {
        this(reader.read(LONG));
    }

    @Override
    public boolean processImmediately() {
        return true;
    }

    @Override
    public void write(@NotNull NetworkBuffer writer) {
        writer.write(LONG, number);
    }

    @Override
    public void listener(PlayerConnection connection) {
        if(connection.getConnectionState() == ConnectionState.PLAY) {
            connection.getPlayer().sendPacket(new PingResponsePacket(number));
            return;
        }
        final ClientPingServerEvent clientPingEvent = new ClientPingServerEvent(connection, number);
        EventDispatcher.call(clientPingEvent);

        if (clientPingEvent.isCancelled()) connection.disconnect();
        else {
            if (clientPingEvent.getDelay().isZero()) {
                connection.sendPacket(new PingResponsePacket(clientPingEvent.getPayload()));
                connection.disconnect();
            } else Scheduler.buildTask(() -> {
                    connection.sendPacket(new PingResponsePacket(clientPingEvent.getPayload()));
                    connection.disconnect();
                }).delay(clientPingEvent.getDelay()).schedule();
        }
    }
}
