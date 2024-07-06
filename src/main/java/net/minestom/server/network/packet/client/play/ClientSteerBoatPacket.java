package net.minestom.server.network.packet.client.play;

import net.minestom.server.entity.Entity;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.metadata.other.BoatMeta;
import net.minestom.server.network.NetworkBuffer;
import net.minestom.server.network.packet.client.ClientPacket;
import org.jetbrains.annotations.NotNull;

import static net.minestom.server.network.NetworkBuffer.BOOLEAN;

public record ClientSteerBoatPacket(boolean leftPaddleTurning, boolean rightPaddleTurning) implements ClientPacket {
    public ClientSteerBoatPacket(@NotNull NetworkBuffer reader) {
        this(reader.read(BOOLEAN), reader.read(BOOLEAN));
    }

    @Override
    public void write(@NotNull NetworkBuffer writer) {
        writer.write(BOOLEAN, leftPaddleTurning);
        writer.write(BOOLEAN, rightPaddleTurning);
    }

    @Override
    public void handle(Player player) {
        final Entity vehicle = player.getVehicle();
        /* The packet may have been received after already exiting the vehicle. */
        if (vehicle == null) return;
        if (!(vehicle.getEntityMeta() instanceof BoatMeta boat)) return;
        boat.setLeftPaddleTurning(leftPaddleTurning);
        boat.setRightPaddleTurning(rightPaddleTurning);
    }
}
