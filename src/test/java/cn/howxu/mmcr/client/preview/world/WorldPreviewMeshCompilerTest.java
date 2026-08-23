package cn.howxu.mmcr.client.preview.world;

import cn.howxu.mmcr.internal.preview.MultiblockPreviewSnapshot;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.client.preview.PreviewLevel;
import cn.howxu.mmcr.client.preview.PreviewVisibility;
import cn.howxu.mmcr.client.preview.StructurePreviewSchema;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies world preview mesh compilation without submitting geometry to the final event.
 *
 * @author howxu <dev@howxu.cn>
 */
class WorldPreviewMeshCompilerTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void compilerGroupsVisibleStatesByChunkSectionLayer() {
        assumeClientModels();
        List<MultiblockPreviewSnapshot.Entry> entries = List.of(
                entry(0, Blocks.IRON_BLOCK),
                entry(1, Blocks.GLASS));

        try (PreviewLevel level = level(entries);
             WorldPreviewMesh mesh = WorldPreviewMeshCompiler.compile(
                level, BlockPos.ZERO, entries, Integer.MAX_VALUE, Vec3.ZERO, new AtomicBoolean())) {
            assertThat(mesh.meshes()).containsKeys(ChunkSectionLayer.SOLID, ChunkSectionLayer.CUTOUT);
        }
    }

    @Test
    void compilerSkipsAirAndNonSelectedLayerEntries() {
        assumeClientModels();
        List<MultiblockPreviewSnapshot.Entry> entries = List.of(
                entry(0, Blocks.IRON_BLOCK),
                entry(1, Blocks.GOLD_BLOCK),
                entry(2, Blocks.AIR));

        try (PreviewLevel level = level(entries);
             WorldPreviewMesh mesh = WorldPreviewMeshCompiler.compile(
                level, BlockPos.ZERO, entries, 1, Vec3.ZERO, new AtomicBoolean())) {
            assertThat(mesh.blockEntityPositions()).isEmpty();
            assertThat(mesh.meshes()).containsOnlyKeys(ChunkSectionLayer.SOLID);
        }
    }

    @Test
    void cancelledCompilationDoesNotReturnUsableMesh() {
        AtomicBoolean cancelled = new AtomicBoolean(true);

        List<MultiblockPreviewSnapshot.Entry> entries = List.of(entry(0, Blocks.IRON_BLOCK));
        try (PreviewLevel level = level(entries)) {
            assertThatThrownBy(() -> WorldPreviewMeshCompiler.compile(
                level, BlockPos.ZERO, entries, Integer.MAX_VALUE, Vec3.ZERO, cancelled))
                .isInstanceOf(WorldPreviewMeshCompiler.CancelledCompilation.class);
        }
    }

    private static MultiblockPreviewSnapshot.Entry entry(int y, net.minecraft.world.level.block.Block block) {
        return new MultiblockPreviewSnapshot.Entry(new BlockPos(0, y, 0), block.defaultBlockState());
    }

    private static void assumeClientModels() {
        Minecraft minecraft = Minecraft.getInstance();
        Assumptions.assumeTrue(minecraft != null && minecraft.getModelManager() != null,
                "client model manager is unavailable in the headless test runtime");
    }

    private static PreviewLevel level(List<MultiblockPreviewSnapshot.Entry> entries) {
        var states = entries.stream().collect(java.util.stream.Collectors.toMap(
                MultiblockPreviewSnapshot.Entry::relativePos, MultiblockPreviewSnapshot.Entry::state));
        return PreviewLevel.create(new StructurePreviewSchema(MMCR.id("world_mesh_test"), states, java.util.Map.of()),
                () -> PreviewVisibility.ALL);
    }
}
