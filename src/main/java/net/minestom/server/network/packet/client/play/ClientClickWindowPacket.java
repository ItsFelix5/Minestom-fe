package net.minestom.server.network.packet.client.play;

import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.inventory.AbstractInventory;
import net.minestom.server.inventory.PlayerInventory;
import net.minestom.server.item.ItemStack;
import net.minestom.server.network.NetworkBuffer;
import net.minestom.server.network.packet.client.ClientPacket;
import net.minestom.server.network.packet.server.common.PingPacket;
import net.minestom.server.network.packet.server.play.SetSlotPacket;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static net.minestom.server.network.NetworkBuffer.*;

public record ClientClickWindowPacket(byte windowId, int stateId,
                                      short slot, byte button, @NotNull ClickType clickType,
                                      @NotNull List<ChangedSlot> changedSlots,
                                      @NotNull ItemStack clickedItem) implements ClientPacket {
    public static final int MAX_CHANGED_SLOTS = 128;

    public ClientClickWindowPacket {
        changedSlots = List.copyOf(changedSlots);
    }

    public ClientClickWindowPacket(@NotNull NetworkBuffer reader) {
        this(reader.read(BYTE), reader.read(VAR_INT),
                reader.read(SHORT), reader.read(BYTE), reader.readEnum(ClickType.class),
                reader.readCollection(ChangedSlot::new, MAX_CHANGED_SLOTS), reader.read(ItemStack.NETWORK_TYPE));
    }

    @Override
    public void write(@NotNull NetworkBuffer writer) {
        writer.write(BYTE, windowId);
        writer.write(VAR_INT, stateId);
        writer.write(SHORT, slot);
        writer.write(BYTE, button);
        writer.write(VAR_INT, clickType.ordinal());
        writer.writeCollection(changedSlots);
        writer.write(ItemStack.NETWORK_TYPE, clickedItem);
    }

    @Override
    public void handle(Player player) {
        final AbstractInventory inventory = windowId == 0 ? player.getInventory() : player.getOpenInventory();
        if (inventory == null) return; // Invalid packet

        boolean successful = false;

        // prevent click in a non-interactive slot (why does it exist?)
        if (slot == -1) return;
        if (clickType == ClickType.PICKUP) {
            if (button == 0) {
                if (slot != -999) successful = inventory.leftClick(player, slot);
                else successful = inventory.drop(player, true, slot, button);
            } else if (button == 1) {
                if (slot != -999) successful = inventory.rightClick(player, slot);
                else successful = inventory.drop(player, false, slot, button);
            }
        } else if (clickType == ClickType.QUICK_MOVE) successful = inventory.shiftClick(player, slot);
        else if (clickType == ClickType.SWAP) successful = inventory.changeHeld(player, slot, button);
        else if (clickType == ClickType.CLONE) {
            successful = player.getGameMode() == GameMode.CREATIVE;
            if (successful) {
                if (inventory instanceof PlayerInventory playerInventory) playerInventory.setCursorItem(clickedItem);
                else player.getInventory().getCursorItem();
            }
        } else if (clickType == ClickType.THROW) successful = inventory.drop(player, false, slot, button);
        else if (clickType == ClickType.QUICK_CRAFT) successful = inventory.dragging(player, slot, button);
        else if (clickType == ClickType.PICKUP_ALL) successful = inventory.doubleClick(player, slot);

        // Prevent ghost item when the click is cancelled
        if (!successful) {
            ItemStack cursorItem = player.getInventory().getCursorItem();
            player.sendPacket(SetSlotPacket.createCursorPacket(cursorItem));
        }

        // Prevent the player from picking a ghost item in cursor
        ItemStack cursorItem;
        if (inventory instanceof PlayerInventory playerInventory) cursorItem = playerInventory.getCursorItem();
        else cursorItem = player.getInventory().getCursorItem();

        player.sendPacket(SetSlotPacket.createCursorPacket(cursorItem));

        // (Why is the ping packet necessary?)
        player.sendPacket(new PingPacket((1 << 30) | (windowId << 16)));
    }

    public record ChangedSlot(short slot, @NotNull ItemStack item) implements Writer {
        public ChangedSlot(@NotNull NetworkBuffer reader) {
            this(reader.read(SHORT), reader.read(ItemStack.NETWORK_TYPE));
        }

        @Override
        public void write(@NotNull NetworkBuffer writer) {
            writer.write(SHORT, slot);
            writer.write(ItemStack.NETWORK_TYPE, item);
        }
    }

    public enum ClickType {
        PICKUP,
        QUICK_MOVE,
        SWAP,
        CLONE,
        THROW,
        QUICK_CRAFT,
        PICKUP_ALL
    }
}
