package net.minestom.server;

import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import net.minestom.server.advancements.AdvancementManager;
import net.minestom.server.adventure.bossbar.BossBarManager;
import net.minestom.server.command.CommandManager;
import net.minestom.server.entity.damage.DamageType;
import net.minestom.server.entity.metadata.animal.tameable.WolfMeta;
import net.minestom.server.entity.metadata.other.PaintingMeta;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.exception.ExceptionManager;
import net.minestom.server.gamedata.tags.TagManager;
import net.minestom.server.instance.block.BlockManager;
import net.minestom.server.instance.block.banner.BannerPattern;
import net.minestom.server.instance.block.jukebox.JukeboxSong;
import net.minestom.server.item.armor.TrimMaterial;
import net.minestom.server.item.armor.TrimPattern;
import net.minestom.server.item.enchant.*;
import net.minestom.server.listener.manager.PacketListenerManager;
import net.minestom.server.message.ChatType;
import net.minestom.server.monitoring.BenchmarkManager;
import net.minestom.server.network.ConnectionManager;
import net.minestom.server.network.PacketProcessor;
import net.minestom.server.network.socket.Server;
import net.minestom.server.recipe.RecipeManager;
import net.minestom.server.registry.DynamicRegistry;
import net.minestom.server.scoreboard.TeamManager;
import net.minestom.server.thread.TickSchedulerThread;
import net.minestom.server.timer.SchedulerManager;
import net.minestom.server.utils.nbt.BinaryTagSerializer;
import net.minestom.server.world.DimensionType;
import net.minestom.server.world.biome.Biome;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.UnknownNullability;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

/**
 * The main server class used to start the server and retrieve all the managers.
 * <p>
 * The server needs to be initialized with {@link #init()} and started with {@link #start(String, int)}.
 * You should register all of your dimensions, biomes, commands, events, etc... in-between.
 */
public class MinecraftServer {
    public static final ComponentLogger LOGGER = ComponentLogger.logger(MinecraftServer.class);

    public static final String VERSION_NAME = "1.21";
    public static final int PROTOCOL_VERSION = 767;

    private static volatile ServerProcess serverProcess;

    public static void init() {
        try {
            serverProcess = new ServerProcess();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static @UnknownNullability ServerProcess process() {
        return serverProcess;
    }

    public static @NotNull GlobalEventHandler getGlobalEventHandler() {
        return serverProcess.eventHandler();
    }

    public static @NotNull PacketListenerManager getPacketListenerManager() {
        return serverProcess.packetListener();
    }

    public static @NotNull BlockManager getBlockManager() {
        return serverProcess.block();
    }

    public static @NotNull CommandManager getCommandManager() {
        return serverProcess.command();
    }

    public static @NotNull RecipeManager getRecipeManager() {
        return serverProcess.recipe();
    }

    public static @NotNull TeamManager getTeamManager() {
        return serverProcess.team();
    }

    public static @NotNull SchedulerManager getSchedulerManager() {
        return serverProcess.scheduler();
    }

    /**
     * Gets the manager handling server monitoring.
     *
     * @return the benchmark manager
     */
    public static @NotNull BenchmarkManager getBenchmarkManager() {
        return serverProcess.benchmark();
    }

    public static @NotNull ExceptionManager getExceptionManager() {
        return serverProcess.exception();
    }

    public static @NotNull ConnectionManager getConnectionManager() {
        return serverProcess.connection();
    }

    public static @NotNull BossBarManager getBossBarManager() {
        return serverProcess.bossBar();
    }

    public static @NotNull PacketProcessor getPacketProcessor() {
        return serverProcess.packetProcessor();
    }

    public static boolean isStarted() {
        return serverProcess.isAlive();
    }

    public static AdvancementManager getAdvancementManager() {
        return serverProcess.advancement();
    }

    public static TagManager getTagManager() {
        return serverProcess.tag();
    }

    public static @NotNull DynamicRegistry<ChatType> getChatTypeRegistry() {
        return serverProcess.chatType();
    }

    public static @NotNull DynamicRegistry<DimensionType> getDimensionTypeRegistry() {
        return serverProcess.dimensionType();
    }

    public static @NotNull DynamicRegistry<Biome> getBiomeRegistry() {
        return serverProcess.biome();
    }

    public static @NotNull DynamicRegistry<DamageType> getDamageTypeRegistry() {
        return serverProcess.damageType();
    }

    public static @NotNull DynamicRegistry<TrimMaterial> getTrimMaterialRegistry() {
        return serverProcess.trimMaterial();
    }

    public static @NotNull DynamicRegistry<TrimPattern> getTrimPatternRegistry() {
        return serverProcess.trimPattern();
    }

    public static @NotNull DynamicRegistry<BannerPattern> getBannerPatternRegistry() {
        return serverProcess.bannerPattern();
    }

    public static @NotNull DynamicRegistry<WolfMeta.Variant> getWolfVariantRegistry() {
        return serverProcess.wolfVariant();
    }

    public static @NotNull DynamicRegistry<Enchantment> getEnchantmentRegistry() {
        return serverProcess.enchantment();
    }

    public static @NotNull DynamicRegistry<PaintingMeta.Variant> getPaintingVariantRegistry() {
        return serverProcess.paintingVariant();
    }

    public static @NotNull DynamicRegistry<JukeboxSong> getJukeboxSongRegistry() {
        return serverProcess.jukeboxSong();
    }

    public static @NotNull DynamicRegistry<BinaryTagSerializer<? extends LevelBasedValue>> enchantmentLevelBasedValues() {
        return process().enchantmentLevelBasedValues();
    }

    public static @NotNull DynamicRegistry<BinaryTagSerializer<? extends ValueEffect>> enchantmentValueEffects() {
        return process().enchantmentValueEffects();
    }

    public static @NotNull DynamicRegistry<BinaryTagSerializer<? extends EntityEffect>> enchantmentEntityEffects() {
        return process().enchantmentEntityEffects();
    }

    public static @NotNull DynamicRegistry<BinaryTagSerializer<? extends LocationEffect>> enchantmentLocationEffects() {
        return process().enchantmentLocationEffects();
    }

    public static Server getServer() {
        return serverProcess.server();
    }

    /**
     * Starts the server.
     * <p>
     * It should be called after {@link #init()} and probably your own initialization code.
     *
     * @param address the server address
     * @throws IllegalStateException if called before {@link #init()} or if the server is already running
     */
    public static void start(@NotNull SocketAddress address) {
        serverProcess.start(address);
        new TickSchedulerThread(serverProcess).start();
    }

    public static void start(@NotNull String address, int port) {
        start(new InetSocketAddress(address, port));
    }

    public static void start() {
        start("0.0.0.0", 25565);
    }

    /**
     * Stops this server properly (saves if needed, kicking players, etc.)
     */
    public static void stopCleanly() {
        serverProcess.stop();
    }
}
