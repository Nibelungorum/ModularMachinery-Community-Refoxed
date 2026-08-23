package cn.howxu.mmcr.client.preview.world;

import cn.howxu.mmcr.internal.preview.MultiblockPreviewSnapshot;
import cn.howxu.mmcr.test.TestBootstrap;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;

import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

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
        List<MultiblockPreviewSnapshot.Entry> entries = List.of(
                entry(0, Blocks.IRON_BLOCK),
                entry(1, Blocks.GLASS));

        var plan = WorldPreviewMeshCompiler.plan(BlockPos.ZERO, entries, Integer.MAX_VALUE,
                state -> state.is(Blocks.GLASS) ? ChunkSectionLayer.CUTOUT : ChunkSectionLayer.SOLID);

        assertThat(plan.entries()).extracting(WorldPreviewMeshCompiler.PlannedEntry::blockLayer)
                .containsExactly(ChunkSectionLayer.SOLID, ChunkSectionLayer.CUTOUT);
    }

    @Test
    void compilerSkipsAirAndNonSelectedLayerEntries() {
        List<MultiblockPreviewSnapshot.Entry> entries = List.of(
                entry(0, Blocks.IRON_BLOCK),
                entry(1, Blocks.GOLD_BLOCK),
                entry(2, Blocks.AIR));

        var plan = WorldPreviewMeshCompiler.plan(BlockPos.ZERO, entries, 1, state -> ChunkSectionLayer.SOLID);

        assertThat(plan.entries()).extracting(WorldPreviewMeshCompiler.PlannedEntry::position)
                .containsExactly(new BlockPos(0, 1, 0));
    }

    @Test
    void compilerFiltersAirEvenWhenAirIsOnTheSelectedLayer() {
        var plan = WorldPreviewMeshCompiler.plan(BlockPos.ZERO,
                List.of(entry(1, Blocks.AIR), entry(1, Blocks.IRON_BLOCK)), 1,
                state -> ChunkSectionLayer.SOLID);

        assertThat(plan.entries()).extracting(WorldPreviewMeshCompiler.PlannedEntry::state)
                .containsExactly(Blocks.IRON_BLOCK.defaultBlockState());
    }

    @Test
    void cancelledCompilationDoesNotReturnUsableMesh() {
        AtomicBoolean cancelled = new AtomicBoolean(true);

        List<MultiblockPreviewSnapshot.Entry> entries = List.of(entry(0, Blocks.IRON_BLOCK));
        assertThatThrownBy(() -> WorldPreviewMeshCompiler.compile(
                null, BlockPos.ZERO, entries, Integer.MAX_VALUE, null, cancelled))
                .isInstanceOf(WorldPreviewMeshCompiler.CancelledCompilation.class);
    }

    @Test
    void fluidEntriesRouteToTranslucentLayer() {
        var plan = WorldPreviewMeshCompiler.plan(BlockPos.ZERO,
                List.of(entry(0, Blocks.WATER)), Integer.MAX_VALUE, state -> ChunkSectionLayer.SOLID);

        assertThat(plan.entries().getFirst().fluidLayer()).isEqualTo(ChunkSectionLayer.TRANSLUCENT);
    }

    @Test
    void compilerUsesFullBrightForBothLightLayers() {
        assertThat(WorldPreviewMeshCompiler.previewLight(LightLayer.BLOCK, BlockPos.ZERO))
                .isEqualTo(WorldPreviewMeshCompiler.FULL_BRIGHT_LEVEL);
        assertThat(WorldPreviewMeshCompiler.previewLight(LightLayer.SKY, BlockPos.ZERO))
                .isEqualTo(WorldPreviewMeshCompiler.FULL_BRIGHT_LEVEL);
    }

    @Test
    void translucentLayerPublishesSortMetadata() {
        assertThat(WorldPreviewMeshCompiler.hasSortMetadata(ChunkSectionLayer.TRANSLUCENT)).isTrue();
        assertThat(WorldPreviewMeshCompiler.hasSortMetadata(ChunkSectionLayer.SOLID)).isFalse();
    }

    @Test
    void compileClosesIntermediateBuildersAndMeshDataOnFailure() {
        AtomicReference<WorldPreviewMeshCompiler.CompilationResources> resources = new AtomicReference<>();
        AtomicReference<MeshData> intermediateMesh = new AtomicReference<>();
        assertThatThrownBy(() -> WorldPreviewMeshCompiler.compile(null, BlockPos.ZERO,
                List.of(entry(0, Blocks.GLASS)), Integer.MAX_VALUE, new net.minecraft.world.phys.Vec3(0, 0, 0),
                new AtomicBoolean(), captured -> {
                    resources.set(captured);
                    MeshData mesh = nonEmptyMeshData();
                    intermediateMesh.set(mesh);
                    captured.meshes().put(ChunkSectionLayer.CUTOUT, mesh);
                    throw new IllegalStateException("injected compile failure");
                }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("injected compile failure");

        assertThat(resources.get()).isNotNull();
        assertThat(resources.get().closed()).isTrue();
        assertThatThrownBy(intermediateMesh.get()::vertexBuffer).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void productionCompileCreatesTranslucentSortMetadata() {
        Assumptions.assumeTrue(Minecraft.getInstance() != null);
        WorldPreviewMesh mesh = WorldPreviewMeshCompiler.compile(null, BlockPos.ZERO,
                List.of(entry(0, Blocks.WATER)), Integer.MAX_VALUE,
                new net.minecraft.world.phys.Vec3(0, 0, 0), new AtomicBoolean());
        try {
            assertThat(mesh.meshes()).containsKey(ChunkSectionLayer.TRANSLUCENT);
            assertThat(mesh.translucentSortState()).isNotNull();
        } finally {
            mesh.close();
        }
    }

    @Test
    void planTracksBlockEntityPositionsWithoutClientModels() {
        var plan = WorldPreviewMeshCompiler.plan(BlockPos.ZERO,
                List.of(entry(0, Blocks.CHEST)), Integer.MAX_VALUE, state -> ChunkSectionLayer.SOLID);

        assertThat(plan.blockEntityPositions()).containsExactly(new BlockPos(0, 0, 0));
    }

    @Test
    void failedCompilationCleanupClosesEveryResource() {
        List<String> closed = new ArrayList<>();
        assertThatThrownBy(() -> WorldPreviewMeshCompiler.closeResources(List.of(
                () -> closed.add("mesh"),
                () -> { closed.add("builder"); throw new IllegalStateException("close failure"); },
                () -> closed.add("remaining"))))
                .isInstanceOf(IllegalStateException.class);

        assertThat(closed).containsExactly("mesh", "builder", "remaining");
    }

    @Test
    void worldPreviewMeshCloseIsIdempotent() {
        var owner = new CloseCounter();
        var meshData = nonEmptyMeshData();
        var mesh = new WorldPreviewMesh(owner, java.util.Map.of(ChunkSectionLayer.SOLID, meshData), null, java.util.Set.of());

        mesh.close();
        mesh.close();

        assertThat(owner.closes).isEqualTo(1);
        assertThatThrownBy(meshData::vertexBuffer).isInstanceOf(IllegalStateException.class);
    }

    private static MeshData nonEmptyMeshData() {
        var buffer = new com.mojang.blaze3d.vertex.ByteBufferBuilder(64);
        buffer.reserve(1);
        return new MeshData(buffer.build(),
                new MeshData.DrawState(DefaultVertexFormat.BLOCK, 1, 0, VertexFormat.Mode.QUADS,
                        VertexFormat.IndexType.SHORT));
    }

    private static MultiblockPreviewSnapshot.Entry entry(int y, net.minecraft.world.level.block.Block block) {
        return new MultiblockPreviewSnapshot.Entry(new BlockPos(0, y, 0), block.defaultBlockState());
    }

    private static final class CloseCounter implements AutoCloseable {
        private int closes;

        @Override
        public void close() {
            closes++;
        }
    }
}
