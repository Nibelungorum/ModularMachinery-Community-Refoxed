package cn.howxu.mmcr.client.preview.world;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.client.preview.PreviewLevel;
import cn.howxu.mmcr.client.preview.PreviewVisibility;
import cn.howxu.mmcr.client.preview.StructurePreviewSchema;
import cn.howxu.mmcr.internal.preview.MultiblockPreviewSnapshot;
import cn.howxu.mmcr.test.TestBootstrap;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;

import java.util.*;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;

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

        var plan = WorldPreviewMeshCompiler.plan(BlockPos.ZERO, entries, Integer.MAX_VALUE);

        assertThat(plan.entries()).extracting(WorldPreviewMeshCompiler.PlannedEntry::state)
                .containsExactly(Blocks.IRON_BLOCK.defaultBlockState(), Blocks.GLASS.defaultBlockState());
    }

    @Test
    void compilerSkipsAirAndNonSelectedLayerEntries() {
        List<MultiblockPreviewSnapshot.Entry> entries = List.of(
                entry(0, Blocks.IRON_BLOCK),
                entry(1, Blocks.GOLD_BLOCK),
                entry(2, Blocks.AIR));

        var plan = WorldPreviewMeshCompiler.plan(BlockPos.ZERO, entries, 1);

        assertThat(plan.entries()).extracting(WorldPreviewMeshCompiler.PlannedEntry::position)
                .containsExactly(new BlockPos(0, 1, 0));
    }

    @Test
    void compilerFiltersAirEvenWhenAirIsOnTheSelectedLayer() {
        var plan = WorldPreviewMeshCompiler.plan(BlockPos.ZERO,
                List.of(entry(1, Blocks.AIR), entry(1, Blocks.IRON_BLOCK)), 1);

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
    void liveRegionWaterTintIsOpaqueAndPreservesBiomeRgb() throws Exception {
        var state = Blocks.WATER.defaultBlockState();
        StructurePreviewSchema schema = new StructurePreviewSchema(MMCR.id("world_preview_water_tint_test"),
                Map.of(BlockPos.ZERO, state), Map.of());
        try (PreviewLevel level = PreviewLevel.create(schema, () -> PreviewVisibility.ALL)) {
            var factory = WorldPreviewMeshCompiler.class.getDeclaredMethod("region", Level.class, Map.class);
            factory.setAccessible(true);
            var region = (BlockAndTintGetter) factory.invoke(null, level, schema.states());

            int tint = BiomeColors.getAverageWaterColor(region, BlockPos.ZERO);

            assertThat(tint).isEqualTo(0xFF3F76E4);
        }
    }

    @Test
    void snapshotRegionWaterTintIsOpaqueForCapturedAndFallbackBiomes() throws Exception {
        var state = Blocks.WATER.defaultBlockState();
        StructurePreviewSchema schema = new StructurePreviewSchema(MMCR.id("world_snapshot_water_tint_test"),
                Map.of(BlockPos.ZERO, state), Map.of());
        try (PreviewLevel level = PreviewLevel.create(schema, () -> PreviewVisibility.ALL)) {
            Biome biome = level.getUncachedNoiseBiome(0, 0, 0).value();
            Biome fallbackBiome = new Biome.BiomeBuilder().hasPrecipitation(false).temperature(0.8F).downfall(0.0F)
                    .specialEffects(new BiomeSpecialEffects.Builder().waterColor(0x123456)
                            .waterFogColor(0x050533).fogColor(0xC0D8FF).skyColor(0x78A7FF).build())
                    .mobSpawnSettings(new MobSpawnSettings.Builder().build())
                    .generationSettings(new BiomeGenerationSettings.PlainBuilder().build())
                    .build();
            var constructor = Class.forName(WorldPreviewCompileInput.class.getName() + "$SnapshotRegion")
                    .getDeclaredConstructor(Map.class, Map.class, Biome.class, int.class, int.class, LevelLightEngine.class);
            constructor.setAccessible(true);
            var region = (BlockAndTintGetter) constructor.newInstance(
                    Map.of(BlockPos.ZERO.asLong(), state), Map.of(BlockPos.ZERO.asLong(), biome),
                    fallbackBiome, level.getMinBuildHeight(), level.getHeight(), null);

            int capturedTint = BiomeColors.getAverageWaterColor(region, BlockPos.ZERO);
            int fallbackTint = BiomeColors.getAverageWaterColor(region, new BlockPos(2, 0, 0));

            assertThat(capturedTint).isEqualTo(0xFF3F76E4);
            assertThat(fallbackTint).isEqualTo(0xFF123456);
        }
    }

    @Test
    void fluidEntriesRouteToTranslucentLayer() {
        var plan = WorldPreviewMeshCompiler.plan(BlockPos.ZERO,
                List.of(entry(0, Blocks.WATER)), Integer.MAX_VALUE);

        assertThat(plan.entries().getFirst().fluidLayer()).isEqualTo(RenderType.translucent());
    }

    @Test
    void compilerUsesFullBrightForBothLightLayers() {
        assertThat(WorldPreviewMeshCompiler.previewLight(LightLayer.BLOCK, BlockPos.ZERO))
                .isEqualTo(WorldPreviewMeshCompiler.FULL_BRIGHT_LEVEL);
        assertThat(WorldPreviewMeshCompiler.previewLight(LightLayer.SKY, BlockPos.ZERO))
                .isEqualTo(WorldPreviewMeshCompiler.FULL_BRIGHT_LEVEL);
    }

    @Test
    void sortedLayersPublishSortMetadata() {
        assertThat(WorldPreviewMeshCompiler.hasSortMetadata(RenderType.translucent())).isTrue();
        assertThat(WorldPreviewMeshCompiler.hasSortMetadata(RenderType.tripwire())).isTrue();
        assertThat(WorldPreviewMeshCompiler.hasSortMetadata(RenderType.solid())).isFalse();
    }

    @Test
    void sortedLayerIsRebuiltWhenCameraMovesWithinTheSameBlock() {
        var camera = new Vec3(1.25, 2.5, 3.75);

        assertThat(WorldPreviewMeshCompiler.needsResort(null, camera)).isTrue();
        assertThat(WorldPreviewMeshCompiler.needsResort(camera,
                new Vec3(1.5, 2.5, 3.75))).isTrue();
        assertThat(WorldPreviewMeshCompiler.needsResort(camera, camera)).isFalse();
    }

    @Test
    void compileClosesIntermediateBuildersAndMeshDataOnFailure() {
        AtomicReference<WorldPreviewMeshCompiler.CompilationResources> resources = new AtomicReference<>();
        AtomicReference<MeshData> intermediateMesh = new AtomicReference<>();
        assertThatThrownBy(() -> WorldPreviewMeshCompiler.compile(null, BlockPos.ZERO,
                List.of(entry(0, Blocks.GLASS)), Integer.MAX_VALUE, new Vec3(0, 0, 0),
                new AtomicBoolean(), captured -> {
                    resources.set(captured);
                    MeshData mesh = nonEmptyMeshData();
                    intermediateMesh.set(mesh);
                    captured.meshes().put(RenderType.cutout(), mesh);
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
        try (WorldPreviewMesh mesh = WorldPreviewMeshCompiler.compile(null, BlockPos.ZERO,
                List.of(entry(0, Blocks.WATER)), Integer.MAX_VALUE,
                new Vec3(0, 0, 0), new AtomicBoolean())) {
            assertThat(mesh.meshes()).containsKey(RenderType.translucent());
            assertThat(mesh.sortStates()).containsKey(RenderType.translucent());
        }
    }

    @Test
    void planTracksBlockEntityPositionsWithoutClientModels() {
        var plan = WorldPreviewMeshCompiler.plan(BlockPos.ZERO,
                List.of(entry(0, Blocks.CHEST)), Integer.MAX_VALUE);

        assertThat(plan.blockEntityPositions()).containsExactly(new BlockPos(0, 0, 0));
    }

    @Test
    void failedCompilationCleanupClosesEveryResource() {
        List<String> closed = new ArrayList<>();
        assertThatThrownBy(() -> WorldPreviewMeshCompiler.closeResources(List.of(
                () -> closed.add("mesh"),
                () -> {
                    closed.add("builder");
                    throw new IllegalStateException("close failure");
                },
                () -> closed.add("remaining"))))
                .isInstanceOf(IllegalStateException.class);

        assertThat(closed).containsExactly("mesh", "builder", "remaining");
    }

    @Test
    void worldPreviewMeshCloseIsIdempotent() {
        var owner = new CloseCounter();
        var meshData = nonEmptyMeshData();
        var mesh = new WorldPreviewMesh(owner, Map.of(RenderType.solid(), meshData), Map.of(), Set.of());

        mesh.close();
        mesh.close();

        assertThat(owner.closes).isEqualTo(1);
        assertThatThrownBy(meshData::vertexBuffer).isInstanceOf(IllegalStateException.class);
    }

    private static MeshData nonEmptyMeshData() {
        try (var buffer = new ByteBufferBuilder(64)) {
            buffer.reserve(1);
            return new MeshData(Objects.requireNonNull(buffer.build()),
                    new MeshData.DrawState(DefaultVertexFormat.BLOCK, 1, 0, VertexFormat.Mode.QUADS,
                            VertexFormat.IndexType.SHORT));
        }
    }

    private static MultiblockPreviewSnapshot.Entry entry(int y, Block block) {
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
