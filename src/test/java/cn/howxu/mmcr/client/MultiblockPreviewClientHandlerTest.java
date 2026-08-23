package cn.howxu.mmcr.client;

import cn.howxu.mmcr.internal.preview.MultiblockPreviewSnapshot;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MultiblockPreviewClientHandlerTest {
    @BeforeAll
    static void bootstrap() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void show_cycles_full_first_layer_second_layer_full_for_same_controller() {
        MultiblockPreviewClientHandler.clearForTesting();
        var entries = List.of(
                new MultiblockPreviewSnapshot.Entry(new BlockPos(0, 0, 0), Blocks.IRON_BLOCK.defaultBlockState()),
                new MultiblockPreviewSnapshot.Entry(new BlockPos(0, 1, 0), Blocks.GOLD_BLOCK.defaultBlockState()));

        MultiblockPreviewClientHandler.showAtTick(Level.OVERWORLD, BlockPos.ZERO, entries, 200, 0L);
        assertEquals(Integer.MAX_VALUE, MultiblockPreviewClientHandler.selectedLayerForTesting());
        assertEquals(2, MultiblockPreviewClientHandler.visibleEntryCountForTesting());

        MultiblockPreviewClientHandler.showAtTick(Level.OVERWORLD, BlockPos.ZERO, entries, 200, 1L);
        assertEquals(0, MultiblockPreviewClientHandler.selectedLayerForTesting());
        assertEquals(1, MultiblockPreviewClientHandler.visibleEntryCountForTesting());

        MultiblockPreviewClientHandler.showAtTick(Level.OVERWORLD, BlockPos.ZERO, entries, 200, 2L);
        assertEquals(1, MultiblockPreviewClientHandler.selectedLayerForTesting());
        assertEquals(1, MultiblockPreviewClientHandler.visibleEntryCountForTesting());

        MultiblockPreviewClientHandler.showAtTick(Level.OVERWORLD, BlockPos.ZERO, entries, 200, 3L);
        assertEquals(Integer.MAX_VALUE, MultiblockPreviewClientHandler.selectedLayerForTesting());
        assertEquals(2, MultiblockPreviewClientHandler.visibleEntryCountForTesting());
    }

    @Test
    void ghostPreviewDoesNotRenderBlueOutline() {
        assertEquals(false, MultiblockPreviewClientHandler.rendersPreviewOutlineForTesting());
    }

    @Test
    void clearPreviewOnlyClearsMatchingController() {
        MultiblockPreviewClientHandler.clearForTesting();
        var entries = List.of(new MultiblockPreviewSnapshot.Entry(new BlockPos(0, 0, 0), Blocks.IRON_BLOCK.defaultBlockState()));

        MultiblockPreviewClientHandler.showAtTick(Level.OVERWORLD, BlockPos.ZERO, entries, 200, 0L);
        MultiblockPreviewClientHandler.clearPreview(Level.OVERWORLD, new BlockPos(1, 0, 0));
        assertEquals(1, MultiblockPreviewClientHandler.visibleEntryCountForTesting());

        MultiblockPreviewClientHandler.clearPreview(Level.OVERWORLD, BlockPos.ZERO);
        assertEquals(0, MultiblockPreviewClientHandler.visibleEntryCountForTesting());
    }

    @Test
    void visibleEntriesAreCulledByPreviewRadius() {
        MultiblockPreviewClientHandler.clearForTesting();
        var entries = List.of(
                new MultiblockPreviewSnapshot.Entry(BlockPos.ZERO, Blocks.IRON_BLOCK.defaultBlockState()),
                new MultiblockPreviewSnapshot.Entry(new BlockPos(65, 0, 0), Blocks.GOLD_BLOCK.defaultBlockState()));

        MultiblockPreviewClientHandler.showAtTick(Level.OVERWORLD, BlockPos.ZERO, entries, 200, 0L);
        MultiblockPreviewClientHandler.rebuildVisibleEntriesForTesting(Vec3.ZERO);

        assertEquals(1, MultiblockPreviewClientHandler.visibleEntryCountForTesting());
    }

    @Test
    void replacingPreviewClearsResolvedModelCache() {
        MultiblockPreviewClientHandler.clearForTesting();
        var state = Blocks.IRON_BLOCK.defaultBlockState();
        MultiblockPreviewClientHandler.resolveModelForTesting(state);
        MultiblockPreviewClientHandler.resolveModelForTesting(state);
        assertEquals(1, MultiblockPreviewClientHandler.resolvedModelCacheSizeForTesting());

        MultiblockPreviewClientHandler.showAtTick(Level.NETHER, BlockPos.ZERO,
                List.of(new MultiblockPreviewSnapshot.Entry(BlockPos.ZERO, state)), 200, 0L);

        assertEquals(0, MultiblockPreviewClientHandler.resolvedModelCacheSizeForTesting());
    }

    @Test
    void clearRemovesPreviewAndResolvedModels() {
        MultiblockPreviewClientHandler.clearForTesting();
        var state = Blocks.IRON_BLOCK.defaultBlockState();
        MultiblockPreviewClientHandler.showAtTick(Level.OVERWORLD, BlockPos.ZERO,
                List.of(new MultiblockPreviewSnapshot.Entry(BlockPos.ZERO, state)), 200, 0L);
        MultiblockPreviewClientHandler.resolveModelForTesting(state);

        MultiblockPreviewClientHandler.clearForTesting();

        assertEquals(0, MultiblockPreviewClientHandler.visibleEntryCountForTesting());
        assertEquals(0, MultiblockPreviewClientHandler.resolvedModelCacheSizeForTesting());
    }

    @Test
    void expiredPreviewRemovesResolvedModels() {
        MultiblockPreviewClientHandler.clearForTesting();
        var state = Blocks.IRON_BLOCK.defaultBlockState();
        MultiblockPreviewClientHandler.showAtTick(Level.OVERWORLD, BlockPos.ZERO,
                List.of(new MultiblockPreviewSnapshot.Entry(BlockPos.ZERO, state)), 1, 0L);
        MultiblockPreviewClientHandler.resolveModelForTesting(state);

        MultiblockPreviewClientHandler.expireForTesting(1L);

        assertEquals(0, MultiblockPreviewClientHandler.visibleEntryCountForTesting());
        assertEquals(0, MultiblockPreviewClientHandler.resolvedModelCacheSizeForTesting());
    }

    @Test
    void unloadingClientLevelRemovesResolvedModels() {
        MultiblockPreviewClientHandler.clearForTesting();
        var state = Blocks.IRON_BLOCK.defaultBlockState();
        MultiblockPreviewClientHandler.showAtTick(Level.OVERWORLD, BlockPos.ZERO,
                List.of(new MultiblockPreviewSnapshot.Entry(BlockPos.ZERO, state)), 200, 0L);
        MultiblockPreviewClientHandler.resolveModelForTesting(state);

        MultiblockPreviewClientHandler.unloadClientLevelForTesting();

        assertEquals(0, MultiblockPreviewClientHandler.visibleEntryCountForTesting());
        assertEquals(0, MultiblockPreviewClientHandler.resolvedModelCacheSizeForTesting());
    }

    @Test
    void sameBlockStateResolvesThroughProductionCachePathOnlyOnce() {
        MultiblockPreviewClientHandler.clearForTesting();
        var state = Blocks.IRON_BLOCK.defaultBlockState();
        var resolutions = new AtomicInteger();

        MultiblockPreviewClientHandler.resolveModelForTesting(state, ignored -> {
            resolutions.incrementAndGet();
            return null;
        });
        MultiblockPreviewClientHandler.resolveModelForTesting(state, ignored -> {
            resolutions.incrementAndGet();
            return null;
        });

        assertEquals(1, resolutions.get());
        assertEquals(1, MultiblockPreviewClientHandler.resolvedModelCacheSizeForTesting());
    }

    @Test
    void selectedLayerIsFilteredBeforeDistanceCulling() {
        MultiblockPreviewClientHandler.clearForTesting();
        var entries = List.of(
                new MultiblockPreviewSnapshot.Entry(new BlockPos(0, 0, 0), Blocks.IRON_BLOCK.defaultBlockState()),
                new MultiblockPreviewSnapshot.Entry(new BlockPos(65, 1, 0), Blocks.GOLD_BLOCK.defaultBlockState()));

        MultiblockPreviewClientHandler.showAtTick(Level.OVERWORLD, BlockPos.ZERO, entries, 200, 0L);
        MultiblockPreviewClientHandler.showAtTick(Level.OVERWORLD, BlockPos.ZERO, entries, 200, 1L);
        MultiblockPreviewClientHandler.showAtTick(Level.OVERWORLD, BlockPos.ZERO, entries, 200, 2L);
        MultiblockPreviewClientHandler.rebuildVisibleEntriesForTesting(Vec3.ZERO);

        assertEquals(0, MultiblockPreviewClientHandler.visibleEntryCountForTesting());
    }

}
