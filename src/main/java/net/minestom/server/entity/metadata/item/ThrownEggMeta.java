package net.minestom.server.entity.metadata.item;

import net.minestom.server.entity.Entity;
import net.minestom.server.entity.MetadataHolder;
import net.minestom.server.item.Material;
import org.jetbrains.annotations.NotNull;

public class ThrownEggMeta extends ItemContainingMeta {
    public static final byte OFFSET = ItemContainingMeta.MAX_OFFSET;
    public static final byte MAX_OFFSET = OFFSET;

    public ThrownEggMeta(@NotNull Entity entity, @NotNull MetadataHolder metadata) {
        super(entity, metadata, Material.EGG);
    }

}
