package net.minestom.server.instance.generator;

import net.minestom.server.coordinate.Point;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.block.Block;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@EnvTest
public class GeneratorForkConsumerIntegrationTest {

    @Test
    public void empty() {
        var instance = new InstanceContainer();
        AtomicReference<Exception> failed = new AtomicReference<>();
        instance.setGenerator(unit -> {
            try {
                unit.fork(setter -> {
                });
            } catch (Exception e) {
                failed.set(e);
            }
        });
        instance.loadChunk(0, 0).join();
        assertNull(failed.get(), "Failed: " + failed.get());
    }

    @Test
    public void local() {
        var instance = new InstanceContainer();
        instance.setGenerator(unit -> unit.fork(setter -> {
            var dynamic = (GeneratorImpl.DynamicFork) setter;
            assertNull(dynamic.minSection);
            assertEquals(0, dynamic.width);
            assertEquals(0, dynamic.height);
            assertEquals(0, dynamic.depth);
            setter.setBlock(unit.absoluteStart(), Block.STONE);
            assertEquals(unit.absoluteStart(), dynamic.minSection);
            assertEquals(1, dynamic.width);
            assertEquals(1, dynamic.height);
            assertEquals(1, dynamic.depth);
        }));
        instance.loadChunk(0, 0).join();
        assertEquals(Block.STONE, instance.getBlock(0, -64, 0));
    }

    @Test
    public void doubleLocal() {
        var instance = new InstanceContainer();
        instance.setGenerator(unit -> unit.fork(setter -> {
            setter.setBlock(unit.absoluteStart(), Block.STONE);
            setter.setBlock(unit.absoluteStart().add(1, 0, 0), Block.STONE);
        }));
        instance.loadChunk(0, 0).join();
        assertEquals(Block.STONE, instance.getBlock(0, -64, 0));
        assertEquals(Block.STONE, instance.getBlock(1, -64, 0));
    }

    @Test
    public void neighborZ() {
        var instance = new InstanceContainer();
        instance.setGenerator(unit -> unit.fork(setter -> {
            var dynamic = (GeneratorImpl.DynamicFork) setter;
            assertNull(dynamic.minSection);
            assertEquals(0, dynamic.width);
            assertEquals(0, dynamic.height);
            assertEquals(0, dynamic.depth);
            setter.setBlock(unit.absoluteStart(), Block.STONE);
            setter.setBlock(unit.absoluteStart().add(0, 0, 16), Block.GRASS_BLOCK);
            assertEquals(unit.absoluteStart(), dynamic.minSection);
            assertEquals(1, dynamic.width);
            assertEquals(1, dynamic.height);
            assertEquals(2, dynamic.depth);
        }));
        instance.loadChunk(0, 0).join();
        instance.setGenerator(null);
        instance.loadChunk(0, 1).join();
        assertEquals(Block.STONE, instance.getBlock(0, -64, 0));
        assertEquals(Block.GRASS_BLOCK, instance.getBlock(0, -64, 16));
    }

    @Test
    public void neighborX() {
        var instance = new InstanceContainer();
        instance.setGenerator(unit -> unit.fork(setter -> {
            var dynamic = (GeneratorImpl.DynamicFork) setter;
            assertNull(dynamic.minSection);
            assertEquals(0, dynamic.width);
            assertEquals(0, dynamic.height);
            assertEquals(0, dynamic.depth);
            setter.setBlock(unit.absoluteStart(), Block.STONE);
            setter.setBlock(unit.absoluteStart().add(16, 0, 0), Block.GRASS_BLOCK);
            assertEquals(unit.absoluteStart(), dynamic.minSection);
            assertEquals(2, dynamic.width);
            assertEquals(1, dynamic.height);
            assertEquals(1, dynamic.depth);
        }));
        instance.loadChunk(0, 0).join();
        instance.setGenerator(null);
        instance.loadChunk(1, 0).join();
        assertEquals(Block.STONE, instance.getBlock(0, -64, 0));
        assertEquals(Block.GRASS_BLOCK, instance.getBlock(16, -64, 0));
    }

    @Test
    public void neighborY() {
        var instance = new InstanceContainer();
        instance.setGenerator(unit -> unit.fork(setter -> {
            var dynamic = (GeneratorImpl.DynamicFork) setter;
            assertNull(dynamic.minSection);
            assertEquals(0, dynamic.width);
            assertEquals(0, dynamic.height);
            assertEquals(0, dynamic.depth);
            setter.setBlock(unit.absoluteStart(), Block.STONE);
            setter.setBlock(unit.absoluteStart().add(0, 16, 0), Block.GRASS_BLOCK);
            assertEquals(unit.absoluteStart(), dynamic.minSection);
            assertEquals(1, dynamic.width);
            assertEquals(2, dynamic.height);
            assertEquals(1, dynamic.depth);
        }));
        instance.loadChunk(0, 0).join();
        assertEquals(Block.STONE, instance.getBlock(0, -64, 0));
        assertEquals(Block.GRASS_BLOCK, instance.getBlock(0, -48, 0));
    }

    @Test
    public void verticalAndHorizontalSectionBorders() {
        var instance = new InstanceContainer();
        Set<Point> points = ConcurrentHashMap.newKeySet();
        instance.setGenerator(unit -> {
            final Point start = unit.absoluteStart().withY(96);
            unit.fork(setter -> {
                var dynamic = (GeneratorImpl.DynamicFork) setter;
                for (int i = 0; i < 16; i++) {
                    setter.setBlock(start.add(i, 0, 0), Block.STONE);
                    setter.setBlock(start.add(-i, 0, 0), Block.STONE);
                    setter.setBlock(start.add(0, i, 0), Block.STONE);
                    setter.setBlock(start.add(0, -i, 0), Block.STONE);

                    points.add(start.add(i, 0, 0));
                    points.add(start.add(-i, 0, 0));
                    points.add(start.add(0, i, 0));
                    points.add(start.add(0, -i, 0));
                }
                assertEquals(2, dynamic.width);
                assertEquals(2, dynamic.height);
                assertEquals(1, dynamic.depth);
            });
        });
        instance.loadChunk(0, 0).join();
        for (Point point : points) {
            if (!instance.isChunkLoaded(point)) continue;
            assertEquals(Block.STONE, instance.getBlock(point), point.toString());
        }
    }
}
