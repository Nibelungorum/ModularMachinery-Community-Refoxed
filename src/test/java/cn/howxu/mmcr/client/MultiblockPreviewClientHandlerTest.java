package cn.howxu.mmcr.client;

import cn.howxu.mmcr.internal.preview.MultiblockPreviewSnapshot;
import cn.howxu.mmcr.client.preview.world.WorldPreviewMeshKey;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix4f;
import org.joml.Vector3fc;
import net.minecraft.world.item.ItemStack;
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
    void replacingPayloadRequestsTheNewLayerWithoutDroppingVisibleEntries() {
        MultiblockPreviewClientHandler.clearForTesting();
        var first = List.of(new MultiblockPreviewSnapshot.Entry(BlockPos.ZERO, Blocks.IRON_BLOCK.defaultBlockState()));
        var replacement = List.of(new MultiblockPreviewSnapshot.Entry(new BlockPos(0, 1, 0), Blocks.GOLD_BLOCK.defaultBlockState()));

        MultiblockPreviewClientHandler.showAtTick(Level.OVERWORLD, BlockPos.ZERO, first, 200, 0L);
        MultiblockPreviewClientHandler.showAtTick(Level.OVERWORLD, BlockPos.ZERO, replacement, 200, 1L);

        assertEquals(1, MultiblockPreviewClientHandler.visibleEntryCountForTesting());
        assertEquals(new WorldPreviewMeshKey(Level.OVERWORLD, BlockPos.ZERO, 1, null),
                MultiblockPreviewClientHandler.worldMeshRequestForTesting().key());
    }

    @Test
    void layerChangeRequestsAReplacementMesh() {
        MultiblockPreviewClientHandler.clearForTesting();
        var entries = List.of(
                new MultiblockPreviewSnapshot.Entry(BlockPos.ZERO, Blocks.IRON_BLOCK.defaultBlockState()),
                new MultiblockPreviewSnapshot.Entry(new BlockPos(0, 1, 0), Blocks.GOLD_BLOCK.defaultBlockState()));

        MultiblockPreviewClientHandler.showAtTick(Level.OVERWORLD, BlockPos.ZERO, entries, 200, 0L);
        MultiblockPreviewClientHandler.showAtTick(Level.OVERWORLD, BlockPos.ZERO, entries, 200, 1L);

        assertEquals(0, MultiblockPreviewClientHandler.worldMeshRequestForTesting().key().selectedLayer());
    }

    @Test
    void cameraCellChangeRequestsAReplacementMesh() {
        MultiblockPreviewClientHandler.clearForTesting();
        MultiblockPreviewClientHandler.showAtTick(Level.OVERWORLD, BlockPos.ZERO,
                List.of(new MultiblockPreviewSnapshot.Entry(BlockPos.ZERO, Blocks.IRON_BLOCK.defaultBlockState())), 200, 0L);

        MultiblockPreviewClientHandler.rebuildVisibleEntriesForTesting(new Vec3(0.5, 0.5, 0.5));
        MultiblockPreviewClientHandler.rebuildVisibleEntriesForTesting(new Vec3(2.5, 0.5, 0.5));

        assertEquals(new BlockPos(2, 0, 0), MultiblockPreviewClientHandler.worldMeshRequestForTesting().key().cameraCell());
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
        org.junit.jupiter.api.Assertions.assertNull(MultiblockPreviewClientHandler.worldMeshRequestForTesting());
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
        org.junit.jupiter.api.Assertions.assertNull(MultiblockPreviewClientHandler.worldMeshRequestForTesting());
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
        var resolutions = new AtomicInteger();

        MultiblockPreviewClientHandler.showAtTick(Level.OVERWORLD, BlockPos.ZERO, entries, 200, 0L);
        MultiblockPreviewClientHandler.showAtTick(Level.OVERWORLD, BlockPos.ZERO, entries, 200, 1L);
        MultiblockPreviewClientHandler.showAtTick(Level.OVERWORLD, BlockPos.ZERO, entries, 200, 2L);
        MultiblockPreviewClientHandler.rebuildVisibleEntriesForTesting(Vec3.ZERO);
        MultiblockPreviewClientHandler.resolveVisibleModelsForTesting(ignored -> {
            resolutions.incrementAndGet();
            return null;
        });

        assertEquals(0, MultiblockPreviewClientHandler.visibleEntryCountForTesting());
        assertEquals(0, resolutions.get());
    }

    @Test
    void cached_preview_resolution_uses_the_complete_model_update_path() {
        MultiblockPreviewClientHandler.clearForTesting();
        var state = Blocks.IRON_BLOCK.defaultBlockState();
        var updates = new AtomicInteger();
        SpecialModelRenderer<Object> specialRenderer = new SpecialModelRenderer<>() {
            @Override
            public void submit(Object argument, PoseStack poseStack, SubmitNodeCollector collector, int light,
                               int overlay, boolean outline, int color) {
            }

            @Override
            public void getExtents(java.util.function.Consumer<Vector3fc> consumer) {
            }

            @Override
            public Object extractArgument(ItemStack stack) {
                return null;
            }
        };
        BlockModel model = (renderState, blockState, context, seed) -> {
            updates.incrementAndGet();
            renderState.setupSpecialModel(specialRenderer, new Matrix4f());
        };

        assertEquals(true, MultiblockPreviewClientHandler.updateRenderStateForTesting(state, model));
        assertEquals(true, MultiblockPreviewClientHandler.updateRenderStateForTesting(state, model));

        assertEquals(2, updates.get(), "Each render state is independently populated through BlockModel.update");
    }

}
