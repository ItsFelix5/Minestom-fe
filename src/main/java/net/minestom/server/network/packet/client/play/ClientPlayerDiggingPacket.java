package net.minestom.server.network.packet.client.play;

import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.metadata.LivingEntityMeta;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.event.item.ItemUpdateStateEvent;
import net.minestom.server.event.player.PlayerCancelDiggingEvent;
import net.minestom.server.event.player.PlayerFinishDiggingEvent;
import net.minestom.server.event.player.PlayerStartDiggingEvent;
import net.minestom.server.event.player.PlayerSwapItemEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockFace;
import net.minestom.server.inventory.PlayerInventory;
import net.minestom.server.item.ItemComponent;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.component.BlockPredicates;
import net.minestom.server.network.NetworkBuffer;
import net.minestom.server.network.packet.client.ClientPacket;
import net.minestom.server.network.packet.server.play.AcknowledgeBlockChangePacket;
import net.minestom.server.network.packet.server.play.BlockEntityDataPacket;
import net.minestom.server.utils.block.BlockUtils;
import org.jetbrains.annotations.NotNull;

import static net.minestom.server.network.NetworkBuffer.*;

public record ClientPlayerDiggingPacket(@NotNull Status status, @NotNull Point blockPosition,
                                        @NotNull BlockFace blockFace, int sequence) implements ClientPacket {
    public ClientPlayerDiggingPacket(@NotNull NetworkBuffer reader) {
        this(reader.readEnum(Status.class), reader.read(BLOCK_POSITION),
                BlockFace.values()[reader.read(BYTE)], reader.read(VAR_INT));
    }

    @Override
    public void write(@NotNull NetworkBuffer writer) {
        writer.writeEnum(Status.class, status);
        writer.write(BLOCK_POSITION, blockPosition);
        writer.write(BYTE, (byte) blockFace.ordinal());
        writer.write(VAR_INT, sequence);
    }

    @Override
    public void handle(Player player) {
        final Instance instance = player.getInstance();
        if (instance == null) return;

        DiggingResult diggingResult = null;
        switch (status) {
            case STARTED_DIGGING -> {
                if (!instance.isChunkLoaded(blockPosition)) return;
                final Block block = instance.getBlock(blockPosition);
                final GameMode gameMode = player.getGameMode();

                // Prevent spectators and check players in adventure mode
                if (shouldPreventBreaking(player, block)) {
                    diggingResult = new DiggingResult(block, false);
                    break;
                }

                if (gameMode == GameMode.CREATIVE) {
                    diggingResult = breakBlock(instance, player, blockPosition, block, blockFace);
                    break;
                }

                // Survival digging
                // FIXME: verify mineable tag and enchantment
                final boolean instantBreak = player.isInstantBreak() || block.registry().hardness() == 0;
                // Client only send a single STARTED_DIGGING when insta-break is enabled
                if (instantBreak) diggingResult = breakBlock(instance, player, blockPosition, block, blockFace);
                else {
                    PlayerStartDiggingEvent playerStartDiggingEvent = new PlayerStartDiggingEvent(player, block, new BlockVec(blockPosition), blockFace);
                    EventDispatcher.call(playerStartDiggingEvent);
                    diggingResult = new DiggingResult(block, !playerStartDiggingEvent.isCancelled());
                }
            }
            case CANCELLED_DIGGING -> {
                if (!instance.isChunkLoaded(blockPosition)) return;
                final Block block = instance.getBlock(blockPosition);
                EventDispatcher.call(new PlayerCancelDiggingEvent(player, block, new BlockVec(blockPosition)));
                diggingResult = new DiggingResult(block, true);
            }
            case FINISHED_DIGGING -> {
                if (!instance.isChunkLoaded(blockPosition)) return;
                final Block block = instance.getBlock(blockPosition);

                if (shouldPreventBreaking(player, block)) {
                    diggingResult = new DiggingResult(block, false);
                    break;
                }

                PlayerFinishDiggingEvent playerFinishDiggingEvent = new PlayerFinishDiggingEvent(player, block, new BlockVec(blockPosition));
                EventDispatcher.call(playerFinishDiggingEvent);

                diggingResult = breakBlock(instance, player, blockPosition, playerFinishDiggingEvent.getBlock(), blockFace);
            }
            case DROP_ITEM_STACK -> dropItem(player, player.getInventory().getItemInMainHand(), ItemStack.AIR);
            case DROP_ITEM -> {
                final ItemStack handItem = player.getInventory().getItemInMainHand();
                if (handItem.amount() <= 1) dropItem(player, handItem, ItemStack.AIR);// Drop the whole item without copy
                else dropItem(player, // Drop a single item
                            handItem.withAmount(1), // Single dropped item
                            handItem.withAmount(handItem.amount() - 1)); // Updated hand
            }
            case UPDATE_ITEM_STATE -> {
                LivingEntityMeta meta = player.getLivingEntityMeta();
                if (meta == null || !meta.isHandActive()) return;
                Player.Hand hand = meta.getActiveHand();

                ItemUpdateStateEvent itemUpdateStateEvent = player.callItemUpdateStateEvent(hand);

                player.clearItemUse();
                player.triggerStatus((byte) 9);

                final boolean isOffHand = itemUpdateStateEvent.getHand() == Player.Hand.OFF;
                player.refreshActiveHand(itemUpdateStateEvent.hasHandAnimation(),
                        isOffHand, itemUpdateStateEvent.isRiptideSpinAttack());
            }
            case SWAP_ITEM_HAND -> {
                final PlayerInventory inventory = player.getInventory();
                PlayerSwapItemEvent swapItemEvent = new PlayerSwapItemEvent(player, inventory.getItemInOffHand(), inventory.getItemInMainHand());
                EventDispatcher.callCancellable(swapItemEvent, () -> {
                    inventory.setItemInMainHand(swapItemEvent.getMainHandItem());
                    inventory.setItemInOffHand(swapItemEvent.getOffHandItem());
                });
            }
        }
        // Acknowledge start/cancel/finish digging status
        if (diggingResult != null) {
            player.sendPacket(new AcknowledgeBlockChangePacket(sequence));
            if (!diggingResult.success()) {
                // Refresh block on player screen in case it had special data (like a sign)
                var registry = diggingResult.block().registry();
                if (registry.isBlockEntity()) {
                    player.sendPacketToViewersAndSelf(new BlockEntityDataPacket(blockPosition, registry.blockEntityId(), BlockUtils.extractClientNbt(diggingResult.block())));
                }
            }
        }
    }

    private static boolean shouldPreventBreaking(@NotNull Player player, Block block) {
        if (player.getGameMode() == GameMode.SPECTATOR) return true; // Spectators can't break blocks
        else if (player.getGameMode() == GameMode.ADVENTURE) {
            // Check if the item can break the block with the current item
            final ItemStack itemInMainHand = player.getItemInMainHand();
            final BlockPredicates breakPredicate = itemInMainHand.get(ItemComponent.CAN_BREAK, BlockPredicates.NEVER);
            return !breakPredicate.test(block);
        }
        return false;
    }

    private static DiggingResult breakBlock(Instance instance,
                                            Player player,
                                            Point blockPosition, Block previousBlock, BlockFace blockFace) {
        // Unverified block break, client is fully responsible
        final boolean success = instance.breakBlock(player, blockPosition, blockFace);
        final Block updatedBlock = instance.getBlock(blockPosition);
        if (!success) {
            if (previousBlock.isSolid()) {
                final Pos playerPosition = player.getPosition();
                // Teleport the player back if he broke a solid block just below him
                if (playerPosition.sub(0, 1, 0).samePoint(blockPosition)) {
                    player.teleport(playerPosition);
                }
            }
        }
        return new DiggingResult(updatedBlock, success);
    }

    private static void dropItem(@NotNull Player player,
                                 @NotNull ItemStack droppedItem, @NotNull ItemStack handItem) {
        final PlayerInventory playerInventory = player.getInventory();
        if (player.dropItem(droppedItem)) {
            playerInventory.setItemInMainHand(handItem);
        } else {
            playerInventory.update();
        }
    }

    private record DiggingResult(Block block, boolean success) {}

    public enum Status {
        STARTED_DIGGING,
        CANCELLED_DIGGING,
        FINISHED_DIGGING,
        DROP_ITEM_STACK,
        DROP_ITEM,
        UPDATE_ITEM_STATE,
        SWAP_ITEM_HAND
    }
}
