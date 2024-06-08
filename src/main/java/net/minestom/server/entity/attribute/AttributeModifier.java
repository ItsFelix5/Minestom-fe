package net.minestom.server.entity.attribute;

import net.minestom.server.network.NetworkBuffer;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Represent an attribute modifier.
 */
public record AttributeModifier(
        @NotNull UUID id,
        @NotNull String name,
        double amount,
        @NotNull AttributeOperation operation
) implements NetworkBuffer.Writer {

    /**
     * Creates a new modifier with a random id.
     *
     * @param name      the name of this modifier
     * @param amount    the value of this modifier
     * @param operation the operation to apply this modifier with
     */
    public AttributeModifier(@NotNull String name, double amount, @NotNull AttributeOperation operation) {
        this(UUID.randomUUID(), name, amount, operation);
    }

    public AttributeModifier(@NotNull NetworkBuffer reader) {
        this(reader.read(NetworkBuffer.UUID), reader.read(NetworkBuffer.STRING),
                reader.read(NetworkBuffer.DOUBLE), reader.readEnum(AttributeOperation.class));
    }

    @Override
    public void write(@NotNull NetworkBuffer writer) {
        writer.write(NetworkBuffer.UUID, id);
        writer.write(NetworkBuffer.STRING, name);
        writer.write(NetworkBuffer.DOUBLE, amount);
        writer.writeEnum(AttributeOperation.class, operation);
    }
}
