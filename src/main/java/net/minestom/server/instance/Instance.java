package net.minestom.server.instance;

import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import net.kyori.adventure.identity.Identity;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.pointer.Pointers;
import net.minestom.server.MinecraftServer;
import net.minestom.server.ServerFlag;
import net.minestom.server.ServerProcess;
import net.minestom.server.Tickable;
import net.minestom.server.adventure.audience.PacketGroupingAudience;
import net.minestom.server.collision.BoundingBox;
import net.minestom.server.collision.CollisionUtils;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.damage.Damage;
import net.minestom.server.entity.damage.DamageType;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.event.EventFilter;
import net.minestom.server.event.EventHandler;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.instance.InstanceRegisterEvent;
import net.minestom.server.event.instance.InstanceTickEvent;
import net.minestom.server.event.instance.InstanceUnregisterEvent;
import net.minestom.server.event.trait.InstanceEvent;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockFace;
import net.minestom.server.instance.block.BlockHandler;
import net.minestom.server.instance.generator.Generator;
import net.minestom.server.instance.light.Light;
import net.minestom.server.network.packet.server.play.*;
import net.minestom.server.registry.DynamicRegistry;
import net.minestom.server.tag.TagHandler;
import net.minestom.server.tag.Taggable;
import net.minestom.server.thread.ThreadDispatcher;
import net.minestom.server.utils.PacketUtils;
import net.minestom.server.utils.chunk.ChunkCache;
import net.minestom.server.utils.chunk.ChunkSupplier;
import net.minestom.server.utils.chunk.ChunkUtils;
import net.minestom.server.utils.validate.Check;
import net.minestom.server.world.DimensionType;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * Instances are what are called "worlds" in Minecraft, you can add an entity in it using {@link Entity#setInstance(Instance)}.
 * <p>
 * An instance has entities and chunks, each instance contains its own entity list but the
 * chunk implementation has to be defined, see {@link InstanceContainer}.
 * <p>
 * WARNING: when making your own implementation you need to be sure to signal
 * the {@link ThreadDispatcher} of every partition/element changes.
 */
public abstract class Instance implements Block.Getter, Block.Setter,
        Tickable, EventHandler<InstanceEvent>, Taggable, PacketGroupingAudience {
    private static final Set<Instance> instances = new CopyOnWriteArraySet<>();

    /**
     * Gets all the registered instances.
     *
     * @return an unmodifiable {@link Set} containing all the registered instances
     */
    public static @NotNull Set<@NotNull Instance> getInstances() {
        return Collections.unmodifiableSet(instances);
    }

    /**
     * Gets an instance by the given UUID.
     *
     * @param uuid UUID of the instance
     * @return the instance with the given UUID, null if not found
     */
    public static @Nullable Instance getInstance(@NotNull UUID uuid) {
        Optional<Instance> instance = getInstances()
                .stream()
                .filter(someInstance -> someInstance.getUniqueId().equals(uuid))
                .findFirst();
        return instance.orElse(null);
    }

    private boolean registered = true;

    private final DynamicRegistry.Key<DimensionType> dimensionType;
    private final DimensionType cachedDimensionType; // Cached to prevent self-destruction if the registry is changed, and to avoid the lookups.
    private final String dimensionName;

    // World border of the instance
    private WorldBorder worldBorder;
    private double targetBorderDiameter;
    private long remainingWorldBorderTransitionTicks;

    // Tick since the creation of the instance
    private long worldAge;

    // World spawn point
    private Pos worldSpawn = Pos.ZERO;

    // The time of the instance
    private long time;
    private int timeRate = 1;
    private int timeSynchronizationTicks = ServerFlag.SERVER_TICKS_PER_SECOND;

    // Weather of the instance
    private Weather weather = Weather.CLEAR;
    private Weather transitioningWeather = Weather.CLEAR;
    private int remainingRainTransitionTicks;
    private int remainingThunderTransitionTicks;

    // Field for tick events
    private long lastTickAge = System.currentTimeMillis();

    private final EntityTracker entityTracker = new EntityTrackerImpl();

    private final ChunkCache blockRetriever = new ChunkCache(this, null, null);

    // the uuid of this instance
    protected UUID uniqueId;

    // instance custom data
    protected TagHandler tagHandler = TagHandler.newHandler();
    private final EventNode<InstanceEvent> eventNode;

    // Adventure
    private final Pointers pointers;

    /**
     * Creates a new instance.
     *
     * @param dimensionType the {@link DimensionType} of the instance
     */
    public Instance(@NotNull DynamicRegistry.Key<DimensionType> dimensionType) {
        this.uniqueId = UUID.randomUUID();
        this.dimensionType = dimensionType;
        this.cachedDimensionType = MinecraftServer.getDimensionTypeRegistry().get(dimensionType);
        Check.argCondition(cachedDimensionType == null, "The dimension " + dimensionType + " is not registered! Please add it to the registry (`MinecraftServer.getDimensionTypeRegistry().registry(dimensionType)`).");
        this.dimensionName = dimensionType.name();

        this.worldBorder = WorldBorder.DEFAULT_BORDER;
        targetBorderDiameter = this.worldBorder.diameter();

        this.pointers = Pointers.builder()
                .withDynamic(Identity.UUID, this::getUniqueId)
                .build();

        final ServerProcess process = MinecraftServer.process();
        if (process != null) this.eventNode = process.eventHandler().map(this, EventFilter.INSTANCE);
        else this.eventNode = null; // Local nodes require a server process
        instances.add(this);
        InstanceRegisterEvent event = new InstanceRegisterEvent(this);
        EventDispatcher.call(event);
    }

    /**
     * Unregisters the {@link Instance} internally.
     */
    public void unregisterInstance() {
        Check.stateCondition(!registered, "This instance has already been unregistered.");
        long onlinePlayers = getPlayers().stream().filter(Player::isOnline).count();
        Check.stateCondition(onlinePlayers > 0, "You cannot unregister an instance with players inside.");
        InstanceUnregisterEvent event = new InstanceUnregisterEvent(this);
        EventDispatcher.call(event);
        // Unregister
        registered = false;
        instances.remove(this);
    }

    @Override
    public void setBlock(int x, int y, int z, @NotNull Block block) {
        setBlock(x, y, z, block, true);
    }

    public void setBlock(@NotNull Point blockPosition, @NotNull Block block, boolean doBlockUpdates) {
        setBlock(blockPosition.blockX(), blockPosition.blockY(), blockPosition.blockZ(), block, doBlockUpdates);
    }

    public abstract void setBlock(int x, int y, int z, @NotNull Block block, boolean doBlockUpdates);

    @ApiStatus.Internal
    public boolean placeBlock(@NotNull BlockHandler.Placement placement) {
        return placeBlock(placement, true);
    }

    @ApiStatus.Internal
    public abstract boolean placeBlock(@NotNull BlockHandler.Placement placement, boolean doBlockUpdates);

    /**
     * Does call {@link net.minestom.server.event.player.PlayerBlockBreakEvent}
     * and send particle packets
     *
     * @param player        the {@link Player} who break the block
     * @param blockPosition the position of the broken block
     * @return true if the block has been broken, false if it has been cancelled
     */
    @ApiStatus.Internal
    public boolean breakBlock(@NotNull Player player, @NotNull Point blockPosition, @NotNull BlockFace blockFace) {
        return breakBlock(player, blockPosition, blockFace, true);
    }

    /**
     * Does call {@link net.minestom.server.event.player.PlayerBlockBreakEvent}
     * and send particle packets
     *
     * @param player        the {@link Player} who break the block
     * @param blockPosition the position of the broken block
     * @param doBlockUpdates true to do block updates, false otherwise
     * @return true if the block has been broken, false if it has been cancelled
     */
    @ApiStatus.Internal
    public abstract boolean breakBlock(@NotNull Player player, @NotNull Point blockPosition, @NotNull BlockFace blockFace, boolean doBlockUpdates);

    /**
     * Forces the generation of a {@link Chunk}, even if no file and {@link Generator} are defined.
     *
     * @param chunkX the chunk X
     * @param chunkZ the chunk Z
     * @return a {@link CompletableFuture} completed once the chunk has been loaded
     */
    public abstract @NotNull CompletableFuture<@NotNull Chunk> loadChunk(int chunkX, int chunkZ);

    /**
     * Loads the chunk at the given {@link Point} with a callback.
     *
     * @param point the chunk position
     */
    public @NotNull CompletableFuture<@NotNull Chunk> loadChunk(@NotNull Point point) {
        return loadChunk(point.chunkX(), point.chunkZ());
    }

    /**
     * Loads the chunk if the chunk is already loaded or if
     * {@link #hasEnabledAutoChunkLoad()} returns true.
     *
     * @param chunkX the chunk X
     * @param chunkZ the chunk Z
     * @return a {@link CompletableFuture} completed once the chunk has been processed, can be null if not loaded
     */
    public abstract @NotNull CompletableFuture<@Nullable Chunk> loadOptionalChunk(int chunkX, int chunkZ);

    /**
     * Loads a {@link Chunk} (if {@link #hasEnabledAutoChunkLoad()} returns true)
     * at the given {@link Point} with a callback.
     *
     * @param point the chunk position
     * @return a {@link CompletableFuture} completed once the chunk has been processed, null if not loaded
     */
    public @NotNull CompletableFuture<@Nullable Chunk> loadOptionalChunk(@NotNull Point point) {
        return loadOptionalChunk(point.chunkX(), point.chunkZ());
    }

    /**
     * Schedules the removal of a {@link Chunk}, this method does not promise when it will be done.
     * <p>
     * WARNING: during unloading, all entities other than {@link Player} will be removed.
     *
     * @param chunk the chunk to unload
     */
    public abstract void unloadChunk(@NotNull Chunk chunk);

    /**
     * Unloads the chunk at the given position.
     *
     * @param chunkX the chunk X
     * @param chunkZ the chunk Z
     */
    public void unloadChunk(int chunkX, int chunkZ) {
        final Chunk chunk = getChunk(chunkX, chunkZ);
        Check.notNull(chunk, "The chunk at {0}:{1} is already unloaded", chunkX, chunkZ);
        unloadChunk(chunk);
    }

    /**
     * Gets the loaded {@link Chunk} at a position.
     * <p>
     * WARNING: this should only return already-loaded chunk, use {@link #loadChunk(int, int)} or similar to load one instead.
     *
     * @param chunkX the chunk X
     * @param chunkZ the chunk Z
     * @return the chunk at the specified position, null if not loaded
     */
    public abstract @Nullable Chunk getChunk(int chunkX, int chunkZ);

    /**
     * @param chunkX the chunk X
     * @param chunkZ this chunk Z
     * @return true if the chunk is loaded
     */
    public boolean isChunkLoaded(int chunkX, int chunkZ) {
        return getChunk(chunkX, chunkZ) != null;
    }

    /**
     * @param point coordinate of a block or other
     * @return true if the chunk is loaded
     */
    public boolean isChunkLoaded(Point point) {
        return isChunkLoaded(point.chunkX(), point.chunkZ());
    }

    /**
     * Saves the current instance tags.
     * <p>
     * Warning: only the global instance data will be saved, not chunks.
     * You would need to call {@link #saveChunksToStorage()} too.
     *
     * @return the future called once the instance data has been saved
     */
    public abstract @NotNull CompletableFuture<Void> saveInstance();

    /**
     * Saves a {@link Chunk} to permanent storage.
     *
     * @param chunk the {@link Chunk} to save
     * @return future called when the chunk is done saving
     */
    public abstract @NotNull CompletableFuture<Void> saveChunkToStorage(@NotNull Chunk chunk);

    /**
     * Saves multiple chunks to permanent storage.
     *
     * @return future called when the chunks are done saving
     */
    public abstract @NotNull CompletableFuture<Void> saveChunksToStorage();

    public abstract void setChunkSupplier(@NotNull ChunkSupplier chunkSupplier);

    /**
     * Gets the chunk supplier of the instance.
     * @return the chunk supplier of the instance
     */
    public abstract ChunkSupplier getChunkSupplier();

    /**
     * Gets the generator associated with the instance
     *
     * @return the generator if any
     */
    public abstract @Nullable Generator generator();

    /**
     * Changes the generator of the instance
     *
     * @param generator the new generator, or null to disable generation
     */
    public abstract void setGenerator(@Nullable Generator generator);

    /**
     * Gets all the instance's loaded chunks.
     *
     * @return an unmodifiable containing all the instance chunks
     */
    public abstract @NotNull Collection<@NotNull Chunk> getChunks();

    /**
     * When set to true, chunks will load automatically when requested.
     * Otherwise using {@link #loadChunk(int, int)} will be required to even spawn a player
     *
     * @param enable enable the auto chunk load
     */
    public abstract void enableAutoChunkLoad(boolean enable);

    /**
     * Gets if the instance should auto load chunks.
     *
     * @return true if auto chunk load is enabled, false otherwise
     */
    public abstract boolean hasEnabledAutoChunkLoad();

    /**
     * Determines whether a position in the void.
     *
     * @param point the point in the world
     * @return true if the point is inside the void
     */
    public abstract boolean isInVoid(@NotNull Point point);

    /**
     * Gets if the instance has been registered.
     *
     * @return true if the instance has been registered
     */
    public boolean isRegistered() {
        return registered;
    }

    /**
     * Gets the instance {@link DimensionType}.
     *
     * @return the dimension of the instance
     */
    public DynamicRegistry.Key<DimensionType> getDimensionType() {
        return dimensionType;
    }

    @ApiStatus.Internal
    public @NotNull DimensionType getCachedDimensionType() {
        return cachedDimensionType;
    }

    /**
     * Gets the instance dimension name.
     * @return the dimension name of the instance
     */
    public @NotNull String getDimensionName() {
        return dimensionName;
    }

    /**
     * Gets the age of this instance in tick.
     *
     * @return the age of this instance in tick
     */
    public long getWorldAge() {
        return worldAge;
    }

    /**
     * Gets the current time in the instance (sun/moon).
     *
     * @return the time in the instance
     */
    public long getTime() {
        return time;
    }

    /**
     * Changes the current time in the instance, from 0 to 24000.
     * <p>
     * If the time is negative, the vanilla client will not move the sun.
     * <p>
     * 0 = sunrise
     * 6000 = noon
     * 12000 = sunset
     * 18000 = midnight
     * <p>
     * This method is unaffected by {@link #getTimeRate()}
     * <p>
     * It does send the new time to all players in the instance, unaffected by {@link #getTimeSynchronizationTicks()}
     *
     * @param time the new time of the instance
     */
    public void setTime(long time) {
        this.time = time;
        PacketUtils.sendGroupedPacket(getPlayers(), createTimePacket());
    }

    /**
     * Gets the rate of the time passing, it is 1 by default
     *
     * @return the time rate of the instance
     */
    public int getTimeRate() {
        return timeRate;
    }

    /**
     * Changes the time rate of the instance
     * <p>
     * 1 is the default value and can be set to 0 to be completely disabled (constant time)
     *
     * @param timeRate the new time rate of the instance
     * @throws IllegalStateException if {@code timeRate} is lower than 0
     */
    public void setTimeRate(int timeRate) {
        Check.stateCondition(timeRate < 0, "The time rate cannot be lower than 0");
        this.timeRate = timeRate;
    }

    /**
     * Gets the rate at which the client is updated with the current instance time
     *
     * @return the client update rate for time related packet
     */
    public int getTimeSynchronizationTicks() {
        return timeSynchronizationTicks;
    }

    /**
     * Changes the natural client time packet synchronization period, defaults to {@link ServerFlag#SERVER_TICKS_PER_SECOND}.
     * <p>
     * Supplying 0 means that the client will never be synchronized with the current natural instance time
     * (time will still change server-side)
     *
     * @param timeSynchronizationTicks the rate to update time in ticks
     */
    public void setTimeSynchronizationTicks(int timeSynchronizationTicks) {
        Check.stateCondition(timeSynchronizationTicks < 0, "The time Synchronization ticks cannot be lower than 0");
        this.timeSynchronizationTicks = timeSynchronizationTicks;
    }

    /**
     * Creates a {@link TimeUpdatePacket} with the current age and time of this instance
     *
     * @return the {@link TimeUpdatePacket} with this instance data
     */
    @ApiStatus.Internal
    public @NotNull TimeUpdatePacket createTimePacket() {
        long time = this.time;
        if (timeRate == 0) {
            //Negative values stop the sun and moon from moving
            //0 as a long cannot be negative
            time = time == 0 ? -24000L : -Math.abs(time);
        }
        return new TimeUpdatePacket(worldAge, time);
    }

    /**
     * Gets the current state of the instance {@link WorldBorder}.
     *
     * @return the {@link WorldBorder} for the instance of the current tick
     */
    public @NotNull WorldBorder getWorldBorder() {
        return worldBorder;
    }

    /**
     * Set the instance {@link WorldBorder} with a smooth transition.
     *
     * @param worldBorder the desired final state of the world border
     * @param transitionTime the time in seconds this world border's diameter
     *                       will transition for (0 makes this instant)
     *
     */
    public void setWorldBorder(@NotNull WorldBorder worldBorder, double transitionTime) {
        Check.stateCondition(transitionTime < 0, "Transition time cannot be lower than 0");
        long transitionMilliseconds = (long) (transitionTime * 1000);
        sendNewWorldBorderPackets(worldBorder, transitionMilliseconds);

        this.targetBorderDiameter = worldBorder.diameter();
        remainingWorldBorderTransitionTicks = transitionMilliseconds / ServerFlag.SERVER_TICKS_MS;
        if (remainingWorldBorderTransitionTicks == 0) this.worldBorder = worldBorder;
        else this.worldBorder = worldBorder.withDiameter(this.worldBorder.diameter());
    }

    /**
     * Set the instance {@link WorldBorder} with an instant transition.
     * see {@link Instance#setWorldBorder(WorldBorder, double)}.
     */
    public void setWorldBorder(@NotNull WorldBorder worldBorder) {
        setWorldBorder(worldBorder, 0);
    }

    /**
     * Creates the {@link InitializeWorldBorderPacket} sent to players who join this instance.
     */
    public @NotNull InitializeWorldBorderPacket createInitializeWorldBorderPacket() {
        return worldBorder.createInitializePacket(targetBorderDiameter, remainingWorldBorderTransitionTicks * ServerFlag.SERVER_TICKS_MS);
    }

    private void sendNewWorldBorderPackets(@NotNull WorldBorder newBorder, long transitionMilliseconds) {
        // Only send the relevant border packets
        if (this.worldBorder.diameter() != newBorder.diameter()) {
            if (transitionMilliseconds == 0) sendGroupedPacket(newBorder.createSizePacket());
            else sendGroupedPacket(this.worldBorder.createLerpSizePacket(newBorder.diameter(), transitionMilliseconds));
        }
        if (this.worldBorder.centerX() != newBorder.centerX() || this.worldBorder.centerZ() != newBorder.centerZ()) {
            sendGroupedPacket(newBorder.createCenterPacket());
        }
        if (this.worldBorder.warningTime() != newBorder.warningTime()) sendGroupedPacket(newBorder.createWarningDelayPacket());
        if (this.worldBorder.warningDistance() != newBorder.warningDistance()) sendGroupedPacket(newBorder.createWarningReachPacket());
    }

    private @NotNull WorldBorder transitionWorldBorder(long remainingTicks) {
        if (remainingTicks <= 1) return worldBorder.withDiameter(targetBorderDiameter);
        return worldBorder.withDiameter(worldBorder.diameter() + (targetBorderDiameter - worldBorder.diameter()) * (1 / (double)remainingTicks));
    }

    /**
     * Gets the entities in the instance;
     *
     * @return an unmodifiable {@link Set} containing all the entities in the instance
     */
    public @NotNull Set<@NotNull Entity> getEntities() {
        return entityTracker.entities();
    }

    /**
     * Gets an entity based on its id (from {@link Entity#getEntityId()}).
     *
     * @param id the entity id
     * @return the entity having the specified id, null if not found
     */
    public @Nullable Entity getEntityById(int id) {
        return entityTracker.getEntityById(id);
    }

    /**
     * Gets an entity based on its UUID (from {@link Entity#getUuid()}).
     *
     * @param uuid the entity UUID
     * @return the entity having the specified uuid, null if not found
     */
    public @Nullable Entity getEntityByUuid(UUID uuid) {
        return entityTracker.getEntityByUuid(uuid);
    }

    /**
     * Gets a player based on its UUID (from {@link Entity#getUuid()}).
     *
     * @param uuid the player UUID
     * @return the player having the specified uuid, null if not found or not a player
     */
    public @Nullable Player getPlayerByUuid(UUID uuid) {
        Entity entity = entityTracker.getEntityByUuid(uuid);
        if (entity instanceof Player player) {
            return player;
        }
        return null;
    }

    /**
     * Gets the players in the instance;
     *
     * @return an unmodifiable {@link Set} containing all the players in the instance
     */
    @Override
    public @NotNull Set<@NotNull Player> getPlayers() {
        return entityTracker.entities(EntityTracker.Target.PLAYERS);
    }

    /**
     * Gets the entities located in the chunk.
     *
     * @param chunk the chunk to get the entities from
     * @return an unmodifiable {@link Set} containing all the entities in a chunk,
     * if {@code chunk} is unloaded, return an empty {@link HashSet}
     */
    public @NotNull Set<@NotNull Entity> getChunkEntities(Chunk chunk) {
        var chunkEntities = entityTracker.chunkEntities(chunk.toPosition(), EntityTracker.Target.ENTITIES);
        return ObjectArraySet.ofUnchecked(chunkEntities.toArray(Entity[]::new));
    }

    /**
     * Gets nearby entities to the given position.
     *
     * @param point position to look at
     * @param range max range from the given point to collect entities at
     * @return entities that are not further than the specified distance from the transmitted position.
     */
    public @NotNull Collection<Entity> getNearbyEntities(@NotNull Point point, double range) {
        List<Entity> result = new ArrayList<>();
        this.entityTracker.nearbyEntities(point, range, EntityTracker.Target.ENTITIES, result::add);
        return result;
    }

    @Override
    public @Nullable Block getBlock(int x, int y, int z, @NotNull Condition condition) {
        final Block block = blockRetriever.getBlock(x, y, z, condition);
        if (block == null) throw new NullPointerException("Unloaded chunk at " + x + "," + y + "," + z);
        return block;
    }

    /**
     * Sends a {@link BlockActionPacket} for all the viewers of the specific position.
     *
     * @param blockPosition the block position
     * @param actionId      the action id, depends on the block
     * @param actionParam   the action parameter, depends on the block
     * @see <a href="https://wiki.vg/Protocol#Block_Action">BlockActionPacket</a> for the action id &amp; param
     */
    public void sendBlockAction(@NotNull Point blockPosition, byte actionId, byte actionParam) {
        final Block block = getBlock(blockPosition);
        final Chunk chunk = getChunkAt(blockPosition);
        Check.notNull(chunk, "The chunk at {0} is not loaded!", blockPosition);
        chunk.sendPacketToViewers(new BlockActionPacket(blockPosition, actionId, actionParam, block));
    }

    /**
     * Gets the {@link Chunk} at the given block position, null if not loaded.
     *
     * @param x the X position
     * @param z the Z position
     * @return the chunk at the given position, null if not loaded
     */
    public @Nullable Chunk getChunkAt(double x, double z) {
        return getChunk(ChunkUtils.getChunkCoordinate(x), ChunkUtils.getChunkCoordinate(z));
    }

    /**
     * Gets the {@link Chunk} at the given {@link Point}, null if not loaded.
     *
     * @param point the position
     * @return the chunk at the given position, null if not loaded
     */
    public @Nullable Chunk getChunkAt(@NotNull Point point) {
        return getChunk(point.chunkX(), point.chunkZ());
    }

    public EntityTracker getEntityTracker() {
        return entityTracker;
    }

    /**
     * Gets the instance unique id.
     *
     * @return the instance unique id
     */
    public @NotNull UUID getUniqueId() {
        return uniqueId;
    }

    /**
     * Performs a single tick in the instance.
     * <p>
     * Warning: this does not update chunks and entities.
     *
     * @param time the tick time in milliseconds
     */
    @Override
    public void tick(long time) {
        // Time
        {
            this.worldAge++;
            this.time += timeRate;
            // time needs to be sent to players
            if (timeSynchronizationTicks > 0 && this.worldAge % timeSynchronizationTicks == 0) {
                PacketUtils.sendGroupedPacket(getPlayers(), createTimePacket());
            }

        }
        // Weather
        if (remainingRainTransitionTicks > 0 || remainingThunderTransitionTicks > 0) {
            Weather previousWeather = transitioningWeather;
            transitioningWeather = transitionWeather(remainingRainTransitionTicks, remainingThunderTransitionTicks);
            sendWeatherPackets(previousWeather);
            remainingRainTransitionTicks = Math.max(0, remainingRainTransitionTicks - 1);
            remainingThunderTransitionTicks = Math.max(0, remainingThunderTransitionTicks - 1);
        }
        // Tick event
        {
            // Process tick events
            EventDispatcher.call(new InstanceTickEvent(this, time, lastTickAge));
            // Set last tick age
            this.lastTickAge = time;
        }
        // World border
        if (remainingWorldBorderTransitionTicks > 0) {
            worldBorder = transitionWorldBorder(remainingWorldBorderTransitionTicks);
            if (worldBorder.diameter() == targetBorderDiameter) remainingWorldBorderTransitionTicks = 0;
            else remainingWorldBorderTransitionTicks--;
        }
    }

    /**
     * Gets the weather of this instance
     *
     * @return the instance weather
     */
    public @NotNull Weather getWeather() {
        return weather;
    }

    /**
     * Sets the weather on this instance, transitions over time
     *
     * @param weather the new weather
     * @param transitionTicks the ticks to transition to new weather
     */
    public void setWeather(@NotNull Weather weather, int transitionTicks) {
        Check.stateCondition(transitionTicks < 1, "Transition ticks cannot be lower than 0");
        this.weather = weather;
        remainingRainTransitionTicks = transitionTicks;
        remainingThunderTransitionTicks = transitionTicks;
    }

    /**
     * Sets the weather of this instance with a fixed transition
     *
     * @param weather the new weather
     */
    public void setWeather(@NotNull Weather weather) {
        this.weather = weather;
        remainingRainTransitionTicks = (int) Math.max(1, Math.abs((this.weather.rainLevel() - transitioningWeather.rainLevel()) / 0.01));
        remainingThunderTransitionTicks = (int) Math.max(1, Math.abs((this.weather.thunderLevel() - transitioningWeather.thunderLevel()) / 0.01));
    }

    private void sendWeatherPackets(@NotNull Weather previousWeather) {
        boolean toggledRain = (transitioningWeather.isRaining() != previousWeather.isRaining());
        if (toggledRain) sendGroupedPacket(transitioningWeather.createIsRainingPacket());
        if (transitioningWeather.rainLevel() != previousWeather.rainLevel()) sendGroupedPacket(transitioningWeather.createRainLevelPacket());
        if (transitioningWeather.thunderLevel() != previousWeather.thunderLevel()) sendGroupedPacket(transitioningWeather.createThunderLevelPacket());
    }

    private @NotNull Weather transitionWeather(int remainingRainTransitionTicks, int remainingThunderTransitionTicks) {
        Weather target = weather;
        Weather current = transitioningWeather;
        float rainLevel = current.rainLevel() + (target.rainLevel() - current.rainLevel()) * (1 / (float)Math.max(1, remainingRainTransitionTicks));
        float thunderLevel = current.thunderLevel() + (target.thunderLevel() - current.thunderLevel()) * (1 / (float)Math.max(1, remainingThunderTransitionTicks));
        return new Weather(rainLevel, thunderLevel);
    }

    @Override
    public @NotNull TagHandler tagHandler() {
        return tagHandler;
    }

    @Override
    public @NotNull EventNode<InstanceEvent> eventNode() {
        return eventNode;
    }

    /**
     * Creates an explosion at the given position with the given strength.
     *
     * @param center the center of the explosion
     * @param strength the strength of the explosion
     */
    public void explode(Point center, float strength) {
        explode(center, strength, null);
    }

    /**
     * Creates an explosion at the given position with the given strength.
     *
     * @param center   the center of the explosion
     * @param strength       the strength of the explosion
     * @param additionalData data to pass to the explosion supplier
     */
    public void explode(Point center, float strength, @Nullable CompoundBinaryTag additionalData) {
        List<Point> blocks = new ArrayList<>();
        ThreadLocalRandom random = ThreadLocalRandom.current();

        if (additionalData == null || !additionalData.keySet().contains("breakBlocks") || additionalData.getByte("breakBlocks") == (byte) 1)
            for (int x = 0; x < 16; ++x)
                for (int y = 0; y < 16; ++y)
                    for (int z = 0; z < 16; ++z)
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
                                Block block = getBlock(pos);

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



        strength *= 2.0F;
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

        Set<Entity> entities = getEntities().stream()
                .filter(entity -> explosionBox.intersectEntity(src, entity))
                .collect(Collectors.toSet());

        Damage damageObj;
        if (additionalData != null && additionalData.getBoolean("anchor")) damageObj = new Damage(DamageType.BAD_RESPAWN_POINT, null, null, null, 0);
        else {
            LivingEntity causingEntity = null;
            if(additionalData != null) {
                String uuid = additionalData.getString("causingEntity");
                if(!uuid.isEmpty()) causingEntity = (LivingEntity) getEntities().stream()
                        .filter(entity -> entity instanceof LivingEntity
                                && entity.getUuid().equals(UUID.fromString(uuid)))
                        .findAny().orElse(null);
            }

            damageObj = new Damage(DamageType.PLAYER_EXPLOSION, causingEntity, causingEntity, null, 0);
        }

        final Map<Player, Vec> playerKnockback = new HashMap<>();
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
                                    if (CollisionUtils.isLineOfSightReachingShape(this, null, point, center, new BoundingBox(1, 1, 1))) exposedCount++;
                                    rayCount++;
                                    dz1 += zStep;
                                }
                                dy1 += yStep;
                            }
                            dx1 += xStep;
                        }
                        knockback = (1.0D - knockback) * exposedCount / (double) rayCount;
                    } else knockback = 0;

                    damageObj.setAmount((float) ((knockback * knockback + knockback)
                            / 2.0D * 7.0D * strength + 1.0D));
                    if (entity instanceof LivingEntity living) living.damage(damageObj);

                    Vec knockbackVec = new Vec(
                            dx * knockback,
                            dy * knockback,
                            dz * knockback
                    );

                    int tps = ServerFlag.SERVER_TICKS_PER_SECOND;
                    if (entity instanceof Player player) {
                        if(!player.getGameMode().canTakeDamage() || player.isFlying()) continue;
                        playerKnockback.put(player, knockbackVec);
                    }
                    entity.setVelocity(entity.getVelocity().add(knockbackVec.mul(tps)));
                }
            }
        }

        byte[] records = new byte[3 * blocks.size()];
        for (int i = 0; i < blocks.size(); i++) {
            final var pos = blocks.get(i);
            setBlock(pos, Block.AIR);
            final byte x = (byte) (pos.x() - center.blockX());
            final byte y = (byte) (pos.y() - center.blockY());
            final byte z = (byte) (pos.z() - center.blockZ());
            records[i * 3] = x;
            records[i * 3 + 1] = y;
            records[i * 3 + 2] = z;
        }

        Chunk chunk = getChunkAt(center.x(), center.z());
        if (chunk != null) {
            for (Player player : chunk.getViewers()) {
                Vec knockbackVec = playerKnockback.getOrDefault(player, Vec.ZERO);
                player.sendPacket(new ExplosionPacket(center.x(), center.y(), center.z(), strength,
                        records, (float) knockbackVec.x(), (float) knockbackVec.y(), (float) knockbackVec.z()));
            }
        }

        if (additionalData != null && additionalData.getBoolean("fire")) {
            for (Point point : blocks) {
                if (random.nextInt(3) != 0
                        || !getBlock(point).isAir()
                        || !getBlock(point.sub(0, 1, 0)).isSolid())
                    continue;

                setBlock(point, Block.FIRE);
            }
        }
    }

    @Override
    public @NotNull Pointers pointers() {
        return this.pointers;
    }

    public int getBlockLight(int blockX, int blockY, int blockZ) {
        var chunk = getChunkAt(blockX, blockZ);
        if (chunk == null) return 0;
        Section section = chunk.getSectionAt(blockY);
        Light light = section.blockLight();
        int sectionCoordinate = ChunkUtils.getChunkCoordinate(blockY);

        int coordX = ChunkUtils.toSectionRelativeCoordinate(blockX);
        int coordY = ChunkUtils.toSectionRelativeCoordinate(blockY);
        int coordZ = ChunkUtils.toSectionRelativeCoordinate(blockZ);

        if (light.requiresUpdate()) LightingChunk.relightSection(chunk.getInstance(), chunk.chunkX, sectionCoordinate, chunk.chunkZ);
        return light.getLevel(coordX, coordY, coordZ);
    }

    public int getSkyLight(int blockX, int blockY, int blockZ) {
        var chunk = getChunkAt(blockX, blockZ);
        if (chunk == null) return 0;
        Section section = chunk.getSectionAt(blockY);
        Light light = section.skyLight();
        int sectionCoordinate = ChunkUtils.getChunkCoordinate(blockY);

        int coordX = ChunkUtils.toSectionRelativeCoordinate(blockX);
        int coordY = ChunkUtils.toSectionRelativeCoordinate(blockY);
        int coordZ = ChunkUtils.toSectionRelativeCoordinate(blockZ);

        if (light.requiresUpdate()) LightingChunk.relightSection(chunk.getInstance(), chunk.chunkX, sectionCoordinate, chunk.chunkZ);
        return light.getLevel(coordX, coordY, coordZ);
    }

    /**
     * Updates the spawn position of the instance and sends the SpawnPositionPacket to all players.
     *
     * @param spawnPosition the new spawn position
     */
    public void setWorldSpawnPosition(@NotNull Pos spawnPosition) {
        if (worldSpawn.samePoint(spawnPosition)) return;
        worldSpawn = spawnPosition;
        if (getPlayers().isEmpty()) return;
        PacketUtils.sendGroupedPacket(getPlayers().stream().filter(p->p.getRespawnPoint() == null).toList(), new SpawnPositionPacket(spawnPosition, spawnPosition.yaw()));
    }

    public Pos getWorldSpawn() {
        return worldSpawn;
    }
}