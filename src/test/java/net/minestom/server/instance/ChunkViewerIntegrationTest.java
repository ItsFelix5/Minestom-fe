package net.minestom.server.instance;

import net.minestom.server.ServerFlag;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.network.packet.server.play.ChunkDataPacket;
import net.minestom.server.utils.chunk.ChunkUtils;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@EnvTest
public class ChunkViewerIntegrationTest {

    @Test
    public void basicJoin(Env env) {
        Instance instance = env.createFlatInstance();

        var chunk = instance.loadChunk(0, 0).join();
        assertEquals(0, chunk.getViewers().size());

        var player = env.createPlayer(instance, new Pos(0, 40, 0));
        assertEquals(1, chunk.getViewers().size(), "Instance should have 1 viewer");
        assertEquals(player, chunk.getViewers().iterator().next());
    }

    @Test
    public void renderDistance(Env env) {
        final int count = ChunkUtils.getChunkCount(ServerFlag.CHUNK_VIEW_DISTANCE);
        var instance = env.createFlatInstance();
        var connection = env.createConnection();
        // Check initial load
        {
            var tracker = connection.trackIncoming(ChunkDataPacket.class);
            var player = connection.connect(instance, new Pos(0, 40, 0)).join();
            assertEquals(instance, player.getInstance());
            assertEquals(new Pos(0, 40, 0), player.getPosition());
            assertEquals(count, tracker.collect().size());
        }
        // Check chunk#sendChunk
        {
            var tracker = connection.trackIncoming(ChunkDataPacket.class);
            for (int x = -ServerFlag.CHUNK_VIEW_DISTANCE; x <= ServerFlag.CHUNK_VIEW_DISTANCE; x++) {
                for (int z = -ServerFlag.CHUNK_VIEW_DISTANCE; z <= ServerFlag.CHUNK_VIEW_DISTANCE; z++) {
                    instance.getChunk(x, z).sendChunk();
                }
            }
            assertEquals(count, tracker.collect().size());
        }
    }
}
