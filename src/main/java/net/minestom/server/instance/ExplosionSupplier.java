package net.minestom.server.instance;

import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.minestom.server.coordinate.Point;

@FunctionalInterface
public interface ExplosionSupplier {

    /**
     * Creates a new explosion
     *
     * @param center        center of the explosion
     * @param strength       strength of the explosion
     * @param additionalData data passed via {@link Instance#explode(Point, float, CompoundBinaryTag)} )}. Can be null
     * @return Explosion object representing the algorithm to use
     */
    Explosion createExplosion(Point center, float strength, CompoundBinaryTag additionalData);

}
