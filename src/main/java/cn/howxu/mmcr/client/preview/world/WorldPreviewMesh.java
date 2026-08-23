package cn.howxu.mmcr.client.preview.world;

import com.mojang.blaze3d.vertex.MeshData;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.core.BlockPos;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Owns the reusable mesh data generated for a world preview.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class WorldPreviewMesh implements AutoCloseable {
    private final SectionBufferBuilderPack builders;
    private final Map<ChunkSectionLayer, MeshData> meshes;
    private final MeshData.SortState translucentSortState;
    private final Set<BlockPos> blockEntityPositions;
    private boolean closed;

    WorldPreviewMesh(SectionBufferBuilderPack builders, Map<ChunkSectionLayer, MeshData> meshes,
            MeshData.SortState translucentSortState, Set<BlockPos> blockEntityPositions) {
        this.builders = Objects.requireNonNull(builders, "builders");
        this.meshes = Map.copyOf(meshes);
        this.translucentSortState = translucentSortState;
        this.blockEntityPositions = Set.copyOf(blockEntityPositions);
    }

    public Map<ChunkSectionLayer, MeshData> meshes() {
        return meshes;
    }

    public MeshData.SortState translucentSortState() {
        return translucentSortState;
    }

    public Set<BlockPos> blockEntityPositions() {
        return blockEntityPositions;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        RuntimeException failure = null;
        for (MeshData mesh : meshes.values()) {
            try {
                mesh.close();
            } catch (RuntimeException exception) {
                if (failure == null) failure = exception;
            }
        }
        try {
            builders.close();
        } catch (RuntimeException exception) {
            if (failure == null) failure = exception;
        }
        if (failure != null) throw failure;
    }
}
