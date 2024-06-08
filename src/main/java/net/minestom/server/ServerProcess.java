package net.minestom.server;

import net.minestom.server.advancements.AdvancementManager;
import net.minestom.server.adventure.bossbar.BossBarManager;
import net.minestom.server.command.CommandManager;
import net.minestom.server.entity.damage.DamageType;
import net.minestom.server.entity.metadata.animal.tameable.WolfMeta;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.server.ServerTickMonitorEvent;
import net.minestom.server.exception.ExceptionManager;
import net.minestom.server.gamedata.tags.TagManager;
import net.minestom.server.instance.Chunk;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.BlockManager;
import net.minestom.server.instance.block.banner.BannerPattern;
import net.minestom.server.item.armor.TrimMaterial;
import net.minestom.server.item.armor.TrimPattern;
import net.minestom.server.listener.manager.PacketListenerManager;
import net.minestom.server.message.ChatType;
import net.minestom.server.monitoring.BenchmarkManager;
import net.minestom.server.monitoring.TickMonitor;
import net.minestom.server.network.ConnectionManager;
import net.minestom.server.network.PacketProcessor;
import net.minestom.server.network.socket.Server;
import net.minestom.server.recipe.RecipeManager;
import net.minestom.server.registry.DynamicRegistry;
import net.minestom.server.registry.Registries;
import net.minestom.server.scoreboard.TeamManager;
import net.minestom.server.thread.Acquirable;
import net.minestom.server.thread.ThreadDispatcher;
import net.minestom.server.timer.SchedulerManager;
import net.minestom.server.utils.PacketUtils;
import net.minestom.server.utils.PropertyUtils;
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

    private final DynamicRegistry<ChatType> chatType;
    private final DynamicRegistry<DimensionType> dimensionType;
    private final DynamicRegistry<Biome> biome;
    private final DynamicRegistry<DamageType> damageType;
    private final DynamicRegistry<TrimMaterial> trimMaterial;
    private final DynamicRegistry<TrimPattern> trimPattern;
    private final DynamicRegistry<BannerPattern> bannerPattern;
    private final DynamicRegistry<WolfMeta.Variant> wolfVariant;

    private final ConnectionManager connection;
    private final PacketListenerManager packetListener;
    private final PacketProcessor packetProcessor;
    private final BlockManager block;
    private final CommandManager command;
    private final RecipeManager recipe;
    private final TeamManager team;
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

        this.chatType = ChatType.createDefaultRegistry();
        this.dimensionType = DimensionType.createDefaultRegistry();
        this.biome = Biome.createDefaultRegistry();
        this.damageType = DamageType.createDefaultRegistry();
        this.trimMaterial = TrimMaterial.createDefaultRegistry();
        this.trimPattern = TrimPattern.createDefaultRegistry();
        this.bannerPattern = BannerPattern.createDefaultRegistry();
        this.wolfVariant = WolfMeta.Variant.createDefaultRegistry();

        this.connection = new ConnectionManager();
        this.packetListener = new PacketListenerManager();
        this.packetProcessor = new PacketProcessor(packetListener);
        this.block = new BlockManager();
        this.command = new CommandManager();
        this.recipe = new RecipeManager();
        this.team = new TeamManager();
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

    public @NotNull TeamManager team() {
        return team;
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

    public @NotNull PacketListenerManager packetListener() {
        return packetListener;
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

        LOGGER.info("Starting " + MinecraftServer.getBrandName() + " server.");

        // Init server
        try {
            server.init(socketAddress);
        } catch (IOException e) {
            exception.handleException(e);
            throw new RuntimeException(e);
        }

        // Start server
        server.start();

        LOGGER.info(MinecraftServer.getBrandName() + " server started successfully.");

        // Stop the server on SIGINT
        if (SHUTDOWN_ON_SIGNAL) Runtime.getRuntime().addShutdownHook(new Thread(this::stop));
    }

    public void stop() {
        if (!stopped.compareAndSet(false, true))
            return;
        LOGGER.info("Stopping " + MinecraftServer.getBrandName() + " server.");
        scheduler.shutdown();
        connection.shutdown();
        server.stop();
        LOGGER.info("Shutting down all thread pools.");
        benchmark.disable();
        dispatcher.shutdown();
        LOGGER.info(MinecraftServer.getBrandName() + " server stopped successfully.");
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

            // Server tick (chunks/entities)
            serverTick(msTime);

            scheduler().processTickEnd();

            // Flush all waiting packets
            PacketUtils.flush();

            // Server connection tick
            server().tick();

            // Monitoring
            {
                final double acquisitionTimeMs = Acquirable.resetAcquiringTime() / 1e6D;
                final double tickTimeMs = (System.nanoTime() - nanoTime) / 1e6D;
                final TickMonitor tickMonitor = new TickMonitor(tickTimeMs, acquisitionTimeMs);
                EventDispatcher.call(new ServerTickMonitorEvent(tickMonitor));
            }
        }

        private void serverTick(long tickStart) {
            // Tick all instances
            for (Instance instance : Instance.getInstances()) {
                try {
                    instance.tick(tickStart);
                } catch (Exception e) {
                    exception().handleException(e);
                }
            }
            // Tick all chunks (and entities inside)
            dispatcher().updateAndAwait(tickStart);

            // Clear removed entities & update threads
            final long tickTime = System.currentTimeMillis() - tickStart;
            dispatcher().refreshThreads(tickTime);
        }
    }
}
