package cn.howxu.mmcr.client.model;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.util.TriState;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.DelegateBlockStateModel;
import net.neoforged.neoforge.client.model.block.CustomUnbakedBlockStateModel;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Changes face lighting after the child model has selected its world-dependent geometry.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class EmissiveOverlayModel extends DelegateBlockStateModel {
    private final int lightEmission;
    private final boolean shade;

    EmissiveOverlayModel(BlockStateModel model, int lightEmission, boolean shade) {
        super(model);
        this.lightEmission = lightEmission;
        this.shade = shade;
    }

    public record Unbaked(int lightEmission, boolean shade, BlockStateModel.Unbaked model) implements CustomUnbakedBlockStateModel {
        public static final MapCodec<Unbaked> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.intRange(0, 15).fieldOf("light_emission").forGetter(Unbaked::lightEmission),
                Codec.BOOL.fieldOf("shade").forGetter(Unbaked::shade),
                BlockStateModel.Unbaked.CODEC.fieldOf("model").forGetter(Unbaked::model)
        ).apply(i, Unbaked::new));

        @Override
        public MapCodec<Unbaked> codec() {
            return CODEC;
        }

        @Override
        public void resolveDependencies(Resolver resolver) {
            model.resolveDependencies(resolver);
        }

        @Override
        public BlockStateModel bake(ModelBaker baker) {
            return new EmissiveOverlayModel(model.bake(baker), lightEmission, shade);
        }
    }

    @Override
    public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, List<BlockStateModelPart> parts) {
        int first = parts.size();
        delegate.collectParts(level, pos, state, random, parts);
        wrapNewParts(parts, first);
    }

    @Override
    public void collectParts(RandomSource random, List<BlockStateModelPart> parts) {
        int first = parts.size();
        delegate.collectParts(random, parts);
        wrapNewParts(parts, first);
    }

    private void wrapNewParts(List<BlockStateModelPart> parts, int first) {
        for (int i = first; i < parts.size(); i++) {
            parts.set(i, new Part(parts.get(i), lightEmission, shade));
        }
    }

    @Override
    public @Nullable Object createGeometryKey(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random) {
        Object key = delegate.createGeometryKey(level, pos, state, random);
        return key == null ? null : new GeometryKey(this, key);
    }

    private record GeometryKey(EmissiveOverlayModel overlay, Object child) {}

    private record Part(BlockStateModelPart child, int lightEmission, boolean shade) implements BlockStateModelPart {
        @Override
        public List<BakedQuad> getQuads(@Nullable Direction direction) {
            List<BakedQuad> quads = child.getQuads(direction);
            if (quads.isEmpty()) return quads;
            List<BakedQuad> result = new ArrayList<>(quads.size());
            for (BakedQuad q : quads) {
                var m = q.materialInfo();
                if (m.shade() == shade && m.lightEmission() == lightEmission) {
                    result.add(q);
                } else {
                    var lit = new BakedQuad.MaterialInfo(m.sprite(), m.layer(), m.itemRenderType(), m.tintIndex(),
                            shade, lightEmission, m.ambientOcclusion());
                    result.add(new BakedQuad(q.position0(), q.position1(), q.position2(), q.position3(),
                            q.packedUV0(), q.packedUV1(), q.packedUV2(), q.packedUV3(), q.direction(),
                            lit, q.bakedNormals(), q.bakedColors()));
                }
            }
            return result;
        }

        @Override
        public boolean useAmbientOcclusion() {
            return child.useAmbientOcclusion();
        }

        @Override
        public TriState ambientOcclusion() {
            return child.ambientOcclusion();
        }

        @Override
        public Material.Baked particleMaterial() {
            return child.particleMaterial();
        }

        @Override
        public int materialFlags() {
            return child.materialFlags();
        }
    }
}
