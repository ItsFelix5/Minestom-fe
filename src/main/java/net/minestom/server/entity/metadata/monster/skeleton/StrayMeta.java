package net.minestom.server.entity.metadata.monster.skeleton;

import net.minestom.server.entity.Entity;
import net.minestom.server.entity.MetadataHolder;
import org.jetbrains.annotations.NotNull;

public class StrayMeta extends AbstractSkeletonMeta {
    public static final byte OFFSET = AbstractSkeletonMeta.MAX_OFFSET;
    public static final byte MAX_OFFSET = OFFSET;

    public StrayMeta(@NotNull Entity entity, @NotNull MetadataHolder metadata) {
        super(entity, metadata);
    }

}
