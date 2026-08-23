package cn.howxu.mmcr.client.preview.world;

import cn.howxu.mmcr.internal.preview.MultiblockPreviewSnapshot;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Compiles visible world preview blocks into one reusable mesh per render layer.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class WorldPreviewMeshCompiler {
    /** Full-bright preview lighting is deliberately independent of the source level light. */
    static final int FULL_BRIGHT_LEVEL = 15;

    private WorldPreviewMeshCompiler() { }

    static CompilationPlan plan(BlockPos controllerPos, List<MultiblockPreviewSnapshot.Entry> entries,
            int selectedLayer) {
        Objects.requireNonNull(controllerPos, "controllerPos");
        Objects.requireNonNull(entries, "entries");
        List<PlannedEntry> planned = new ArrayList<>();
        Set<BlockPos> blockEntities = new HashSet<>();
        for (MultiblockPreviewSnapshot.Entry entry : entries) {
            if (selectedLayer != Integer.MAX_VALUE && entry.relativePos().getY() != selectedLayer) continue;
            BlockState state = entry.state();
            if (state.isAir()) continue;
            BlockPos position = controllerPos.offset(entry.relativePos()).immutable();
            if (state.hasBlockEntity()) blockEntities.add(position);
            ChunkSectionLayer fluidLayer = state.getFluidState().isEmpty() ? null : ChunkSectionLayer.TRANSLUCENT;
            planned.add(new PlannedEntry(position, state, fluidLayer));
        }
        return new CompilationPlan(planned, blockEntities);
    }

    static int previewLight(LightLayer lightLayer, BlockPos position) {
        return FULL_BRIGHT_LEVEL;
    }

    static boolean hasSortMetadata(ChunkSectionLayer layer) {
        return layer == ChunkSectionLayer.TRANSLUCENT;
    }

    static boolean needsTranslucentResort(Vec3 previousCamera, Vec3 camera) {
        return previousCamera == null || !previousCamera.equals(camera);
    }

    public static WorldPreviewMesh compile(Level level, BlockPos controllerPos,
            List<MultiblockPreviewSnapshot.Entry> entries, int selectedLayer, Vec3 camera,
            AtomicBoolean cancelled) {
        return compile(level, controllerPos, entries, selectedLayer, camera, cancelled, ignored -> { });
    }

    static WorldPreviewMesh compile(Level level, BlockPos controllerPos,
            List<MultiblockPreviewSnapshot.Entry> entries, int selectedLayer, Vec3 camera,
            AtomicBoolean cancelled, Consumer<CompilationResources> failureInjector) {
        Minecraft minecraft = Minecraft.getInstance();
        SectionBufferBuilderPack builders = new SectionBufferBuilderPack();
        Map<ChunkSectionLayer, BufferBuilder> started = new EnumMap<>(ChunkSectionLayer.class);
        Map<ChunkSectionLayer, MeshData> meshes = new EnumMap<>(ChunkSectionLayer.class);
        Set<BlockPos> blockEntities = new HashSet<>();
        CompilationResources resources = new CompilationResources(builders, meshes);
        try {
            failureInjector.accept(resources);
            if (cancelled.get()) throw new CancelledCompilation();
            var modelSet = minecraft.getModelManager().getBlockStateModelSet();
            var fluidSet = minecraft.getModelManager().getFluidStateModelSet();
            ModelBlockRenderer blockRenderer = new ModelBlockRenderer(
                    minecraft.options.ambientOcclusion().get(), true, minecraft.getBlockColors());
            FluidRenderer fluidRenderer = new FluidRenderer(fluidSet);
            CompilationPlan plan = plan(controllerPos, entries, selectedLayer);
            Map<BlockPos, BlockState> visibleStates = new LinkedHashMap<>();
            plan.entries().forEach(entry -> visibleStates.put(entry.position(), entry.state()));
            blockEntities.addAll(plan.blockEntityPositions());
            BlockAndTintGetter region = region(level, visibleStates);
            Map<BlockState, BlockStateModel> models = new HashMap<>();
            for (PlannedEntry planned : plan.entries()) {
                if (cancelled.get()) throw new CancelledCompilation();
                BlockPos position = planned.position();
                BlockState state = planned.state();
                if (planned.fluidLayer() != null) {
                    FluidRenderer.Output fluidOutput = layer -> builderFor(started, builders, planned.fluidLayer());
                    fluidRenderer.tesselate(region, position, offset(fluidOutput, position), state, state.getFluidState());
                }
                if (state.getRenderShape() == RenderShape.MODEL) {
                    BlockStateModel model = models.computeIfAbsent(state, modelSet::get);
                    BlockQuadOutput blockOutput = (x, y, z, quad, instance) -> builderFor(started, builders,
                            quad.materialInfo().layer()).putBlockBakedQuad(x, y, z, quad, instance);
                    blockRenderer.tesselateBlock(blockOutput, position.getX(), position.getY(), position.getZ(),
                            region, position, state, model, state.getSeed(position));
                }
            }
            if (cancelled.get()) throw new CancelledCompilation();
            MeshData.SortState sortState = null;
            VertexSorting sorting = VertexSorting.byDistance((float) camera.x, (float) camera.y, (float) camera.z);
            for (Map.Entry<ChunkSectionLayer, BufferBuilder> entry : started.entrySet()) {
                MeshData mesh = entry.getValue().build();
                if (mesh == null) continue;
                if (hasSortMetadata(entry.getKey())) {
                    sortState = mesh.sortQuads(builders.buffer(entry.getKey()), sorting);
                }
                meshes.put(entry.getKey(), mesh);
            }
            return new WorldPreviewMesh(builders, meshes, sortState, blockEntities);
        } catch (RuntimeException exception) {
            List<AutoCloseable> closeables = new ArrayList<>(meshes.values());
            closeables.add(builders);
            try {
                closeResources(closeables);
            } catch (RuntimeException cleanupFailure) {
                exception.addSuppressed(cleanupFailure);
            } finally {
                resources.markClosed();
            }
            throw exception;
        }
    }


    static void closeResources(Iterable<? extends AutoCloseable> resources) {
        RuntimeException failure = null;
        for (AutoCloseable resource : resources) {
            try {
                resource.close();
            } catch (Exception exception) {
                RuntimeException runtime = exception instanceof RuntimeException
                        ? (RuntimeException) exception : new IllegalStateException("resource cleanup failed", exception);
                if (failure == null) failure = runtime;
            }
        }
        if (failure != null) throw failure;
    }

    private static BufferBuilder builderFor(Map<ChunkSectionLayer, BufferBuilder> started,
            SectionBufferBuilderPack builders, ChunkSectionLayer layer) {
        return started.computeIfAbsent(layer, key -> new BufferBuilder(builders.buffer(key),
                VertexFormat.Mode.QUADS, key.vertexFormat()));
    }

    private static BlockAndTintGetter region(Level level, Map<BlockPos, BlockState> states) {
        return new BlockAndTintGetter() {
            @Override public BlockState getBlockState(BlockPos position) {
                return states.getOrDefault(position, level.getBlockState(position));
            }
            @Override public FluidState getFluidState(BlockPos position) {
                return getBlockState(position).getFluidState();
            }
            @Override public BlockEntity getBlockEntity(BlockPos position) { return level.getBlockEntity(position); }
            @Override public int getHeight() { return level.getHeight(); }
            @Override public int getMinY() { return level.getMinY(); }
            @Override public int getBrightness(LightLayer lightLayer, BlockPos position) {
                return previewLight(lightLayer, position);
            }
            @Override public CardinalLighting cardinalLighting() { return CardinalLighting.DEFAULT; }
            @Override public LevelLightEngine getLightEngine() { return level.getLightEngine(); }
            @Override public int getBlockTint(BlockPos position, ColorResolver resolver) {
                return resolver.getColor(level.getBiome(position).value(), position.getX(), position.getZ());
            }
        };
    }

    private static FluidRenderer.Output offset(FluidRenderer.Output output, BlockPos position) {
        float x = position.getX() & ~15;
        float y = position.getY() & ~15;
        float z = position.getZ() & ~15;
        return layer -> new SectionOriginConsumer(output.getBuilder(layer), x, y, z);
    }

    private static final class SectionOriginConsumer implements VertexConsumer {
        private final VertexConsumer delegate;
        private final float x;
        private final float y;
        private final float z;

        private SectionOriginConsumer(VertexConsumer delegate, float x, float y, float z) {
            this.delegate = delegate; this.x = x; this.y = y; this.z = z;
        }
        @Override public VertexConsumer addVertex(float x, float y, float z) { return delegate.addVertex(x + this.x, y + this.y, z + this.z); }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) { return delegate.setColor(r, g, b, a); }
        @Override public VertexConsumer setColor(int color) { return delegate.setColor(color); }
        @Override public VertexConsumer setUv(float u, float v) { return delegate.setUv(u, v); }
        @Override public VertexConsumer setUv1(int u, int v) { return delegate.setUv1(u, v); }
        @Override public VertexConsumer setUv2(int u, int v) { return delegate.setUv2(u, v); }
        @Override public VertexConsumer setNormal(float x, float y, float z) { return delegate.setNormal(x, y, z); }
        @Override public VertexConsumer setLineWidth(float width) { return delegate.setLineWidth(width); }
    }

    static final class CancelledCompilation extends RuntimeException { }

    static final class CompilationResources {
        private final SectionBufferBuilderPack builders;
        private final Map<ChunkSectionLayer, MeshData> meshes;
        private boolean closed;

        private CompilationResources(SectionBufferBuilderPack builders, Map<ChunkSectionLayer, MeshData> meshes) {
            this.builders = builders;
            this.meshes = meshes;
        }

        SectionBufferBuilderPack builders() { return builders; }
        Map<ChunkSectionLayer, MeshData> meshes() { return meshes; }
        boolean closed() { return closed; }
        void markClosed() { closed = true; }
    }

    record CompilationPlan(List<PlannedEntry> entries, Set<BlockPos> blockEntityPositions) {
        CompilationPlan {
            entries = List.copyOf(entries);
            blockEntityPositions = Set.copyOf(blockEntityPositions);
        }
    }

    record PlannedEntry(BlockPos position, BlockState state, ChunkSectionLayer fluidLayer) { }
}
