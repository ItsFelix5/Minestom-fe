package net.minestom.server.network.packet.client.play;

import net.minestom.server.entity.Player;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.event.inventory.InventoryCloseEvent;
import net.minestom.server.inventory.Inventory;
import net.minestom.server.network.NetworkBuffer;
import net.minestom.server.network.packet.client.ClientPacket;
import org.jetbrains.annotations.NotNull;

import static net.minestom.server.network.NetworkBuffer.BYTE;

public record ClientCloseWindowPacket(byte windowId) implements ClientPacket {
    public ClientCloseWindowPacket(@NotNull NetworkBuffer reader) {
        this(reader.read(BYTE));
    }

    @Override
    public void write(@NotNull NetworkBuffer writer) {
        writer.write(BYTE, windowId);
    }

    @Override
    public void handle(Player player) {
        // if windowId == 0 then it is player's inventory, meaning that they hadn't been any open inventory packet
        InventoryCloseEvent inventoryCloseEvent = new InventoryCloseEvent(player.getOpenInventory(), player);
        EventDispatcher.call(inventoryCloseEvent);

        player.closeInventory(true);

        Inventory newInventory = inventoryCloseEvent.getNewInventory();
        if (newInventory != null)
            player.openInventory(newInventory);
    }
}
