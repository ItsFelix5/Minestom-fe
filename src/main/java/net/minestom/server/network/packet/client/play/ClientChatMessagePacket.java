package net.minestom.server.network.packet.client.play;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.event.player.PlayerChatEvent;
import net.minestom.server.message.ChatPosition;
import net.minestom.server.message.Messenger;
import net.minestom.server.network.NetworkBuffer;
import net.minestom.server.network.packet.client.ClientPacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.function.Function;

import static net.minestom.server.network.NetworkBuffer.*;

public record ClientChatMessagePacket(String message, long timestamp,
                                      long salt, byte @Nullable [] signature,
                                      int ackOffset, BitSet ackList) implements ClientPacket {

    public ClientChatMessagePacket(@NotNull NetworkBuffer reader) {
        this(reader.read(STRING), reader.read(LONG),
                reader.read(LONG), reader.readOptional(r -> r.readBytes(256)),
                reader.read(VAR_INT), BitSet.valueOf(reader.readBytes(3)));
    }

    @Override
    public void write(@NotNull NetworkBuffer writer) {
        writer.write(STRING, message);
        writer.write(LONG, timestamp);
        writer.write(LONG, salt);
        writer.writeOptional(BYTE_ARRAY, signature);
        writer.write(VAR_INT, ackOffset);
        writer.write(RAW_BYTES, Arrays.copyOf(ackList.toByteArray(), 3));
    }

    @Override
    public void listener(Player player) {
        if (!Messenger.canReceiveMessage(player)) {
            Messenger.sendRejectionMessage(player);
            return;
        }

        final Collection<Player> players = MinecraftServer.getConnectionManager().getOnlinePlayers();
        PlayerChatEvent playerChatEvent = new PlayerChatEvent(player, players, (e) -> {
            final String username = e.getPlayer().getUsername();
            return Component.translatable("chat.type.text")
                    .arguments(Component.text(username)
                                    .insertion(username)
                                    .clickEvent(ClickEvent.suggestCommand("/msg " + username + " "))
                                    .hoverEvent(e.getPlayer()),
                            Component.text(e.getMessage())
                    );
        }, message);

        // Call the event
        EventDispatcher.callCancellable(playerChatEvent, () -> {
            final Function<PlayerChatEvent, Component> formatFunction = playerChatEvent.getChatFormatFunction();
            Component textObject = formatFunction.apply(playerChatEvent);

            final Collection<Player> recipients = playerChatEvent.getRecipients();
            if (!recipients.isEmpty()) {
                // delegate to the messenger to avoid sending messages we shouldn't be
                Messenger.sendMessage(recipients, textObject, ChatPosition.CHAT, player.getUuid());
            }
        });
    }
}
