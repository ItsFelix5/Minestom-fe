package net.minestom.server;

import net.minestom.server.advancements.AdvancementManager;
import net.minestom.server.adventure.bossbar.BossBarManager;
import net.minestom.server.command.CommandManager;
import net.minestom.server.entity.damage.DamageType;
import net.minestom.server.entity.metadata.animal.tameable.WolfMeta;
import net.minestom.server.entity.metadata.other.PaintingMeta;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.server.ServerTickMonitorEvent;
import net.minestom.server.exception.ExceptionManager;
import net.minestom.server.gamedata.tags.TagManager;
import net.minestom.server.instance.Chunk;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.BlockManager;
import net.minestom.server.instance.block.banner.BannerPattern;
import net.minestom.server.instance.block.jukebox.JukeboxSong;
import net.minestom.server.item.armor.TrimMaterial;
import net.minestom.server.item.armor.TrimPattern;
import net.minestom.server.item.enchant.*;
import net.minestom.server.message.ChatType;
import net.minestom.server.monitoring.BenchmarkManager;
import net.minestom.server.monitoring.TickMonitor;
import net.minestom.server.network.ConnectionManager;
import net.minestom.server.network.PacketProcessor;
import net.minestom.server.network.socket.Server;
import net.minestom.server.recipe.RecipeManager;
import net.minestom.server.registry.DynamicRegistry;
import net.minestom.server.registry.Registries;
import net.minestom.server.thread.Acquirable;
import net.minestom.server.thread.ThreadDispatcher;
import net.minestom.server.timer.SchedulerManager;
import net.minestom.server.utils.PacketUtils;
import net.minestom.server.utils.PropertyUtils;
import net.minestom.server.utils.nbt.BinaryTagSerializer;
import net.minestom.server.world.DimensionType;
import net.minestom.server.world.biome.Biome;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.SocketAddress;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ServerProcess implements Registries {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServerProcess.class);
    private static final Boolean SHUTDOWN_ON_SIGNAL = PropertyUtils.getBoolean("minestom.shutdown-on-signal", true);

    private final ExceptionManager exception;

    private final DynamicRegistry<BinaryTagSerializer<? extends LevelBasedValue>> enchantmentLevelBasedValues;
    private final DynamicRegistry<BinaryTagSerializer<? extends ValueEffect>> enchantmentValueEffects;
    private final DynamicRegistry<BinaryTagSerializer<? extends EntityEffect>> enchantmentEntityEffects;
    private final DynamicRegistry<BinaryTagSerializer<? extends LocationEffect>> enchantmentLocationEffects;

    private final DynamicRegistry<ChatType> chatType;
    private final DynamicRegistry<DimensionType> dimensionType;
    private final DynamicRegistry<Biome> biome;
    private final DynamicRegistry<DamageType> damageType;
    private final DynamicRegistry<TrimMaterial> trimMaterial;
    private final DynamicRegistry<TrimPattern> trimPattern;
    private final DynamicRegistry<BannerPattern> bannerPattern;
    private final DynamicRegistry<WolfMeta.Variant> wolfVariant;
    private final DynamicRegistry<Enchantment> enchantment;
    private final DynamicRegistry<PaintingMeta.Variant> paintingVariant;
    private final DynamicRegistry<JukeboxSong> jukeboxSong;

    private final ConnectionManager connection;
    private final PacketProcessor packetProcessor;
    private final BlockManager block;
    private final CommandManager command;
    private final RecipeManager recipe;
    private final GlobalEventHandler eventHandler;
    private final SchedulerManager scheduler;
    private final BenchmarkManager benchmark;
    private final AdvancementManager advancement;
    private final BossBarManager bossBar;
    private final TagManager tag;

    private final Server server;

    private final ThreadDispatcher<Chunk> dispatcher;
    private final Ticker ticker;

    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean stopped = new AtomicBoolean();

    public ServerProcess() throws IOException {
        this.exception = new ExceptionManager();

        // The order of initialization here is relevant, we must load the enchantment util registries before the vanilla data is loaded.

        this.enchantmentLevelBasedValues = LevelBasedValue.createDefaultRegistry();
        this.enchantmentValueEffects = ValueEffect.createDefaultRegistry();
        this.enchantmentEntityEffects = EntityEffect.createDefaultRegistry();
        this.enchantmentLocationEffects = LocationEffect.createDefaultRegistry();

        this.chatType = ChatType.createDefaultRegistry();
        this.dimensionType = DimensionType.createDefaultRegistry();
        this.biome = Biome.createDefaultRegistry();
        this.damageType = DamageType.createDefaultRegistry();
        this.trimMaterial = TrimMaterial.createDefaultRegistry();
        this.trimPattern = TrimPattern.createDefaultRegistry();
        this.bannerPattern = BannerPattern.createDefaultRegistry();
        this.wolfVariant = WolfMeta.Variant.createDefaultRegistry();

        this.enchantment = Enchantment.createDefaultRegistry(this);
        this.paintingVariant = PaintingMeta.Variant.createDefaultRegistry();
        this.jukeboxSong = JukeboxSong.createDefaultRegistry();

        this.connection = new ConnectionManager();
        this.packetProcessor = new PacketProcessor();
        this.block = new BlockManager();
        this.command = new CommandManager();
        this.recipe = new RecipeManager();
        this.eventHandler = new GlobalEventHandler();
        this.scheduler = new SchedulerManager();
        this.benchmark = new BenchmarkManager();
        this.advancement = new AdvancementManager();
        this.bossBar = new BossBarManager();
        this.tag = new TagManager();

        this.server = new Server(packetProcessor);

        this.dispatcher = ThreadDispatcher.singleThread();
        this.ticker = new Ticker();
    }

    public @NotNull ExceptionManager exception() {
        return exception;
    }

    public @NotNull DynamicRegistry<DamageType> damageType() {
        return damageType;
    }

    public @NotNull DynamicRegistry<TrimMaterial> trimMaterial() {
        return trimMaterial;
    }

    public @NotNull DynamicRegistry<TrimPattern> trimPattern() {
        return trimPattern;
    }

    public @NotNull DynamicRegistry<BannerPattern> bannerPattern() {
        return bannerPattern;
    }

    public @NotNull DynamicRegistry<WolfMeta.Variant> wolfVariant() {
        return wolfVariant;
    }

    @Override
    public @NotNull DynamicRegistry<Enchantment> enchantment() {
        return enchantment;
    }

    @Override
    public @NotNull DynamicRegistry<PaintingMeta.Variant> paintingVariant() {
        return paintingVariant;
    }

    @Override
    public @NotNull DynamicRegistry<JukeboxSong> jukeboxSong() {
        return jukeboxSong;
    }

    @Override
    public @NotNull DynamicRegistry<BinaryTagSerializer<? extends LevelBasedValue>> enchantmentLevelBasedValues() {
        return enchantmentLevelBasedValues;
    }

    @Override
    public @NotNull DynamicRegistry<BinaryTagSerializer<? extends ValueEffect>> enchantmentValueEffects() {
        return enchantmentValueEffects;
    }

    @Override
    public @NotNull DynamicRegistry<BinaryTagSerializer<? extends EntityEffect>> enchantmentEntityEffects() {
        return enchantmentEntityEffects;
    }

    @Override
    public @NotNull DynamicRegistry<BinaryTagSerializer<? extends LocationEffect>> enchantmentLocationEffects() {
        return enchantmentLocationEffects;
    }

    public @NotNull ConnectionManager connection() {
        return connection;
    }

    public @NotNull BlockManager block() {
        return block;
    }

    public @NotNull CommandManager command() {
        return command;
    }

    public @NotNull RecipeManager recipe() {
        return recipe;
    }

    public @NotNull GlobalEventHandler eventHandler() {
        return eventHandler;
    }

    public @NotNull SchedulerManager scheduler() {
        return scheduler;
    }

    public @NotNull BenchmarkManager benchmark() {
        return benchmark;
    }

    public @NotNull AdvancementManager advancement() {
        return advancement;
    }

    public @NotNull BossBarManager bossBar() {
        return bossBar;
    }

    public @NotNull TagManager tag() {
        return tag;
    }

    public @NotNull DynamicRegistry<ChatType> chatType() {
        return chatType;
    }

    public @NotNull DynamicRegistry<DimensionType> dimensionType() {
        return dimensionType;
    }

    public @NotNull DynamicRegistry<Biome> biome() {
        return biome;
    }

    public @NotNull PacketProcessor packetProcessor() {
        return packetProcessor;
    }

    public @NotNull Server server() {
        return server;
    }

    public @NotNull ThreadDispatcher<Chunk> dispatcher() {
        return dispatcher;
    }

    public @NotNull Ticker ticker() {
        return ticker;
    }

    public void start(@NotNull SocketAddress socketAddress) {
        if (!started.compareAndSet(false, true)) {
            throw new IllegalStateException("Server already started");
        }

        LOGGER.info("Starting server...");

        // Init server
        try {
            server.init(socketAddress);
        } catch (IOException e) {
            exception.handleException(e);
            throw new RuntimeException(e);
        }

        // Start server
        server.start();

        LOGGER.info(ServerSettings.getBrandName() + " server started successfully.");

        // Stop the server on SIGINT
        if (SHUTDOWN_ON_SIGNAL) Runtime.getRuntime().addShutdownHook(new Thread(this::stop));
    }

    public void stop() {
        if (!stopped.compareAndSet(false, true))
            return;
        LOGGER.info("Stopping server...");
        scheduler.shutdown();
        connection.shutdown();
        server.stop();
        benchmark.disable();
        dispatcher.shutdown();
        LOGGER.info("Server stopped successfully.");
    }

    public boolean isAlive() {
        return started.get() && !stopped.get();
    }

    public final class Ticker {
        public void tick(long nanoTime) {
            final long msTime = System.currentTimeMillis();

            scheduler().processTick();

            // Connection tick (let waiting clients in, send keep alives, handle configuration players packets)
            connection().tick(msTime);

            // Tick all instances
            for (Instance instance : Instance.getInstances()) {
                try {
                    instance.tick(msTime);
                } catch (Exception e) {
                    exception().handleException(e);
                }
            }
            // Tick all chunks (and entities inside)
            dispatcher().updateAndAwait(msTime);

            // Clear removed entities & update threads
            dispatcher().refreshThreads(System.currentTimeMillis() - msTime);

            scheduler().processTickEnd();

            // Flush all waiting packets
            PacketUtils.flush();

            // Server connection tick
            server().tick();

            // Monitoring
            final double acquisitionTimeMs = Acquirable.resetAcquiringTime() / 1e6D;
            final double tickTimeMs = (System.nanoTime() - nanoTime) / 1e6D;
            final TickMonitor tickMonitor = new TickMonitor(tickTimeMs, acquisitionTimeMs);
            EventDispatcher.call(new ServerTickMonitorEvent(tickMonitor));
        }
    }
}
