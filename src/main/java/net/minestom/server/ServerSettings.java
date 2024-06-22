package net.minestom.server;

import net.minestom.server.instance.Instance;
import net.minestom.server.network.packet.server.common.PluginMessagePacket;
import net.minestom.server.network.packet.server.play.ChangeGameStatePacket;
import net.minestom.server.network.packet.server.play.ServerDifficultyPacket;
import net.minestom.server.utils.PacketUtils;
import net.minestom.server.world.Difficulty;
import org.jetbrains.annotations.NotNull;

public class ServerSettings {
    private static String brandName = "Minestom";
    private static Difficulty difficulty = Difficulty.NORMAL;
    private static boolean hardcore = false;
    private static boolean reducedDebugScreen = false;
    private static boolean respawnScreen = true;
    private static Instance defaultInstance;

    /**
     * Gets the current server brand name.
     *
     * @return the server brand name
     */
    @NotNull
    public static String getBrandName() {
        return brandName;
    }

    /**
     * Changes the server brand name and send the change to all connected players.
     *
     * @param brandName the server brand name
     * @throws NullPointerException if {@code brandName} is null
     */
    public static void setBrandName(@NotNull String brandName) {
        ServerSettings.brandName = brandName;
        PacketUtils.broadcastPlayPacket(PluginMessagePacket.getBrandPacket());
    }

    /**
     * Gets the server difficulty showed in game option.
     *
     * @return the server difficulty
     */
    @NotNull
    public static Difficulty getDifficulty() {
        return difficulty;
    }

    /**
     * Changes the server difficulty and send the appropriate packet to all connected clients.
     *
     * @param difficulty the new server difficulty
     */
    public static void setDifficulty(@NotNull Difficulty difficulty) {
        ServerSettings.difficulty = difficulty;
        PacketUtils.broadcastPlayPacket(new ServerDifficultyPacket(difficulty, true));
    }

    /**
     * Sets if the hardcore health bar should be displayed.
     * This currently does not send an update to the player.
     *
     * @param hardcore if the hardcore health bar should be displayed
     */
    public static void setHardcore(boolean hardcore) {
        ServerSettings.hardcore = hardcore;
    }

    /**
     * Gets if the player has the hardcore health bar.
     *
     * @return true if the player hardcore health bar, false otherwise
     */
    public static boolean isHardcore() {
        return hardcore;
    }

    /**
     * Sets if debug screen info should be reduced and send an update packet.
     *
     * @param reduced should the player have the reduced debug screen
     */
    public static void setReducedDebugScreen(boolean reduced) {
        reducedDebugScreen = reduced;
        MinecraftServer.getConnectionManager().getOnlinePlayers().forEach(p->p.triggerStatus((byte) (reduced ? 22 : 23)));
    }

    /**
     * Gets if the player has the reduced debug screen.
     *
     * @return true if the player has the reduced debug screen, false otherwise
     */
    public static boolean isReducedDebugScreen() {
        return reducedDebugScreen;
    }

    /**
     * Sets if the respawn screen should be displayed.
     *
     * @param respawnScreen if the respawn screen should be displayed
     */
    public static void setRespawnScreenEnabled(boolean respawnScreen) {
        ServerSettings.respawnScreen = respawnScreen;
        PacketUtils.broadcastPlayPacket(new ChangeGameStatePacket(ChangeGameStatePacket.Reason.ENABLE_RESPAWN_SCREEN, respawnScreen ? 0 : 1));
    }

    /**
     * Gets if the respawn screen should be displayed.
     *
     * @return if the player respawn screen is showed
     */
    public static boolean respawnScreenEnabled() {
        return respawnScreen;
    }

    /**
     * Sets the instance players join in.
     *
     * @param instance the new default instance
     */
    public static void setDefaultInstance(Instance instance) {
        defaultInstance = instance;
    }

    /**
     * Gets the instance players join in.
     *
     * @return the default instance
     */
    public static Instance getDefaultInstance() {
        return defaultInstance;
    }
}
