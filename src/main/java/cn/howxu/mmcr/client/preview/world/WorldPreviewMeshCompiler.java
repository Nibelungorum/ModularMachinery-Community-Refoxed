package cn.howxu.mmcr.client.preview.world;

import cn.howxu.mmcr.internal.preview.MultiblockPreviewSnapshot;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.LiquidBlockRenderer;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.data.ModelData;

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
            RenderType fluidLayer = state.getFluidState().isEmpty()
                    ? null : ItemBlockRenderTypes.getRenderLayer(state.getFluidState());
            planned.add(new PlannedEntry(position, state, fluidLayer));
        }
        return new CompilationPlan(planned, blockEntities);
    }

    static int previewLight(LightLayer lightLayer, BlockPos position) {
        return FULL_BRIGHT_LEVEL;
    }

    static boolean hasSortMetadata(RenderType layer) {
        return layer.sortOnUpload();
    }

    static boolean needsResort(Vec3 previousCamera, Vec3 camera) {
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
        if (cancelled.get()) throw new CancelledCompilation();
        CompilationPlan plan = plan(controllerPos, entries, selectedLayer);
        if (minecraft == null) {
            return compileInternal(null, null, null, null, plan, camera, cancelled, failureInjector);
        }
        Map<BlockPos, BlockState> visibleStates = new LinkedHashMap<>();
        plan.entries().forEach(entry -> visibleStates.put(entry.position(), entry.state()));
        return compileInternal(minecraft.getBlockRenderer().getBlockModelShaper(),
                minecraft.getBlockRenderer().getLiquidBlockRenderer(), minecraft.getBlockColors(),
                region(level, visibleStates), plan,
                camera, cancelled, failureInjector);
    }

    public static WorldPreviewMesh compileSnapshot(WorldPreviewCompileInput input, BlockPos controllerPos,
            List<MultiblockPreviewSnapshot.Entry> entries, int selectedLayer, Vec3 camera,
            AtomicBoolean cancelled) {
        return compileInternal(input.blockModels(), input.fluidModels(), input.blockColors(),
                input.region(), plan(controllerPos, entries, selectedLayer),
                camera, cancelled, ignored -> { });
    }

    private static WorldPreviewMesh compileInternal(BlockModelShaper modelSet, LiquidBlockRenderer fluidRenderer,
            BlockColors blockColors, BlockAndTintGetter region,
            CompilationPlan plan, Vec3 camera, AtomicBoolean cancelled,
            Consumer<CompilationResources> failureInjector) {
        SectionBufferBuilderPack builders = new SectionBufferBuilderPack();
        Map<RenderType, BufferBuilder> started = new LinkedHashMap<>();
        Map<RenderType, MeshData> meshes = new LinkedHashMap<>();
        Set<BlockPos> blockEntities = new HashSet<>();
        CompilationResources resources = new CompilationResources(builders, meshes);
        try {
            failureInjector.accept(resources);
            if (cancelled.get()) throw new CancelledCompilation();
            ModelBlockRenderer blockRenderer = new ModelBlockRenderer(blockColors);
            blockEntities.addAll(plan.blockEntityPositions());
            Map<BlockState, BakedModel> models = new HashMap<>();
            for (PlannedEntry planned : plan.entries()) {
                if (cancelled.get()) throw new CancelledCompilation();
                BlockPos position = planned.position();
                BlockState state = planned.state();
                if (planned.fluidLayer() != null) {
                    fluidRenderer.tesselate(region, position,
                            offset(builderFor(started, builders, planned.fluidLayer()), position),
                            state, state.getFluidState());
                }
                if (state.getRenderShape() == RenderShape.MODEL) {
                    BakedModel model = models.computeIfAbsent(state, modelSet::getBlockModel);
                    ModelData modelData = model.getModelData(region, position, state, region.getModelData(position));
                    RandomSource random = RandomSource.create(state.getSeed(position));
                    for (RenderType renderType : model.getRenderTypes(state, random, modelData)) {
                        PoseStack poseStack = new PoseStack();
                        poseStack.translate(position.getX(), position.getY(), position.getZ());
                        blockRenderer.tesselateBlock(region, model, state, position, poseStack,
                                builderFor(started, builders, renderType), true, random,
                                state.getSeed(position), OverlayTexture.NO_OVERLAY, modelData, renderType);
                    }
                }
            }
            if (cancelled.get()) throw new CancelledCompilation();
            Map<RenderType, MeshData.SortState> sortStates = new LinkedHashMap<>();
            VertexSorting sorting = VertexSorting.byDistance((float) camera.x, (float) camera.y, (float) camera.z);
            for (Map.Entry<RenderType, BufferBuilder> entry : started.entrySet()) {
                MeshData mesh = entry.getValue().build();
                if (mesh == null) continue;
                if (hasSortMetadata(entry.getKey())) {
                    MeshData.SortState sortState = mesh.sortQuads(builders.buffer(entry.getKey()), sorting);
                    if (sortState != null) sortStates.put(entry.getKey(), sortState);
                }
                meshes.put(entry.getKey(), mesh);
            }
            return new WorldPreviewMesh(builders, meshes, sortStates, blockEntities);
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

    private static BufferBuilder builderFor(Map<RenderType, BufferBuilder> started,
            SectionBufferBuilderPack builders, RenderType layer) {
        return started.computeIfAbsent(layer, key -> new BufferBuilder(builders.buffer(key),
                key.mode(), key.format()));
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
            @Override public int getMinBuildHeight() { return level.getMinBuildHeight(); }
            @Override public int getBrightness(LightLayer lightLayer, BlockPos position) {
                return previewLight(lightLayer, position);
            }
            @Override public float getShade(net.minecraft.core.Direction direction, boolean shade) {
                return level.getShade(direction, shade);
            }
            @Override public LevelLightEngine getLightEngine() { return level.getLightEngine(); }
            @Override public int getBlockTint(BlockPos position, ColorResolver resolver) {
                return resolver.getColor(level.getBiome(position).value(), position.getX(), position.getZ());
            }
        };
    }

    private static VertexConsumer offset(VertexConsumer output, BlockPos position) {
        float x = position.getX() & ~15;
        float y = position.getY() & ~15;
        float z = position.getZ() & ~15;
        return new SectionOriginConsumer(output, x, y, z);
    }

    private record SectionOriginConsumer(VertexConsumer delegate, float x, float y, float z) implements VertexConsumer {
        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            return delegate.addVertex(x + this.x, y + this.y, z + this.z);
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            return delegate.setColor(r, g, b, a);
        }

        @Override
        public VertexConsumer setColor(int color) {
            return delegate.setColor(color);
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            return delegate.setUv(u, v);
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return delegate.setUv1(u, v);
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return delegate.setUv2(u, v);
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            return delegate.setNormal(x, y, z);
        }

        }

    static final class CancelledCompilation extends RuntimeException { }

    static final class CompilationResources {
        private final SectionBufferBuilderPack builders;
        private final Map<RenderType, MeshData> meshes;
        private boolean closed;

        private CompilationResources(SectionBufferBuilderPack builders, Map<RenderType, MeshData> meshes) {
            this.builders = builders;
            this.meshes = meshes;
        }

        SectionBufferBuilderPack builders() { return builders; }
        Map<RenderType, MeshData> meshes() { return meshes; }
        boolean closed() { return closed; }
        void markClosed() { closed = true; }
    }

    record CompilationPlan(List<PlannedEntry> entries, Set<BlockPos> blockEntityPositions) {
        CompilationPlan {
            entries = List.copyOf(entries);
            blockEntityPositions = Set.copyOf(blockEntityPositions);
        }
    }

    record PlannedEntry(BlockPos position, BlockState state, RenderType fluidLayer) { }
}
