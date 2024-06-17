package net.minestom.server.instance;

import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.minestom.server.ServerFlag;
import net.minestom.server.collision.BoundingBox;
import net.minestom.server.collision.CollisionUtils;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.damage.Damage;
import net.minestom.server.entity.damage.DamageType;
import net.minestom.server.instance.block.Block;
import net.minestom.server.network.packet.server.play.ExplosionPacket;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * Base explosion.
 * Instance can provide a supplier through {@link Instance#setExplosionSupplier}
 */
public class Explosion {
    protected final Map<Player, Vec> playerKnockback = new HashMap<>();
    protected final Point center;
    protected final float strength;
    protected CompoundBinaryTag additionalData;

    public Explosion(Point center, float strength, CompoundBinaryTag additionalData) {
        this.center = center;
        this.strength = strength;
        this.additionalData = additionalData;
    }
    /**
     * Prepares the list of blocks that will be broken. Also pushes and damage entities affected by this explosion
     *
     * @param instance instance to perform this explosion in
     * @return list of blocks that will be broken.
     */
    protected List<Point> prepare(Instance instance) {
        List<Point> blocks = new ArrayList<>();
        ThreadLocalRandom random = ThreadLocalRandom.current();

        boolean breakBlocks = true;
        if (additionalData != null && additionalData.keySet().contains("breakBlocks"))
            breakBlocks = additionalData.getByte("breakBlocks") == (byte) 1;

        if (breakBlocks) {
            for (int x = 0; x < 16; ++x) {
                for (int y = 0; y < 16; ++y) {
                    for (int z = 0; z < 16; ++z) {
                        if (x == 0 || x == 15 || y == 0 || y == 15 || z == 0 || z == 15) {
                            double xLength = (float) x / 15.0F * 2.0F - 1.0F;
                            double yLength = (float) y / 15.0F * 2.0F - 1.0F;
                            double zLength = (float) z / 15.0F * 2.0F - 1.0F;
                            double length = Math.sqrt(xLength * xLength + yLength * yLength + zLength * zLength);
                            xLength /= length;
                            yLength /= length;
                            zLength /= length;
                            Point pos = center;

                            float strengthLeft = strength * (0.7F + random.nextFloat() * 0.6F);
                            for (; strengthLeft > 0.0F; strengthLeft -= 0.225F) {
                                Block block = instance.getBlock(pos);

                                if (!block.isAir()) {
                                    strengthLeft -= ((float) block.registry().explosionResistance() + 0.3F) * 0.3F;

                                    if (strengthLeft > 0.0F) {
                                        Point blockPos = new BlockVec(pos);
                                        if (!blocks.contains(blockPos)) blocks.add(blockPos);
                                    }
                                }

                                pos = pos.add(xLength * 0.30000001192092896D, yLength * 0.30000001192092896D, zLength * 0.30000001192092896D);
                            }
                        }
                    }
                }
            }
        }


        double strength = this.strength * 2.0F;
        int minX = (int) Math.floor(center.x() - strength - 1.0D);
        int maxX = (int) Math.floor(center.x() + strength + 1.0D);
        int minY = (int) Math.floor(center.y() - strength - 1.0D);
        int maxY = (int) Math.floor(center.y() + strength + 1.0D);
        int minZ = (int) Math.floor(center.z() - strength - 1.0D);
        int maxZ = (int) Math.floor(center.z() + strength + 1.0D);

        BoundingBox explosionBox = new BoundingBox(
                Math.max(minX, maxX) - Math.min(minX, maxX),
                Math.max(minY, maxY) - Math.min(minY, maxY),
                Math.max(minZ, maxZ) - Math.min(minZ, maxZ)
        );

        Point src = center.sub(0, explosionBox.height() / 2, 0);

        Set<Entity> entities = instance.getEntities().stream()
                .filter(entity -> explosionBox.intersectEntity(src, entity))
                .collect(Collectors.toSet());

        Damage damageObj;
        if (additionalData != null && additionalData.getBoolean("anchor")) damageObj = new Damage(DamageType.BAD_RESPAWN_POINT, null, null, null, 0);
        else {
            LivingEntity causingEntity = null;
            if(additionalData != null) {
                String uuid = additionalData.getString("causingEntity");
                if(!uuid.isEmpty()) causingEntity = (LivingEntity) instance.getEntities().stream()
                        .filter(entity -> entity instanceof LivingEntity
                                && entity.getUuid().equals(UUID.fromString(uuid)))
                        .findAny().orElse(null);
            }

            damageObj = new Damage(DamageType.PLAYER_EXPLOSION, causingEntity, causingEntity, null, 0);
        }

        for (Entity entity : entities) {
            double knockback = entity.getPosition().distance(center) / strength;
            if (knockback <= 1.0D) {
                double dx = entity.getPosition().x() - center.x();
                double dy = (entity.getEntityType() == EntityType.TNT ? entity.getPosition().y() :
                        entity.getPosition().y() + entity.getEyeHeight()) - center.y();
                double dz = entity.getPosition().z() - center.z();
                double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (distance != 0.0D) {
                    dx /= distance;
                    dy /= distance;
                    dz /= distance;
                    BoundingBox box = entity.getBoundingBox();
                    double xStep = 1 / (box.width() * 2 + 1);
                    double yStep = 1 / (box.height() * 2 + 1);
                    double zStep = 1 / (box.depth() * 2 + 1);
                    double g = (1 - Math.floor(1 / xStep) * xStep) / 2;
                    double h = (1 - Math.floor(1 / zStep) * zStep) / 2;
                    if (xStep >= 0 && yStep >= 0 && zStep >= 0) {
                        int exposedCount = 0;
                        int rayCount = 0;
                        double dx1 = 0;
                        while (dx1 <= 1) {
                            double dy1 = 0;
                            while (dy1 <= 1) {
                                double dz1 = 0;
                                while (dz1 <= 1) {
                                    double rayX = box.minX() + dx1 * box.width();
                                    double rayY = box.minY() + dy1 * box.height();
                                    double rayZ = box.minZ() + dz1 * box.depth();
                                    Point point = new Vec(rayX + g, rayY, rayZ + h).add(entity.getPosition());
                                    if (CollisionUtils.isLineOfSightReachingShape(instance, null, point, center, new BoundingBox(1, 1, 1))) exposedCount++;
                                    rayCount++;
                                    dz1 += zStep;
                                }
                                dy1 += yStep;
                            }
                            dx1 += xStep;
                        }
                        knockback = (1.0D - knockback) * exposedCount / (double) rayCount;
                    }else knockback = 0;

                    damageObj.setAmount((float) ((knockback * knockback + knockback)
                            / 2.0D * 7.0D * strength + 1.0D));
                    if (entity instanceof LivingEntity living) living.damage(damageObj);

                    Vec knockbackVec = new Vec(
                            dx * knockback,
                            dy * knockback,
                            dz * knockback
                    );

                    int tps = ServerFlag.SERVER_TICKS_PER_SECOND;
                    if (entity instanceof Player player && player.getGameMode().canTakeDamage() && !player.isFlying())
                        playerKnockback.put(player, knockbackVec);
                    entity.setVelocity(entity.getVelocity().add(knockbackVec.mul(tps)));
                }
            }
        }

        return blocks;
    }

    /**
     * Performs the explosion and send the corresponding packet
     *
     * @param instance instance to perform this explosion in
     */
    public void apply(@NotNull Instance instance) {
        List<Point> blocks = prepare(instance);
        byte[] records = new byte[3 * blocks.size()];
        for (int i = 0; i < blocks.size(); i++) {
            final var pos = blocks.get(i);
            // TODO chain reaction?
            instance.setBlock(pos, Block.AIR);
            final byte x = (byte) (pos.x() - center.blockX());
            final byte y = (byte) (pos.y() - center.blockY());
            final byte z = (byte) (pos.z() - center.blockZ());
            records[i * 3] = x;
            records[i * 3 + 1] = y;
            records[i * 3 + 2] = z;
        }

        Chunk chunk = instance.getChunkAt(center.x(), center.z());
        if (chunk != null) {
            for (Player player : chunk.getViewers()) {
                Vec knockbackVec = playerKnockback.getOrDefault(player, Vec.ZERO);
                player.sendPacket(new ExplosionPacket(center.x(), center.y(), center.z(), strength,
                        records, (float) knockbackVec.x(), (float) knockbackVec.y(), (float) knockbackVec.z()));
            }
        }
        playerKnockback.clear();

        if (additionalData != null && additionalData.getBoolean("fire")) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            for (Point point : blocks) {
                if (random.nextInt(3) != 0
                        || !instance.getBlock(point).isAir()
                        || !instance.getBlock(point.sub(0, 1, 0)).isSolid())
                    continue;

                instance.setBlock(point, Block.FIRE);
            }
        }

        postSend(instance, blocks);
    }

    /**
     * Called after removing blocks and preparing the packet, but before sending it.
     *
     * @param instance the instance in which the explosion occurs
     * @param blocks   the block positions returned by prepare
     * @param packet   the explosion packet to sent to the client. Be careful with what you're doing.
     *                 It is initialized with the center and radius of the explosion. The positions in 'blocks' are also
     *                 stored in the packet before this call, but you are free to modify 'records' to modify the blocks sent to the client.
     *                 Just be careful, you might just crash the server or the client. Or you're lucky, both at the same time.
     */
    protected void postExplosion(Instance instance, List<Point> blocks, ExplosionPacket packet) {
    }

    /**
     * Called after sending the explosion packet. Can be used to (re)set blocks that have been destroyed.
     * This is necessary to do after the packet being sent, because the client sets the positions received to air.
     *
     * @param instance the instance in which the explosion occurs
     * @param blocks   the block positions returned by prepare
     */
    protected void postSend(Instance instance, List<Point> blocks) {
    }
}
