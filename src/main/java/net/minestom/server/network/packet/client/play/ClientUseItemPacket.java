package net.minestom.server.network.packet.client.play;

import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.entity.Player;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.event.player.PlayerItemAnimationEvent;
import net.minestom.server.event.player.PlayerPreEatEvent;
import net.minestom.server.event.player.PlayerUseItemEvent;
import net.minestom.server.inventory.PlayerInventory;
import net.minestom.server.item.ItemComponent;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.item.component.Food;
import net.minestom.server.item.component.PotionContents;
import net.minestom.server.network.NetworkBuffer;
import net.minestom.server.network.packet.client.ClientPacket;
import net.minestom.server.network.packet.server.play.AcknowledgeBlockChangePacket;
import org.jetbrains.annotations.NotNull;

import static net.minestom.server.network.NetworkBuffer.VAR_INT;

public record ClientUseItemPacket(@NotNull Player.Hand hand, int sequence, float yaw, float pitch) implements ClientPacket {
    public ClientUseItemPacket(@NotNull NetworkBuffer reader) {
        this(reader.readEnum(Player.Hand.class), reader.read(VAR_INT),
                reader.read(NetworkBuffer.FLOAT), reader.read(NetworkBuffer.FLOAT));
    }

    @Override
    public void write(@NotNull NetworkBuffer writer) {
        writer.writeEnum(Player.Hand.class, hand);
        writer.write(VAR_INT, sequence);
        writer.write(NetworkBuffer.FLOAT, yaw);
        writer.write(NetworkBuffer.FLOAT, pitch);
    }

    @Override
    public void listener(Player player) {
        final ItemStack itemStack = player.getInventory().getItemInHand(hand);
        final Material material = itemStack.material();

        long itemUseTime = 0;
        final Food food = itemStack.get(ItemComponent.FOOD);
        if(food != null) itemUseTime = food.eatDurationTicks();
        else if (material == Material.POTION) itemUseTime = PotionContents.POTION_DRINK_TIME;
        else if (material == Material.BOW || material == Material.CROSSBOW
                || material == Material.SHIELD || material == Material.TRIDENT
                || material == Material.SPYGLASS || material == Material.GOAT_HORN
                || material == Material.BRUSH) itemUseTime = -1;
        PlayerUseItemEvent useItemEvent = new PlayerUseItemEvent(player, hand, itemStack, itemUseTime);
        EventDispatcher.call(useItemEvent);

        player.sendPacket(new AcknowledgeBlockChangePacket(sequence));
        final PlayerInventory playerInventory = player.getInventory();
        if (useItemEvent.isCancelled()) {
            playerInventory.update();
            return;
        }

        // Equip armor with right click
        final EquipmentSlot equipmentSlot = material.registry().equipmentSlot();
        if (equipmentSlot != null) {
            final ItemStack currentlyEquipped = player.getEquipment(equipmentSlot);
            player.setEquipment(equipmentSlot, itemStack);
            player.setItemInHand(hand, currentlyEquipped);
        }

        itemUseTime = useItemEvent.getItemUseTime();
        PlayerItemAnimationEvent.ItemAnimationType itemAnimationType;

        if (material == Material.BOW) itemAnimationType = PlayerItemAnimationEvent.ItemAnimationType.BOW;
        else if (material == Material.CROSSBOW) itemAnimationType = PlayerItemAnimationEvent.ItemAnimationType.CROSSBOW;
        else if (material == Material.SHIELD) itemAnimationType = PlayerItemAnimationEvent.ItemAnimationType.SHIELD;
        else if (material == Material.TRIDENT) itemAnimationType = PlayerItemAnimationEvent.ItemAnimationType.TRIDENT;
        else if (material == Material.SPYGLASS) itemAnimationType = PlayerItemAnimationEvent.ItemAnimationType.SPYGLASS;
        else if (material == Material.GOAT_HORN) itemAnimationType = PlayerItemAnimationEvent.ItemAnimationType.HORN;
        else if (material == Material.BRUSH) itemAnimationType = PlayerItemAnimationEvent.ItemAnimationType.BRUSH;
        else if (itemStack.has(ItemComponent.FOOD) || material == Material.MILK_BUCKET || material == Material.POTION) {
            itemAnimationType = PlayerItemAnimationEvent.ItemAnimationType.EAT;

            PlayerPreEatEvent playerPreEatEvent = new PlayerPreEatEvent(player, itemStack, hand, itemUseTime);
            EventDispatcher.call(playerPreEatEvent);
            if (playerPreEatEvent.isCancelled()) return;
            itemUseTime = playerPreEatEvent.getEatingTime();
        } else itemAnimationType = PlayerItemAnimationEvent.ItemAnimationType.OTHER;

        if (itemUseTime != 0) {
            player.refreshItemUse(hand, itemUseTime);

            PlayerItemAnimationEvent playerItemAnimationEvent = new PlayerItemAnimationEvent(player, itemAnimationType, hand);
            EventDispatcher.callCancellable(playerItemAnimationEvent, () -> {
                player.refreshActiveHand(true, hand == Player.Hand.OFF, false);
                player.sendPacketToViewers(player.getMetadataPacket());
            });
        }
    }
}
