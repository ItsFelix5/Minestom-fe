package net.minestom.server.entity.metadata.item;

import net.minestom.server.entity.Entity;
import net.minestom.server.entity.MetadataHolder;
import net.minestom.server.entity.metadata.ObjectDataProvider;
import net.minestom.server.item.Material;
import org.jetbrains.annotations.NotNull;

public class ItemEntityMeta extends ItemContainingMeta implements ObjectDataProvider {
    public static final byte OFFSET = ItemContainingMeta.MAX_OFFSET;
    public static final byte MAX_OFFSET = OFFSET;

    public ItemEntityMeta(@NotNull Entity entity, @NotNull MetadataHolder metadata) {
        super(entity, metadata, Material.AIR);
    }

    @Override
    public int getObjectData() {
        return 1;
    }

    @Override
    public boolean requiresVelocityPacketAtSpawn() {
        return true;
    }

}
