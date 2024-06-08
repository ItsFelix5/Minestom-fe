package net.minestom.server.adventure.audience;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.key.Keyed;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import java.util.stream.StreamSupport;

/**
 * Utility class to access Adventure audiences.
 */
public class Audiences {
    private static final Audience players = PacketGroupingAudience.of(MinecraftServer.getConnectionManager().getOnlinePlayers());
    private static final AudienceRegistry registry = new AudienceRegistry(new ConcurrentHashMap<>(), CopyOnWriteArrayList::new);

    /**
     * Gets all audience members. This returns {@link #players()} combined with {@link #customs()}.
     *
     * @return all audience members
     */
    public static @NotNull Audience all() {
        return Audience.audience(players, Audience.audience(registry.all()));
    }

    /**
     * Gets all audience members that are of type {@link Player}.
     *
     * @return all players
     */
    public static @NotNull Audience players() {
        return players;
    }

    /**
     * Gets all audience members that are of type {@link Player} and match the predicate.
     *
     * @param filter the predicate
     * @return all players matching the predicate
     */
    public static @NotNull Audience players(@NotNull Predicate<Player> filter) {
        return PacketGroupingAudience.of(MinecraftServer.getConnectionManager().getOnlinePlayers().stream().filter(filter).toList());
    }

    /**
     * Gets all custom audience members.
     *
     * @return all custom audience members
     */
    public static @NotNull Audience customs() {
        return Audience.audience(registry.all());
    }

    /**
     * Gets all custom audience members stored using the given keyed object.
     *
     * @param keyed the keyed object
     * @return all custom audience members stored using the key of the object
     */
    public static @NotNull Audience custom(@NotNull Keyed keyed) {
        return custom(keyed.key());
    }

    /**
     * Gets all custom audience members stored using the given key.
     *
     * @param key the key
     * @return all custom audience members stored using the key
     */
    public static @NotNull Audience custom(@NotNull Key key) {
        return Audience.audience(registry.of(key));
    }

    /**
     * Gets all custom audience members stored using the given keyed object that match
     * the given predicate.
     *
     * @param keyed  the keyed object
     * @param filter the predicate
     * @return all custom audience members stored using the key
     */
    public static @NotNull Audience custom(@NotNull Keyed keyed, Predicate<Audience> filter) {
        return custom(keyed.key(), filter);
    }

    /**
     * Gets all custom audience members stored using the given key that match the
     * given predicate.
     *
     * @param key    the key
     * @param filter the predicate
     * @return all custom audience members stored using the key
     */
    public static @NotNull Audience custom(@NotNull Key key, Predicate<Audience> filter) {
        return Audience.audience(StreamSupport.stream(registry.of(key).spliterator(), false).filter(filter).toList());
    }

    /**
     * Gets all custom audience members matching the given predicate.
     *
     * @param filter the predicate
     * @return all matching custom audience members
     */
    public static @NotNull Audience customs(@NotNull Predicate<Audience> filter) {
        return Audience.audience(registry().of(filter));
    }

    /**
     * Gets all audience members that match the given predicate.
     *
     * @param filter the predicate
     * @return all matching audience members
     */
    public static @NotNull Audience all(@NotNull Predicate<Audience> filter) {
        return Audience.audience(players, Audience.audience(registry().of(filter)));
    }

    /**
     * Gets the audience registry used to register custom audiences.
     *
     * @return the registry
     */
    public static @NotNull AudienceRegistry registry() {
        return registry;
    }
}
