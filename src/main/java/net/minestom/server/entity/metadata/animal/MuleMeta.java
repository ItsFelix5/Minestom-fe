package net.minestom.server.entity.metadata.animal;

import net.minestom.server.entity.Entity;
import net.minestom.server.entity.MetadataHolder;
import org.jetbrains.annotations.NotNull;

public class MuleMeta extends ChestedHorseMeta {
    public static final byte OFFSET = ChestedHorseMeta.MAX_OFFSET;
    public static final byte MAX_OFFSET = OFFSET;

    public MuleMeta(@NotNull Entity entity, @NotNull MetadataHolder metadata) {
        super(entity, metadata);
    }

}
