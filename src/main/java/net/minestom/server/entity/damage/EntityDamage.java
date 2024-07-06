package net.minestom.server.entity.damage;

import net.minestom.server.entity.Entity;
import org.jetbrains.annotations.NotNull;

/**
 * Represents damage inflicted by an {@link Entity}.
 */
public class EntityDamage extends Damage {

    public EntityDamage(@NotNull Entity source, float amount) {
        super(DamageType.MOB_ATTACK, source, source, null, amount);
    }

    @Override
    public Entity getAttacker() {
        return getSource();
    }
}