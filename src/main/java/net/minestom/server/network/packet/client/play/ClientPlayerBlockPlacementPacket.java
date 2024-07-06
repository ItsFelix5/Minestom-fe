package net.minestom.server.network.packet.client.play;

import net.minestom.server.MinecraftServer;
import net.minestom.server.collision.CollisionUtils;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.event.player.PlayerBlockInteractEvent;
import net.minestom.server.event.player.PlayerBlockPlaceEvent;
import net.minestom.server.event.player.PlayerUseItemOnBlockEvent;
import net.minestom.server.instance.Chunk;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockFace;
import net.minestom.server.instance.block.BlockHandler;
import net.minestom.server.instance.block.rule.BlockPlacementRule;
import net.minestom.server.inventory.PlayerInventory;
import net.minestom.server.item.ItemComponent;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.item.component.BlockPredicates;
import net.minestom.server.item.component.ItemBlockState;
import net.minestom.server.network.NetworkBuffer;
import net.minestom.server.network.packet.client.ClientPacket;
import net.minestom.server.network.packet.server.play.AcknowledgeBlockChangePacket;
import net.minestom.server.network.packet.server.play.BlockChangePacket;
import net.minestom.server.utils.chunk.ChunkUtils;
import net.minestom.server.utils.validate.Check;
import net.minestom.server.world.DimensionType;
import org.jetbrains.annotations.NotNull;

import static net.minestom.server.network.NetworkBuffer.*;

public record ClientPlayerBlockPlacementPacket(@NotNull Player.Hand hand, @NotNull Point blockPosition,
                                               @NotNull BlockFace blockFace,
                                               float cursorPositionX, float cursorPositionY, float cursorPositionZ,
                                               boolean insideBlock, int sequence) implements ClientPacket {
    public ClientPlayerBlockPlacementPacket(@NotNull NetworkBuffer reader) {
        this(reader.readEnum(Player.Hand.class), reader.read(BLOCK_POSITION),
                reader.readEnum(BlockFace.class),
                reader.read(FLOAT), reader.read(FLOAT), reader.read(FLOAT),
                reader.read(BOOLEAN), reader.read(VAR_INT));
    }

    @Override
    public void write(@NotNull NetworkBuffer writer) {
        writer.writeEnum(Player.Hand.class, hand);
        writer.write(BLOCK_POSITION, blockPosition);
        writer.writeEnum(BlockFace.class, blockFace);
        writer.write(FLOAT, cursorPositionX);
        writer.write(FLOAT, cursorPositionY);
        writer.write(FLOAT, cursorPositionZ);
        writer.write(BOOLEAN, insideBlock);
        writer.write(VAR_INT, sequence);
    }

    @Override
    public void handle(Player player) {
        final PlayerInventory playerInventory = player.getInventory();

        final Instance instance = player.getInstance();
        if (instance == null) return;

        // Prevent outdated/modified client data
        final Chunk interactedChunk = instance.getChunkAt(blockPosition);
        if (!ChunkUtils.isLoaded(interactedChunk)) return; // Client tried to place a block in an unloaded chunk, ignore the request

        final ItemStack usedItem = player.getItemInHand(hand);
        final Block interactedBlock = instance.getBlock(blockPosition);

        final Point cursorPosition = new Vec(cursorPositionX, cursorPositionY, cursorPositionZ);

        // Interact at block
        // FIXME: onUseOnBlock
        PlayerBlockInteractEvent playerBlockInteractEvent = new PlayerBlockInteractEvent(player, hand, interactedBlock, new BlockVec(blockPosition), cursorPosition, blockFace);
        EventDispatcher.call(playerBlockInteractEvent);
        boolean blockUse = playerBlockInteractEvent.isBlockingItemUse();
        if (!playerBlockInteractEvent.isCancelled()) {
            final var handler = interactedBlock.handler();
            if (handler != null) blockUse |= !handler.onInteract(new BlockHandler.Interaction(interactedBlock, instance, blockPosition, cursorPosition, player, hand));
        }
        if (blockUse) {
            // If the usage was blocked then the world is already up-to-date (from the prior handlers),
            // So ack the change with the current world state.
            player.sendPacket(new AcknowledgeBlockChangePacket(sequence));
            return;
        }

        final Material useMaterial = usedItem.material();
        if (!useMaterial.isBlock()) {
            // Player didn't try to place a block but interacted with one
            PlayerUseItemOnBlockEvent event = new PlayerUseItemOnBlockEvent(player, hand, usedItem, blockPosition, cursorPosition, blockFace);
            EventDispatcher.call(event);
            // Ack the block change. This is required to reset the client prediction to the server state.
            player.sendPacket(new AcknowledgeBlockChangePacket(sequence));
            return;
        }

        // Verify if the player can place the block
        boolean canPlaceBlock = true;
        // Check if the player is allowed to place blocks based on their game mode
        if (player.getGameMode() == GameMode.SPECTATOR) {
            canPlaceBlock = false; // Spectators can't place blocks
        } else if (player.getGameMode() == GameMode.ADVENTURE) {
            //Check if the block can be placed on the block
            BlockPredicates placePredicate = usedItem.get(ItemComponent.CAN_PLACE_ON, BlockPredicates.NEVER);
            canPlaceBlock = placePredicate.test(interactedBlock);
        }


        // Get the newly placed block position
        //todo it feels like it should be possible to have better replacement rules than this, feels pretty scuffed.
        Point placementPosition = blockPosition;
        var interactedPlacementRule = MinecraftServer.getBlockManager().getBlockPlacementRule(interactedBlock);
        if (!interactedBlock.isAir() && (interactedPlacementRule == null || !interactedPlacementRule.isSelfReplaceable(
                new BlockPlacementRule.Replacement(interactedBlock, blockFace, cursorPosition, useMaterial)))) {
            // If the block is not replaceable, try to place next to it.
            final int offsetX = blockFace == BlockFace.WEST ? -1 : blockFace == BlockFace.EAST ? 1 : 0;
            final int offsetY = blockFace == BlockFace.BOTTOM ? -1 : blockFace == BlockFace.TOP ? 1 : 0;
            final int offsetZ = blockFace == BlockFace.NORTH ? -1 : blockFace == BlockFace.SOUTH ? 1 : 0;
            placementPosition = blockPosition.add(offsetX, offsetY, offsetZ);

            var placementBlock = instance.getBlock(placementPosition);
            var placementRule = MinecraftServer.getBlockManager().getBlockPlacementRule(placementBlock);
            // If the block is still not replaceable, cancel the placement
            if (!placementBlock.registry().isReplaceable() && !(placementRule != null && placementRule.isSelfReplaceable(
                    new BlockPlacementRule.Replacement(placementBlock, blockFace, cursorPosition, useMaterial)))) canPlaceBlock = false;
        }

        final DimensionType instanceDim = instance.getCachedDimensionType();
        if (placementPosition.y() >= instanceDim.maxY() || placementPosition.y() < instanceDim.minY()) return;

        // Ensure that the final placement position is inside the world border.
        if (!instance.getWorldBorder().inBounds(placementPosition)) canPlaceBlock = false;

        if (!canPlaceBlock) {
            // Send a block change with the real block in the instance to keep the client in sync,
            // using refreshChunk results in the client not being in sync
            // after rapid invalid block placements
            final Block block = instance.getBlock(placementPosition);
            player.sendPacket(new BlockChangePacket(placementPosition, block));
            return;
        }

        final Chunk chunk = instance.getChunkAt(placementPosition);
        Check.stateCondition(!ChunkUtils.isLoaded(chunk),
                "A player tried to place a block in the border of a loaded chunk {0}", placementPosition);
        if (chunk.isReadOnly()) {
            player.getInventory().update();
            chunk.sendChunk(player);
            return;
        }

        final ItemBlockState blockState = usedItem.get(ItemComponent.BLOCK_STATE, ItemBlockState.EMPTY);
        final Block placedBlock = blockState.apply(useMaterial.block());

        Entity collisionEntity = CollisionUtils.canPlaceBlockAt(instance, placementPosition, placedBlock);
        if (collisionEntity != null) {
            // If a player is trying to place a block on themselves, the client will send a block change but will not set the block on the client
            // For this reason, the block doesn't need to be updated for the client

            // Client also doesn't predict placement of blocks on entities, but we need to refresh for cases where bounding boxes on the server don't match the client
            if (collisionEntity != player) {
                player.getInventory().update();
                chunk.sendChunk(player);
            }

            return;
        }

        // BlockPlaceEvent check
        PlayerBlockPlaceEvent playerBlockPlaceEvent = new PlayerBlockPlaceEvent(player, placedBlock, blockFace, new BlockVec(placementPosition), hand);
        playerBlockPlaceEvent.consumeBlock(player.getGameMode() != GameMode.CREATIVE);
        EventDispatcher.call(playerBlockPlaceEvent);
        if (playerBlockPlaceEvent.isCancelled()) {
            player.getInventory().update();
            chunk.sendChunk(player);
            return;
        }

        // Place the block
        Block resultBlock = playerBlockPlaceEvent.getBlock();
        instance.placeBlock(new BlockHandler.PlayerPlacement(resultBlock, instance, placementPosition, player, hand, blockFace,
                cursorPositionX, cursorPositionY, cursorPositionZ), playerBlockPlaceEvent.shouldDoBlockUpdates());
        player.sendPacket(new AcknowledgeBlockChangePacket(sequence));
        // Block consuming
        if (playerBlockPlaceEvent.doesConsumeBlock()) {
            // Consume the block in the player's hand
            final ItemStack newUsedItem = usedItem.consume(1);
            playerInventory.setItemInHand(hand, newUsedItem);
        } else playerInventory.update(); // Prevent invisible item on client
    }
}
