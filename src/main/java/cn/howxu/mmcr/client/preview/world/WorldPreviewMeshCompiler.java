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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Compiles visible world preview blocks into one reusable mesh per render layer.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class WorldPreviewMeshCompiler {
    private WorldPreviewMeshCompiler() { }

    public static WorldPreviewMesh compile(Level level, BlockPos controllerPos,
            List<MultiblockPreviewSnapshot.Entry> entries, int selectedLayer, Vec3 camera,
            AtomicBoolean cancelled) {
        if (cancelled.get()) throw new CancelledCompilation();
        Minecraft minecraft = Minecraft.getInstance();
        SectionBufferBuilderPack builders = new SectionBufferBuilderPack();
        Map<ChunkSectionLayer, BufferBuilder> started = new EnumMap<>(ChunkSectionLayer.class);
        Map<ChunkSectionLayer, MeshData> meshes = new EnumMap<>(ChunkSectionLayer.class);
        Set<BlockPos> blockEntities = new HashSet<>();
        try {
            var modelSet = minecraft.getModelManager().getBlockStateModelSet();
            var fluidSet = minecraft.getModelManager().getFluidStateModelSet();
            ModelBlockRenderer blockRenderer = new ModelBlockRenderer(
                    minecraft.options.ambientOcclusion().get(), true, minecraft.getBlockColors());
            FluidRenderer fluidRenderer = new FluidRenderer(fluidSet);
            Map<BlockPos, BlockState> visibleStates = new LinkedHashMap<>();
            for (MultiblockPreviewSnapshot.Entry entry : entries) {
                if (selectedLayer != Integer.MAX_VALUE && entry.relativePos().getY() != selectedLayer) continue;
                BlockPos position = controllerPos.offset(entry.relativePos());
                if (!entry.state().isAir()) visibleStates.put(position, entry.state());
            }
            BlockAndTintGetter region = region(level, visibleStates);
            Map<BlockState, BlockStateModel> models = new HashMap<>();
            BlockQuadOutput blockOutput = (x, y, z, quad, instance) -> builderFor(started, builders,
                    quad.materialInfo().layer()).putBlockBakedQuad(x, y, z, quad, instance);
            FluidRenderer.Output fluidOutput = layer -> builderFor(started, builders, layer);
            for (Map.Entry<BlockPos, BlockState> entry : visibleStates.entrySet()) {
                if (cancelled.get()) throw new CancelledCompilation();
                BlockPos position = entry.getKey();
                BlockState state = entry.getValue();
                if (state.hasBlockEntity()) blockEntities.add(position.immutable());
                if (!state.getFluidState().isEmpty()) {
                    fluidRenderer.tesselate(region, position, offset(fluidOutput, position), state,
                            state.getFluidState());
                }
                if (state.getRenderShape() == RenderShape.MODEL) {
                    blockRenderer.tesselateBlock(blockOutput, position.getX(), position.getY(), position.getZ(),
                            region, position, state, models.computeIfAbsent(state, modelSet::get),
                            state.getSeed(position));
                }
            }
            if (cancelled.get()) throw new CancelledCompilation();
            MeshData.SortState sortState = null;
            VertexSorting sorting = VertexSorting.byDistance((float) camera.x, (float) camera.y, (float) camera.z);
            for (Map.Entry<ChunkSectionLayer, BufferBuilder> entry : started.entrySet()) {
                MeshData mesh = entry.getValue().build();
                if (mesh == null) continue;
                if (entry.getKey() == ChunkSectionLayer.TRANSLUCENT) {
                    sortState = mesh.sortQuads(builders.buffer(entry.getKey()), sorting);
                }
                meshes.put(entry.getKey(), mesh);
            }
            return new WorldPreviewMesh(builders, meshes, sortState, blockEntities);
        } catch (RuntimeException exception) {
            meshes.values().forEach(MeshData::close);
            builders.close();
            throw exception;
        }
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
            @Override public int getBrightness(LightLayer lightLayer, BlockPos position) { return 15; }
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
}
