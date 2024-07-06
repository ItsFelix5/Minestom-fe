package net.minestom.server.event.player;

import net.minestom.server.entity.Player;
import net.minestom.server.event.trait.PlayerInstanceEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Called when a new instance is set for a player.
 */
public class PlayerSpawnEvent implements PlayerInstanceEvent {
    private final Player player;
    private final boolean firstSpawn;

    public PlayerSpawnEvent(@NotNull Player player, boolean firstSpawn) {
        this.player = player;
        this.firstSpawn = firstSpawn;
    }

    /**
     * 'true' if the player is spawning for the first time. 'false' if this spawn event was triggered by a dimension teleport
     *
     * @return true if this is the first spawn, false otherwise
     */
    public boolean isFirstSpawn() {
        return firstSpawn;
    }

    @Override
    public @NotNull Player getPlayer() {
        return player;
    }
}
