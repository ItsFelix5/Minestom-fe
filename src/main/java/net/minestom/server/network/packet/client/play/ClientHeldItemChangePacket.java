package net.minestom.server.network.packet.client.play;

import net.minestom.server.entity.Player;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.event.player.PlayerChangeHeldSlotEvent;
import net.minestom.server.network.NetworkBuffer;
import net.minestom.server.network.packet.client.ClientPacket;
import net.minestom.server.utils.MathUtils;
import org.jetbrains.annotations.NotNull;

import static net.minestom.server.network.NetworkBuffer.SHORT;

public record ClientHeldItemChangePacket(short slot) implements ClientPacket {
    public ClientHeldItemChangePacket(@NotNull NetworkBuffer reader) {
        this(reader.read(SHORT));
    }

    @Override
    public void write(@NotNull NetworkBuffer writer) {
        writer.write(SHORT, slot);
    }

    @Override
    public void handle(Player player) {
        if (!MathUtils.isBetween(slot, 0, 8)) return; // Incorrect packet, ignore

        PlayerChangeHeldSlotEvent changeHeldSlotEvent = new PlayerChangeHeldSlotEvent(player, (byte) slot);
        EventDispatcher.call(changeHeldSlotEvent);

        if (!changeHeldSlotEvent.isCancelled()) {
            // Event hasn't been canceled, process it
            final byte resultSlot = changeHeldSlotEvent.getSlot();

            // If the held slot has been changed by the event, send the change to the player. Otherwise, simply refresh the player field
            if (resultSlot != slot) player.setHeldItemSlot(resultSlot);
            else player.refreshHeldSlot(resultSlot);
        } else player.setHeldItemSlot(player.getHeldSlot());// Event has been canceled, send the last held slot to refresh the client
    }
}
