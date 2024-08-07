package net.minestom.server.entity.metadata.monster;

import net.minestom.server.entity.Entity;
import net.minestom.server.entity.Metadata;
import net.minestom.server.entity.MetadataHolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GuardianMeta extends MonsterMeta {
    public static final byte OFFSET = MonsterMeta.MAX_OFFSET;
    public static final byte MAX_OFFSET = OFFSET + 2;

    private Entity target;

    public GuardianMeta(@NotNull Entity entity, @NotNull MetadataHolder metadata) {
        super(entity, metadata);
    }

    public boolean isRetractingSpikes() {
        return super.metadata.getIndex(OFFSET, false);
    }

    public void setRetractingSpikes(boolean retractingSpikes) {
        super.metadata.setIndex(OFFSET, Metadata.Boolean(retractingSpikes));
    }

    public Entity getTarget() {
        return this.target;
    }

    public void setTarget(@Nullable Entity target) {
        this.target = target;
        super.metadata.setIndex(OFFSET + 1, Metadata.VarInt(target == null ? 0 : target.getEntityId()));
    }

}
