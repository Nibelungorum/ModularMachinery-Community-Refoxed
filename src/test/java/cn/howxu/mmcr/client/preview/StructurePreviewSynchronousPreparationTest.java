package cn.howxu.mmcr.client.preview;

import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** Real schema acquisition with a no-GPU renderer boundary for single-frame preparation.
 * @author howxu <dev@howxu.cn>
 */
class StructurePreviewSynchronousPreparationTest {
    @BeforeAll static void bootstrap() throws Exception { TestBootstrap.bootstrap(); }

    @Test void coldSchemaIsBuiltSynchronouslyWithoutStartingOrAwaitingTheAsyncJob() {
        AtomicInteger starts = new AtomicInteger();
        var compilation = new StructurePreviewCompilation(starts::incrementAndGet);
        var renderer = new AtomicReference<PreparedRenderer>();
        var machine = machine();
        var panel = new StructurePreviewPanel(machine, 0, compilation, schema -> {
            var created = new PreparedRenderer(schema);
            renderer.set(created);
            return created;
        });
        assertFalse(panel.isReady());
        panel.prepareSynchronously();
        assertTrue(panel.isReady());
        assertEquals(Blocks.IRON_BLOCK.defaultBlockState(), panel.schema().stateAt(BlockPos.ZERO));
        assertEquals(1, renderer.get().prepares);
        assertEquals(0, starts.get());
        panel.render(null, 100, 100, 0, 0, 0, -1000, -1000, 0, 0);
        assertEquals(1, renderer.get().draws);
        assertEquals(0, starts.get());
        panel.close();
        panel.close();
        assertEquals(1, renderer.get().closes);
    }

    @Test void hotSchemaStillPreparesAnIndependentMeshForEachSingleFrameOwner() {
        var machine = machine();
        var warm = new StructurePreviewSchemaFactory().create(machine);
        var compilation = new StructurePreviewCompilation(() -> fail("Hot snapshot must not restart schema work"));
        compilation.complete(warm, null);
        AtomicInteger creates = new AtomicInteger(), preparedCount = new AtomicInteger(), closedCount = new AtomicInteger();
        for (int capture = 0; capture < 2; capture++) {
            try (var panel = new StructurePreviewPanel(machine, 0, compilation, schema -> {
                creates.incrementAndGet();
                assertSame(warm, schema);
                return new PreparedRenderer(schema) {
                    @Override public void prepareSynchronously(PreviewCamera camera) { preparedCount.incrementAndGet(); }
                    @Override public void close() { closedCount.incrementAndGet(); }
                };
            })) {
                panel.prepareSynchronously();
                assertSame(warm, panel.schema());
            }
        }
        assertEquals(2, creates.get());
        assertEquals(2, preparedCount.get());
        assertEquals(2, closedCount.get());
    }

    private static DynamicMachine machine() {
        return new DynamicMachine(ResourceLocation.parse("mmcr:single_frame"), "machine.preview", new BlockArray(
                Map.of(BlockPos.ZERO, new BlockPredicate.OfBlock(Blocks.IRON_BLOCK))));
    }

    private static class PreparedRenderer implements PreviewRenderer {
        private final StructurePreviewSchema schema;
        int prepares, draws, closes;
        PreparedRenderer(StructurePreviewSchema schema) { this.schema = schema; }
        public StructurePreviewSchema schema() { return schema; }
        public void setVisibility(PreviewVisibility visibility) { }
        public void resetCamera() { }
        public void prepareSynchronously(PreviewCamera camera) { prepares++; }
        public void render(PreviewRenderContext context) {
            assertTrue(prepares > 0, "First draw must follow synchronous mesh preparation");
            draws++;
        }
        public Object hitResult() { return null; }
        public void selectHit(Object hitResult) { }
        public void close() { closes++; }
    }
}
