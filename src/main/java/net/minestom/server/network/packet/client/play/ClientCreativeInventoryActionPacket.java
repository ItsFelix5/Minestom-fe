package net.minestom.server.network.packet.client.play;

import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.inventory.PlayerInventory;
import net.minestom.server.item.ItemStack;
import net.minestom.server.network.NetworkBuffer;
import net.minestom.server.network.packet.client.ClientPacket;
import net.minestom.server.utils.inventory.PlayerInventoryUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

import static net.minestom.server.network.NetworkBuffer.SHORT;

public record ClientCreativeInventoryActionPacket(short slot, @NotNull ItemStack item) implements ClientPacket {
    public ClientCreativeInventoryActionPacket(@NotNull NetworkBuffer reader) {
        this(reader.read(SHORT), reader.read(ItemStack.NETWORK_TYPE));
    }

    @Override
    public void write(@NotNull NetworkBuffer writer) {
        writer.write(SHORT, slot);
        writer.write(ItemStack.NETWORK_TYPE, item);
    }

    @Override
    public void handle(Player player) {
        if (player.getGameMode() != GameMode.CREATIVE) return;
        if (slot == -1) {
            // Drop item
            player.dropItem(item);
            return;
        }
        // Bounds check
        // 0 is crafting result inventory slot, ignore attempts to place into it
        if (slot < 1 || slot > PlayerInventoryUtils.OFFHAND_SLOT) return;
        // Set item
        short slot = (short) PlayerInventoryUtils.convertPlayerInventorySlot(this.slot, PlayerInventoryUtils.OFFSET);
        PlayerInventory inventory = player.getInventory();
        if (Objects.equals(inventory.getItemStack(slot), item)) return;// Item is already present, ignore

        inventory.setItemStack(slot, item);
    }
}
