package net.minestom.server.network.packet.client.play;

import net.minestom.server.entity.Player;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.event.player.PlayerAnvilInputEvent;
import net.minestom.server.inventory.Inventory;
import net.minestom.server.inventory.InventoryType;
import net.minestom.server.network.NetworkBuffer;
import net.minestom.server.network.packet.client.ClientPacket;
import org.jetbrains.annotations.NotNull;

import static net.minestom.server.network.NetworkBuffer.STRING;

public record ClientNameItemPacket(@NotNull String itemName) implements ClientPacket {
    public ClientNameItemPacket {
        if (itemName.length() > Short.MAX_VALUE) {
            throw new IllegalArgumentException("ItemStack name cannot be longer than Short.MAX_VALUE characters!");
        }
    }

    public ClientNameItemPacket(@NotNull NetworkBuffer reader) {
        this(reader.read(STRING));
    }

    @Override
    public void write(@NotNull NetworkBuffer writer) {
        writer.write(STRING, itemName);
    }

    @Override
    public void handle(Player player) {
        if (!(player.getOpenInventory() instanceof Inventory openInventory))
            return;
        if (openInventory.getInventoryType() != InventoryType.ANVIL)
            return;

        EventDispatcher.call(new PlayerAnvilInputEvent(player, itemName));
    }
}
