package net.minestom.server.network.packet.client.play;

import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.suggestion.Suggestion;
import net.minestom.server.entity.Player;
import net.minestom.server.network.NetworkBuffer;
import net.minestom.server.network.packet.client.ClientPacket;
import net.minestom.server.network.packet.server.play.TabCompletePacket;
import org.jetbrains.annotations.NotNull;

import static net.minestom.server.network.NetworkBuffer.STRING;
import static net.minestom.server.network.NetworkBuffer.VAR_INT;

public record ClientTabCompletePacket(int transactionId, @NotNull String text) implements ClientPacket {
    public ClientTabCompletePacket(@NotNull NetworkBuffer reader) {
        this(reader.read(VAR_INT), reader.read(STRING));
    }

    @Override
    public void write(@NotNull NetworkBuffer writer) {
        writer.write(VAR_INT, transactionId);
        writer.write(STRING, text);
    }

    @Override
    public void listener(Player player) {
        String text = this.text;
        if (text.startsWith("/")) text = text.substring(1);
        // Append a placeholder char if the command ends with a space allowing the parser to find suggestion
        // for the next arg without typing the first char of it, this is probably the most hacky solution, but hey
        // it works as intended :)
        if (text.endsWith(" ")) text = text + '\00';
        final Suggestion suggestion = MinecraftServer.getCommandManager().parseCommand(player, text).suggestion(player);
        if (suggestion != null) {
            player.sendPacket(new TabCompletePacket(
                    transactionId,
                    suggestion.getStart(),
                    suggestion.getLength(),
                    suggestion.getEntries().stream()
                            .map(suggestionEntry -> new TabCompletePacket.Match(suggestionEntry.getEntry(), suggestionEntry.getTooltip()))
                            .toList())
            );
        }
    }
}
