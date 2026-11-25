package cn.howxu.mmcr.client.model;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.model.IQuadTransformer;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.List;

/**
 * Decorates only the quads emitted in the base model's cull bucket. Each layer keeps its atlas sprite.
 *
 * @author HowXu {@literal <dev@howxu.cn>}
 */
record WorldFaceLayers(@Nullable Direction front, Direction roll, List<Layer> layers) {
    static final WorldFaceLayers BASE_ONLY = new WorldFaceLayers(null, Direction.NORTH, List.of());

    /** Block-space UVs, independent of the geometry in a CTM tile.
     * @author HowXu {@literal <dev@howxu.cn>}
     */
    @FunctionalInterface
    interface Projection {
        float[] uv(Direction face, Direction roll, Vector3f point);
    }

    record Layer(TextureAtlasSprite sprite, RenderType pass, int tintIndex, Projection projection) {}

    static RenderType later(RenderType a, RenderType b) {
        return a.getChunkLayerId() >= b.getChunkLayerId() ? a : b;
    }

    boolean rendersIn(RenderType basePass, @Nullable RenderType requested) {
        if (requested == null || requested == basePass) return true;
        for (Layer layer : layers) {
            if (later(basePass, layer.pass()) == requested) return true;
        }
        return false;
    }

    void emit(List<BakedQuad> result, BakedQuad base, RenderType basePass, @Nullable RenderType requested) {
        if (requested == null || requested == basePass) result.add(base);
        if (front != null && base.getDirection() != front) return;
        float[][] uv = null;
        Projection projection = null;
        for (Layer layer : layers) {
            if (requested != null && later(basePass, layer.pass()) != requested) continue;
            var sprite = layer.sprite();
            if (projection != layer.projection()) {
                projection = layer.projection();
                uv = project(base, projection);
            }
            int[] data = base.getVertices().clone();
            for (int i = 0; i < 4; i++) {
                int offset = i * IQuadTransformer.STRIDE;
                data[offset + IQuadTransformer.COLOR] = -1;
                data[offset + IQuadTransformer.UV0] = Float.floatToRawIntBits(sprite.getU(Mth.lerp(sprite.uvShrinkRatio(), uv[i][0], .5F)));
                data[offset + IQuadTransformer.UV0 + 1] = Float.floatToRawIntBits(sprite.getV(Mth.lerp(sprite.uvShrinkRatio(), uv[i][1], .5F)));
            }
            result.add(new BakedQuad(data, layer.tintIndex(), base.getDirection(), sprite, base.isShade(), base.hasAmbientOcclusion()));
        }
    }

    private float[][] project(BakedQuad base, Projection projection) {
        // Use block coordinates, not a CTM tile's own (possibly rotated) UVs.
        int[] data = base.getVertices();
        float[][] uv = new float[4][];
        var point = new Vector3f();
        for (int i = 0; i < 4; i++) {
            int pos = i * IQuadTransformer.STRIDE + IQuadTransformer.POSITION;
            for (int j = 0; j < 3; j++) point.setComponent(j, Mth.clamp(Float.intBitsToFloat(data[pos + j]), 0F, 1F));
            uv[i] = projection.uv(base.getDirection(), roll, point);
        }
        return uv;
    }
}
