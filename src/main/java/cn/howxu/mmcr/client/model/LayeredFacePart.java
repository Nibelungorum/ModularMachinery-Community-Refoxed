package cn.howxu.mmcr.client.model;

import com.mojang.blaze3d.platform.Transparency;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.Direction;
import net.minecraft.util.TriState;
import net.neoforged.neoforge.client.model.quad.BakedColors;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Adds coplanar, independently animated sprites only to faces the base model actually emits.
 *
 * @author HowXu {@literal <dev@howxu.cn>}
 */
record LayeredFacePart(BlockStateModelPart base, @Nullable Direction front, Direction roll,
                       List<Material.Baked> overlays) implements BlockStateModelPart {
    @Override
    public List<BakedQuad> getQuads(@Nullable Direction bucket) {
        var quads = base.getQuads(bucket);
        if (quads.isEmpty() || (front != null && quads.stream().noneMatch(q -> q.direction() == front))) return quads;

        var result = new ArrayList<BakedQuad>(quads.size() * (1 + overlays.size()));
        for (var quad : quads) {
            result.add(quad);
            if (front == null || quad.direction() == front) addOverlays(result, quad);
        }
        return result;
    }

    private void addOverlays(List<BakedQuad> result, BakedQuad quad) {
        var face = quad.direction();
        // Project from block space, not the CTM tile's own (possibly rotated) UVs.
        var uv0 = DynamicOverlayModelLoader.uv(face, roll, quad.position0());
        var uv1 = DynamicOverlayModelLoader.uv(face, roll, quad.position1());
        var uv2 = DynamicOverlayModelLoader.uv(face, roll, quad.position2());
        var uv3 = DynamicOverlayModelLoader.uv(face, roll, quad.position3());

        var baseInfo = quad.materialInfo();
        var transparency = switch (baseInfo.layer()) {
            case SOLID -> Transparency.NONE;
            case CUTOUT -> Transparency.TRANSPARENT;
            case TRANSLUCENT -> Transparency.TRANSLUCENT;
        };
        for (var material : overlays) {
            var sprite = material.sprite();
            // Terrain draws SOLID, CUTOUT, then TRANSLUCENT. Never draw a later overlay in an earlier pass.
            transparency = transparency.or(material.forceTranslucent() ? Transparency.TRANSLUCENT : sprite.transparency());
            var info = BakedQuad.MaterialInfo.of(material, transparency, -1, baseInfo.shade(),
                    baseInfo.lightEmission(), baseInfo.ambientOcclusion());
            result.add(new BakedQuad(quad.position0(), quad.position1(), quad.position2(), quad.position3(),
                    pack(sprite, uv0), pack(sprite, uv1), pack(sprite, uv2), pack(sprite, uv3),
                    face, info, quad.bakedNormals(), BakedColors.DEFAULT));
        }
    }

    private static long pack(TextureAtlasSprite sprite, float[] uv) {
        return UVPair.pack(sprite.getU(uv[0]), sprite.getV(uv[1]));
    }

    @Override public boolean useAmbientOcclusion() { return base.useAmbientOcclusion(); }
    @Override public TriState ambientOcclusion() { return base.ambientOcclusion(); }
    @Override public Material.Baked particleMaterial() { return base.particleMaterial(); }

    @Override
    public int materialFlags() {
        int flags = base.materialFlags();
        for (var material : overlays) {
            if (material.forceTranslucent() || material.sprite().transparency().hasTranslucent()) flags |= BakedQuad.FLAG_TRANSLUCENT;
            if (material.sprite().contents().isAnimated()) flags |= BakedQuad.FLAG_ANIMATED;
        }
        return flags;
    }
}
