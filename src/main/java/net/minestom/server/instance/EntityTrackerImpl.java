package net.minestom.server.instance;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minestom.server.ServerFlag;
import net.minestom.server.Viewable;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.Player;
import net.minestom.server.utils.chunk.ChunkUtils;
import net.minestom.server.utils.validate.Check;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.jetbrains.annotations.UnmodifiableView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import space.vectrix.flare.fastutil.Int2ObjectSyncMap;
import space.vectrix.flare.fastutil.Long2ObjectSyncMap;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;

import static net.minestom.server.instance.Chunk.CHUNK_SIZE_X;
import static net.minestom.server.instance.Chunk.CHUNK_SIZE_Z;
import static net.minestom.server.utils.chunk.ChunkUtils.*;

final class EntityTrackerImpl implements EntityTracker {
    private static final Logger LOGGER = LoggerFactory.getLogger(EntityTrackerImpl.class);

    static final AtomicInteger TARGET_COUNTER = new AtomicInteger();

    // Store all data associated to a Target
    // The array index is the Target enum ordinal
    final TargetEntry<Entity>[] targetEntries = EntityTracker.Target.TARGETS.stream().map((Function<Target<?>, TargetEntry>) TargetEntry::new).toArray(TargetEntry[]::new);

    private final Int2ObjectSyncMap<EntityTrackerEntry> entriesByEntityId = Int2ObjectSyncMap.hashmap();
    private final Map<UUID, EntityTrackerEntry> entriesByEntityUuid = new ConcurrentHashMap<>();

    @Override
    public <T extends Entity> void register(@NotNull Entity entity, @NotNull Point point,
                                            @NotNull Target<T> target, @Nullable Update<T> update) {
        EntityTrackerEntry newEntry = new EntityTrackerEntry(entity, point);

        EntityTrackerEntry prevEntryWithId = entriesByEntityId.putIfAbsent(entity.getEntityId(), newEntry);
        Check.isTrue(prevEntryWithId == null, "There is already an entity registered with id {0}", entity.getEntityId());
        EntityTrackerEntry prevEntryWithUuid = entriesByEntityUuid.putIfAbsent(entity.getUuid(), newEntry);
        Check.isTrue(prevEntryWithUuid == null, "There is already an entity registered with uuid {0}", entity.getUuid());

        final long index = getChunkIndex(point);
        for (TargetEntry<Entity> targetEntry : targetEntries) {
            if (targetEntry.target.type().isInstance(entity)) {
                targetEntry.entities.add(entity);
                targetEntry.addToChunk(index, entity);
            }
        }
        if (update != null) {
            update.referenceUpdate(point, this);
            nearbyEntitiesByChunkRange(point, ServerFlag.ENTITY_VIEW_DISTANCE, target, newEntity -> {
                if (newEntity == entity) return;
                update.add(newEntity);
            });
        }
    }

    @Override
    public <T extends Entity> void unregister(@NotNull Entity entity,
                                              @NotNull Target<T> target, @Nullable Update<T> update) {
        EntityTrackerEntry entry = entriesByEntityId.remove(entity.getEntityId());
        entriesByEntityUuid.remove(entity.getUuid());
        final Point point = entry == null ? null : entry.getLastPosition();
        if (point == null) return;

        final long index = getChunkIndex(point);
        for (TargetEntry<Entity> targetEntry : targetEntries) {
            if (targetEntry.target.type().isInstance(entity)) {
                targetEntry.entities.remove(entity);
                targetEntry.removeFromChunk(index, entity);
            }
        }
        if (update != null) {
            update.referenceUpdate(point, null);
            nearbyEntitiesByChunkRange(point, ServerFlag.ENTITY_VIEW_DISTANCE, target, newEntity -> {
                if (newEntity == entity) return;
                update.remove(newEntity);
            });
        }
    }

    @Override
    public @Nullable Entity getEntityById(int id) {
        EntityTrackerEntry entry = entriesByEntityId.get(id);
        return entry == null ? null : entry.getEntity();
    }

    @Override
    public @Nullable Entity getEntityByUuid(UUID uuid) {
        EntityTrackerEntry entry = entriesByEntityUuid.get(uuid);
        return entry == null ? null : entry.getEntity();
    }

    @Override
    public <T extends Entity> void move(@NotNull Entity entity, @NotNull Point newPoint,
                                        @NotNull Target<T> target, @Nullable Update<T> update) {
        EntityTrackerEntry entry = entriesByEntityId.get(entity.getEntityId());
        if (entry == null) {
            LOGGER.warn("Attempted to move unregistered entity {} in the entity tracker", entity.getEntityId());
            return;
        }
        Point oldPoint = entry.getLastPosition();
        entry.setLastPosition(newPoint);
        if (oldPoint == null || oldPoint.sameChunk(newPoint)) return;
        final long oldIndex = getChunkIndex(oldPoint);
        final long newIndex = getChunkIndex(newPoint);
        for (TargetEntry<Entity> targetEntry : targetEntries) {
            if (targetEntry.target.type().isInstance(entity)) {
                targetEntry.addToChunk(newIndex, entity);
                targetEntry.removeFromChunk(oldIndex, entity);
            }
        }
        if (update != null) {
            difference(oldPoint, newPoint, target, new Update<>() {
                @Override
                public void add(@NotNull T added) {
                    if (entity != added) update.add(added);
                }

                @Override
                public void remove(@NotNull T removed) {
                    if (entity != removed) update.remove(removed);
                }
            });
            update.referenceUpdate(newPoint, this);
        }
    }

    @Override
    public @Unmodifiable <T extends Entity> Collection<T> chunkEntities(int chunkX, int chunkZ, @NotNull Target<T> target) {
        final TargetEntry<Entity> entry = targetEntries[target.ordinal()];
        //noinspection unchecked
        var chunkEntities = (List<T>) entry.chunkEntities(getChunkIndex(chunkX, chunkZ));
        return Collections.unmodifiableList(chunkEntities);
    }

    @Override
    public <T extends Entity> void nearbyEntitiesByChunkRange(@NotNull Point point, int chunkRange, @NotNull Target<T> target, @NotNull Consumer<T> query) {
        final Long2ObjectSyncMap<List<Entity>> entities = targetEntries[target.ordinal()].chunkEntities;
        if (chunkRange == 0) {
            // Single chunk
            final var chunkEntities = (List<T>) entities.get(getChunkIndex(point));
            if (chunkEntities != null && !chunkEntities.isEmpty()) {
                chunkEntities.forEach(query);
            }
        } else {
            // Multiple chunks
            forChunksInRange(point, chunkRange, (chunkX, chunkZ) -> {
                final var chunkEntities = (List<T>) entities.get(getChunkIndex(chunkX, chunkZ));
                if (chunkEntities == null || chunkEntities.isEmpty()) return;
                chunkEntities.forEach(query);
            });
        }
    }

    @Override
    public <T extends Entity> void nearbyEntities(@NotNull Point point, double range, @NotNull Target<T> target, @NotNull Consumer<T> query) {
        final Long2ObjectSyncMap<List<Entity>> entities = targetEntries[target.ordinal()].chunkEntities;
        final int minChunkX = ChunkUtils.getChunkCoordinate(point.x() - range);
        final int minChunkZ = ChunkUtils.getChunkCoordinate(point.z() - range);
        final int maxChunkX = ChunkUtils.getChunkCoordinate(point.x() + range);
        final int maxChunkZ = ChunkUtils.getChunkCoordinate(point.z() + range);
        final double squaredRange = range * range;
        if (minChunkX == maxChunkX && minChunkZ == maxChunkZ) {
            // Single chunk
            final var chunkEntities = (List<T>) entities.get(getChunkIndex(point));
            if (chunkEntities != null && !chunkEntities.isEmpty()) {
                chunkEntities.forEach(entity -> {
                    final Point position = entriesByEntityId.get(entity.getEntityId()).getLastPosition();
                    if (point.distanceSquared(position) <= squaredRange) query.accept(entity);
                });
            }
        } else {
            // Multiple chunks
            final int chunkRange = (int) (range / Chunk.CHUNK_SECTION_SIZE) + 1;
            forChunksInRange(point, chunkRange, (chunkX, chunkZ) -> {
                final var chunkEntities = (List<T>) entities.get(getChunkIndex(chunkX, chunkZ));
                if (chunkEntities == null || chunkEntities.isEmpty()) return;
                chunkEntities.forEach(entity -> {
                    final Point position = entriesByEntityId.get(entity.getEntityId()).getLastPosition();
                    if (point.distanceSquared(position) <= squaredRange) {
                        query.accept(entity);
                    }
                });
            });
        }
    }

    @Override
    public @UnmodifiableView @NotNull <T extends Entity> Set<@NotNull T> entities(@NotNull Target<T> target) {
        //noinspection unchecked
        return (Set<T>) targetEntries[target.ordinal()].entitiesView;
    }

    @Override
    public @NotNull Viewable viewable(int chunkX, int chunkZ) {
        var entry = targetEntries[Target.PLAYERS.ordinal()];
        return entry.viewers.computeIfAbsent(new ChunkViewKey(chunkX, chunkZ), ChunkView::new);
    }

    private static class EntityTrackerEntry {
        private final Entity entity;
        private Point lastPosition;

        private EntityTrackerEntry(Entity entity, @Nullable Point lastPosition) {
            this.entity = entity;
            this.lastPosition = lastPosition;
        }

        public Entity getEntity() {
            return entity;
        }

        @Nullable
        public Point getLastPosition() {
            return lastPosition;
        }

        public void setLastPosition(Point lastPosition) {
            this.lastPosition = lastPosition;
        }
    }

    private <T extends Entity> void difference(Point oldPoint, Point newPoint,
                                               @NotNull Target<T> target, @NotNull Update<T> update) {
        final TargetEntry<Entity> entry = targetEntries[target.ordinal()];
        forDifferingChunksInRange(newPoint.chunkX(), newPoint.chunkZ(), oldPoint.chunkX(), oldPoint.chunkZ(),
                ServerFlag.ENTITY_VIEW_DISTANCE, (chunkX, chunkZ) -> {
                    // Add
                    final List<Entity> entities = entry.chunkEntities.get(getChunkIndex(chunkX, chunkZ));
                    if (entities == null || entities.isEmpty()) return;
                    for (Entity entity : entities) update.add((T) entity);
                }, (chunkX, chunkZ) -> {
                    // Remove
                    final List<Entity> entities = entry.chunkEntities.get(getChunkIndex(chunkX, chunkZ));
                    if (entities == null || entities.isEmpty()) return;
                    for (Entity entity : entities) update.remove((T) entity);
                });
    }

    record ChunkViewKey(int chunkX, int chunkZ) {
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof ChunkViewKey key)) return false;
            return chunkX == key.chunkX && chunkZ == key.chunkZ;
        }
    }

    static final class TargetEntry<T extends Entity> {
        private final EntityTracker.Target<T> target;
        private final Set<T> entities = ConcurrentHashMap.newKeySet(); // Thread-safe since exposed
        private final Set<T> entitiesView = Collections.unmodifiableSet(entities);
        // Chunk index -> entities inside it
        final Long2ObjectSyncMap<List<T>> chunkEntities = Long2ObjectSyncMap.hashmap();
        final Map<ChunkViewKey, ChunkView> viewers = new ConcurrentHashMap<>();

        TargetEntry(Target<T> target) {
            this.target = target;
        }

        List<T> chunkEntities(long index) {
            return chunkEntities.computeIfAbsent(index, i -> (List<T>) new CopyOnWriteArrayList());
        }

        void addToChunk(long index, T entity) {
            chunkEntities(index).add(entity);
        }

        void removeFromChunk(long index, T entity) {
            List<T> entities = chunkEntities.get(index);
            if (entities != null) entities.remove(entity);
        }
    }

    private final class ChunkView implements Viewable {
        private final Point point;
        final Set<Player> set = new SetImpl();
        private int lastReferenceCount;

        private ChunkView(ChunkViewKey key) {
            this.point = new Vec(CHUNK_SIZE_X * key.chunkX, 0, CHUNK_SIZE_Z * key.chunkZ);
        }

        @Override
        public boolean addViewer(@NotNull Player player) {
            throw new UnsupportedOperationException("Chunk does not support manual viewers");
        }

        @Override
        public boolean removeViewer(@NotNull Player player) {
            throw new UnsupportedOperationException("Chunk does not support manual viewers");
        }

        @Override
        public @NotNull Set<@NotNull Player> getViewers() {
            return set;
        }

        private Collection<Player> references() {
            Int2ObjectOpenHashMap<Player> entityMap = new Int2ObjectOpenHashMap<>(lastReferenceCount);
            collectPlayers(EntityTrackerImpl.this, entityMap);
            this.lastReferenceCount = entityMap.size();
            return entityMap.values();
        }

        private void collectPlayers(EntityTracker tracker, Int2ObjectOpenHashMap<Player> map) {
            tracker.nearbyEntitiesByChunkRange(point, ServerFlag.CHUNK_VIEW_DISTANCE,
                    EntityTracker.Target.PLAYERS, (player) -> map.putIfAbsent(player.getEntityId(), player));
        }

        final class SetImpl extends AbstractSet<Player> {
            @Override
            public @NotNull Iterator<Player> iterator() {
                return references().iterator();
            }

            @Override
            public int size() {
                return references().size();
            }

            @Override
            public void forEach(Consumer<? super Player> action) {
                references().forEach(action);
            }
        }
    }
}
