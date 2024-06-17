package net.minestom.server.network.packet.client.play;

import net.minestom.server.entity.Player;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.event.player.PlayerStartFlyingWithElytraEvent;
import net.minestom.server.event.player.PlayerStartSneakingEvent;
import net.minestom.server.event.player.PlayerStopSneakingEvent;
import net.minestom.server.event.player.PlayerStopSprintingEvent;
import net.minestom.server.network.NetworkBuffer;
import net.minestom.server.network.packet.client.ClientPacket;
import org.jetbrains.annotations.NotNull;

import static net.minestom.server.network.NetworkBuffer.VAR_INT;

public record ClientEntityActionPacket(int playerId, @NotNull Action action,
                                       int horseJumpBoost) implements ClientPacket {
    public ClientEntityActionPacket(@NotNull NetworkBuffer reader) {
        this(reader.read(VAR_INT), reader.readEnum(Action.class),
                reader.read(VAR_INT));
    }

    @Override
    public void write(@NotNull NetworkBuffer writer) {
        writer.write(VAR_INT, playerId);
        writer.writeEnum(Action.class, action);
        writer.write(VAR_INT, horseJumpBoost);
    }

    @Override
    public void listener(Player player) {
        switch (action) {
            case START_SNEAKING -> {
                if (!player.isSneaking()) {
                    player.setSneaking(true);
                    EventDispatcher.call(new PlayerStartSneakingEvent(player));
                }
            }
            case STOP_SNEAKING -> {
                if (player.isSneaking()) {
                    player.setSneaking(false);
                    EventDispatcher.call(new PlayerStopSneakingEvent(player));
                }
            }
            case START_SPRINTING -> {
                if (!player.isSprinting()) {
                    player.setSprinting(false);
                    EventDispatcher.call(new PlayerStopSprintingEvent(player));
                }
            }
            case STOP_SPRINTING -> {
                if (player.isSprinting()) {
                    player.setSprinting(false);
                    EventDispatcher.call(new PlayerStopSprintingEvent(player));
                }
            }
            case START_FLYING_ELYTRA -> {
                player.setFlyingWithElytra(true);
                EventDispatcher.call(new PlayerStartFlyingWithElytraEvent(player));
            }

            // TODO do remaining actions
        }
    }

    public enum Action {
        START_SNEAKING,
        STOP_SNEAKING,
        LEAVE_BED,
        START_SPRINTING,
        STOP_SPRINTING,
        START_JUMP_HORSE,
        STOP_JUMP_HORSE,
        OPEN_HORSE_INVENTORY,
        START_FLYING_ELYTRA
    }
}
