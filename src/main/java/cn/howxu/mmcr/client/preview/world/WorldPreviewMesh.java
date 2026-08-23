package cn.howxu.mmcr.client.preview.world;

import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.core.BlockPos;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
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
    private final Map<ChunkSectionLayer, MeshData> meshes;
    private final MeshData.SortState translucentSortState;
    private final Set<BlockPos> blockEntityPositions;
    private boolean closed;

    WorldPreviewMesh(AutoCloseable builders, Map<ChunkSectionLayer, MeshData> meshes,
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

    void replay(ChunkSectionLayer layer, PoseStack.Pose pose, VertexConsumer output) {
        MeshData mesh = meshes.get(layer);
        if (mesh == null) return;

        VertexFormat format = mesh.drawState().format();
        ByteBuffer vertices = mesh.vertexBuffer().duplicate().order(ByteOrder.nativeOrder());
        int stride = format.getVertexSize();
        for (int base = vertices.position(); base + stride <= vertices.limit(); base += stride) {
            VertexConsumer vertex = output.addVertex(pose,
                    vertices.getFloat(base + format.getOffset(VertexFormatElement.POSITION)),
                    vertices.getFloat(base + format.getOffset(VertexFormatElement.POSITION) + Float.BYTES),
                    vertices.getFloat(base + format.getOffset(VertexFormatElement.POSITION) + Float.BYTES * 2));
            for (VertexFormatElement element : format.getElements()) {
                int offset = base + format.getOffset(element);
                if (element.equals(VertexFormatElement.POSITION)) continue;
                if (element.equals(VertexFormatElement.COLOR)) {
                    vertex.setColor(vertices.getInt(offset));
                } else if (element.equals(VertexFormatElement.UV0)) {
                    vertex.setUv(vertices.getFloat(offset), vertices.getFloat(offset + Float.BYTES));
                } else if (element.equals(VertexFormatElement.UV1)) {
                    vertex.setUv1(vertices.getShort(offset), vertices.getShort(offset + Short.BYTES));
                } else if (element.equals(VertexFormatElement.UV2)) {
                    vertex.setUv2(vertices.getShort(offset), vertices.getShort(offset + Short.BYTES));
                } else if (element.equals(VertexFormatElement.NORMAL)) {
                    vertex.setNormal(vertices.get(offset) / 127.0F,
                            vertices.get(offset + 1) / 127.0F,
                            vertices.get(offset + 2) / 127.0F);
                } else if (element.equals(VertexFormatElement.LINE_WIDTH)) {
                    vertex.setLineWidth(vertices.getFloat(offset));
                }
            }
        }
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        java.util.List<AutoCloseable> resources = new java.util.ArrayList<>(meshes.values());
        resources.add(builders);
        WorldPreviewMeshCompiler.closeResources(resources);
    }
}
