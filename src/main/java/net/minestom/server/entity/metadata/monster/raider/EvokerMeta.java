package net.minestom.server.entity.metadata.monster.raider;

import net.minestom.server.entity.Entity;
import net.minestom.server.entity.MetadataHolder;
import org.jetbrains.annotations.NotNull;

public class EvokerMeta extends SpellcasterIllagerMeta {
    public static final byte OFFSET = SpellcasterIllagerMeta.MAX_OFFSET;
    public static final byte MAX_OFFSET = OFFSET;

    public EvokerMeta(@NotNull Entity entity, @NotNull MetadataHolder metadata) {
        super(entity, metadata);
    }

}
