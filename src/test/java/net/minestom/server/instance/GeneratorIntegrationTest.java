package net.minestom.server.instance;

import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.minestom.server.instance.block.Block;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

@EnvTest
public class GeneratorIntegrationTest {

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    public void loader(boolean data) {
        var block = data ? Block.STONE.withNbt(CompoundBinaryTag.builder().putString("key", "value").build()) : Block.STONE;
        var instance = new InstanceContainer();
        instance.setGenerator(unit -> unit.modifier().fill(block));
        instance.loadChunk(0, 0).join();
        assertEquals(block, instance.getBlock(0, 0, 0));
        assertEquals(block, instance.getBlock(15, 0, 0));
        assertEquals(block, instance.getBlock(0, 15, 0));
        assertEquals(block, instance.getBlock(0, 0, 15));
    }

    @Test
    public void exceptionCatch(Env env) {
        var instance = new InstanceContainer();

        var ref = new AtomicReference<Throwable>();
        env.process().exception().setExceptionHandler(ref::set);

        var exception = new RuntimeException();
        instance.setGenerator(unit -> {
            unit.modifier().fill(Block.STONE);
            throw exception;
        });
        instance.loadChunk(0, 0).join();

        assertSame(exception, ref.get());
    }

    @Test
    public void fillHeightNegative() {
        var instance = new InstanceContainer();
        instance.setGenerator(unit -> unit.modifier().fillHeight(-64, -60, Block.STONE));
        instance.loadChunk(0, 0).join();
        for (int y = -64; y < -60; y++) {
            assertEquals(Block.STONE, instance.getBlock(0, y, 0), "y=" + y);
        }
        for (int y = -60; y < 100; y++) {
            assertEquals(Block.AIR, instance.getBlock(0, y, 0), "y=" + y);
        }
    }

    @Test
    public void fillHeightSingleSectionFull() {
        var instance = new InstanceContainer();
        instance.setGenerator(unit -> unit.modifier().fillHeight(0, 16, Block.GRASS_BLOCK));
        instance.loadChunk(0, 0).join();
        for (int y = 0; y < 16; y++) {
            assertEquals(Block.GRASS_BLOCK, instance.getBlock(0, y, 0), "y=" + y);
        }
    }

    @Test
    public void fillHeightSingleSection() {
        var instance = new InstanceContainer();
        instance.setGenerator(unit -> unit.modifier().fillHeight(4, 5, Block.GRASS_BLOCK));
        instance.loadChunk(0, 0).join();
        for (int y = 0; y < 5; y++) {
            assertEquals(y == 4 ? Block.GRASS_BLOCK : Block.AIR, instance.getBlock(0, y, 0), "y=" + y);
        }
    }

    @Test
    public void fillHeightOverride() {
        var instance = new InstanceContainer();
        instance.setGenerator(unit -> {
            unit.modifier().fillHeight(0, 39, Block.GRASS_BLOCK);
            unit.modifier().fillHeight(39, 40, Block.STONE);
        });
        instance.loadChunk(0, 0).join();
        for (int y = 0; y < 40; y++) {
            assertEquals(y == 39 ? Block.STONE : Block.GRASS_BLOCK, instance.getBlock(0, y, 0), "y=" + y);
        }
    }
}
