package net.minestom.server.entity.metadata.minecart;

import net.minestom.server.entity.Entity;
import net.minestom.server.entity.MetadataHolder;
import org.jetbrains.annotations.NotNull;

public class MinecartMeta extends AbstractMinecartMeta {
    public static final byte OFFSET = AbstractMinecartMeta.MAX_OFFSET;
    public static final byte MAX_OFFSET = OFFSET;

    public MinecartMeta(@NotNull Entity entity, @NotNull MetadataHolder metadata) {
        super(entity, metadata);
    }

    @Override
    public int getObjectData() {
        return 0;
    }

}
