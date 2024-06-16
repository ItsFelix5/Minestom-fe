package net.minestom.server.network.packet.client.play;

import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.event.entity.EntityAttackEvent;
import net.minestom.server.event.player.PlayerEntityInteractEvent;
import net.minestom.server.network.NetworkBuffer;
import net.minestom.server.network.packet.client.ClientPacket;
import org.jetbrains.annotations.NotNull;

import static net.minestom.server.network.NetworkBuffer.*;

public record ClientInteractEntityPacket(int targetId, @NotNull Type type, boolean sneaking) implements ClientPacket {
    public ClientInteractEntityPacket(@NotNull NetworkBuffer reader) {
        this(reader.read(VAR_INT), switch (reader.read(VAR_INT)) {
            case 0 -> new Interact(reader);
            case 1 -> new Attack();
            case 2 -> new InteractAt(reader);
            default -> throw new RuntimeException("Unknown action id");
        }, reader.read(BOOLEAN));
    }

    @Override
    public void write(@NotNull NetworkBuffer writer) {
        writer.write(VAR_INT, targetId);
        writer.write(VAR_INT, type.id());
        writer.write(type);
        writer.write(BOOLEAN, sneaking);
    }

    @Override
    public void listener(Player player) {
        final Entity entity = player.getInstance().getEntityById(targetId);
        if (entity == null || !entity.isViewer(player) || player.getDistanceSquared(entity) > 6 * 6)
            return;

        if (type instanceof ClientInteractEntityPacket.Attack) {
            if (entity instanceof LivingEntity && ((LivingEntity) entity).isDead()) // Can't attack dead entities
                return;
            EventDispatcher.call(new EntityAttackEvent(player, entity));
        } else if (type instanceof ClientInteractEntityPacket.InteractAt interactAt) {
            Point interactPosition = new Vec(interactAt.targetX(), interactAt.targetY(), interactAt.targetZ());
            EventDispatcher.call(new PlayerEntityInteractEvent(player, entity, interactAt.hand(), interactPosition));
        }
    }

    public sealed interface Type extends Writer
            permits Interact, Attack, InteractAt {
        int id();
    }

    public record Interact(Player.@NotNull Hand hand) implements Type {
        public Interact(@NotNull NetworkBuffer reader) {
            this(reader.readEnum(Player.Hand.class));
        }

        @Override
        public void write(@NotNull NetworkBuffer writer) {
            writer.writeEnum(Player.Hand.class, hand);
        }

        @Override
        public int id() {
            return 0;
        }
    }

    public record Attack() implements Type {
        @Override
        public void write(@NotNull NetworkBuffer writer) {
            // Empty
        }

        @Override
        public int id() {
            return 1;
        }
    }

    public record InteractAt(float targetX, float targetY, float targetZ,
                             Player.@NotNull Hand hand) implements Type {
        public InteractAt(@NotNull NetworkBuffer reader) {
            this(reader.read(FLOAT), reader.read(FLOAT), reader.read(FLOAT),
                    reader.readEnum(Player.Hand.class));
        }

        @Override
        public void write(@NotNull NetworkBuffer writer) {
            writer.write(FLOAT, targetX);
            writer.write(FLOAT, targetY);
            writer.write(FLOAT, targetZ);
            writer.writeEnum(Player.Hand.class, hand);
        }

        @Override
        public int id() {
            return 2;
        }
    }
}
