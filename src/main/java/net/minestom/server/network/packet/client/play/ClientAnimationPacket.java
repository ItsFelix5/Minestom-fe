package net.minestom.server.network.packet.client.play;

import net.minestom.server.entity.Player;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.event.player.PlayerHandAnimationEvent;
import net.minestom.server.item.ItemStack;
import net.minestom.server.network.NetworkBuffer;
import net.minestom.server.network.packet.client.ClientPacket;
import org.jetbrains.annotations.NotNull;

public record ClientAnimationPacket(@NotNull Player.Hand hand) implements ClientPacket {
    public ClientAnimationPacket(@NotNull NetworkBuffer reader) {
        this(reader.readEnum(Player.Hand.class));
    }

    @Override
    public void write(@NotNull NetworkBuffer writer) {
        writer.writeEnum(Player.Hand.class, hand);
    }

    @Override
    public void listener(Player player) {
        //player.getItemInHand(hand).onLeftClick(player, hand);
        PlayerHandAnimationEvent handAnimationEvent = new PlayerHandAnimationEvent(player, hand);
        EventDispatcher.callCancellable(handAnimationEvent, () -> {
            switch (hand) {
                case MAIN -> player.swingMainHand(true);
                case OFF -> player.swingOffHand(true);
            }
        });
    }
}
