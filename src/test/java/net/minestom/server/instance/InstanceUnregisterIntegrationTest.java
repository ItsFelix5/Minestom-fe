package net.minestom.server.instance;

import net.minestom.server.event.instance.InstanceTickEvent;
import net.minestom.server.event.player.PlayerMoveEvent;
import net.minestom.server.world.DimensionType;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;

import java.lang.ref.WeakReference;

import static net.minestom.testing.TestUtils.waitUntilCleared;

@EnvTest
public class InstanceUnregisterIntegrationTest {
    @Test
    public void instanceGC(Env env) {
        var instance = env.createFlatInstance();
        var ref = new WeakReference<>(instance);
        instance.unregisterInstance();

        //noinspection UnusedAssignment
        instance = null;
        waitUntilCleared(ref);
    }

    @Test
    public void instanceNodeGC() {
        final class Game {
            final Instance instance;

            Game() {
                instance = new InstanceContainer();
                instance.eventNode().addListener(PlayerMoveEvent.class, e -> System.out.println(instance));
            }
        }
        var game = new Game();
        var ref = new WeakReference<>(game);
        game.instance.unregisterInstance();

        //noinspection UnusedAssignment
        game = null;
        waitUntilCleared(ref);
    }

    @Test
    public void chunkGC(Env env) {
        // Ensure that unregistering an instance does release its chunks
        var instance = env.createFlatInstance();
        var chunk = instance.loadChunk(0, 0).join();
        var ref = new WeakReference<>(chunk);
        instance.unloadChunk(chunk);
        instance.unregisterInstance();
        env.tick(); // Required to remove the chunk from the thread dispatcher

        //noinspection UnusedAssignment
        chunk = null;
        waitUntilCleared(ref);
    }

    @Test
    public void testGCWithEventsLambda() {
        var ref = new WeakReference<>(new InstanceContainer(DimensionType.OVERWORLD));

        tmp(ref.get());

        ref.get().tick(0);
        ref.get().unregisterInstance();

        waitUntilCleared(ref);
    }

    private void tmp(InstanceContainer instanceContainer) {
        instanceContainer.eventNode().addListener(InstanceTickEvent.class, (e) -> {
            var uuid = instanceContainer.getUniqueId();
        });
    }
}
