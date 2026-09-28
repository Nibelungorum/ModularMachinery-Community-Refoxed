package cn.howxu.mmcr.client.preview.world;

import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexSorting;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Owns the reusable mesh data generated for a world preview.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class WorldPreviewMesh implements AutoCloseable {
    private final AutoCloseable builders;
    private final SectionBufferBuilderPack sortableBuilders;
    private final Map<RenderType, MeshData> meshes;
    private final MeshData.SortState translucentSortState;
    private final Set<BlockPos> blockEntityPositions;
    private boolean closed;

    WorldPreviewMesh(SectionBufferBuilderPack builders, Map<RenderType, MeshData> meshes,
            MeshData.SortState translucentSortState, Set<BlockPos> blockEntityPositions) {
        this(builders, builders, meshes, translucentSortState, blockEntityPositions);
    }

    WorldPreviewMesh(AutoCloseable builders, Map<RenderType, MeshData> meshes,
            MeshData.SortState translucentSortState, Set<BlockPos> blockEntityPositions) {
        this(builders, null, meshes, translucentSortState, blockEntityPositions);
    }

    private WorldPreviewMesh(AutoCloseable builders, SectionBufferBuilderPack sortableBuilders,
            Map<RenderType, MeshData> meshes,
            MeshData.SortState translucentSortState, Set<BlockPos> blockEntityPositions) {
        this.builders = Objects.requireNonNull(builders, "builders");
        this.sortableBuilders = sortableBuilders;
        this.meshes = Map.copyOf(meshes);
        this.translucentSortState = translucentSortState;
        this.blockEntityPositions = Set.copyOf(blockEntityPositions);
    }

    public Map<RenderType, MeshData> meshes() {
        return meshes;
    }

    public MeshData.SortState translucentSortState() {
        return translucentSortState;
    }

    public Set<BlockPos> blockEntityPositions() {
        return blockEntityPositions;
    }

    ByteBufferBuilder.Result sortedTranslucentIndex(Vec3 camera) {
        if (translucentSortState == null || sortableBuilders == null) return null;
        return translucentSortState.buildSortedIndexBuffer(sortableBuilders.buffer(RenderType.translucent()),
                VertexSorting.byDistance((float) camera.x, (float) camera.y, (float) camera.z));
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        List<AutoCloseable> resources = new ArrayList<>(meshes.values());
        resources.add(builders);
        WorldPreviewMeshCompiler.closeResources(resources);
    }
}
