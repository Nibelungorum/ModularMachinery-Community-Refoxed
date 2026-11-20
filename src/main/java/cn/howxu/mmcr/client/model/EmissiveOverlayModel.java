package cn.howxu.mmcr.client.model;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.IQuadTransformer;
import net.neoforged.neoforge.client.model.QuadTransformers;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.geometry.IGeometryBakingContext;
import net.neoforged.neoforge.client.model.geometry.IGeometryLoader;
import net.neoforged.neoforge.client.model.geometry.IUnbakedGeometry;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Relights only the overlay quads, after the child model has selected its connected textures.
 *
 * @author howxu <dev@howxu.cn>
 */
final class EmissiveOverlayModel extends BakedModelWrapper<BakedModel> {
    static final IGeometryLoader<Unbaked> LOADER = EmissiveOverlayModel::read;
    private final IQuadTransformer light;
    private final boolean shade;

    EmissiveOverlayModel(BakedModel model, int lightEmission, boolean shade) {
        super(model);
        this.light = QuadTransformers.settingEmissivity(lightEmission);
        this.shade = shade;
    }

    private static Unbaked read(JsonObject json, JsonDeserializationContext context) {
        int light = GsonHelper.getAsInt(json, "light_emission");
        if (light < 0 || light > 15) throw new JsonParseException("light_emission must be between 0 and 15");
        boolean shade = GsonHelper.getAsBoolean(json, "shade");
        BlockModel child = context.deserialize(GsonHelper.getAsJsonObject(json, "model"), BlockModel.class);
        return new Unbaked(child, light, shade);
    }

    record Unbaked(BlockModel child, int lightEmission, boolean shade) implements IUnbakedGeometry<Unbaked> {
        @Override
        public void resolveParents(Function<ResourceLocation, UnbakedModel> modelGetter, IGeometryBakingContext context) {
            child.resolveParents(modelGetter);
        }

        @Override
        public BakedModel bake(IGeometryBakingContext context, ModelBaker baker, Function<Material, TextureAtlasSprite> sprites,
                               ModelState state, ItemOverrides overrides) {
            return new EmissiveOverlayModel(child.bake(baker, sprites, state), lightEmission, shade);
        }
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random,
                                    ModelData data, @Nullable RenderType renderType) {
        return relight(originalModel.getQuads(state, side, random, data, renderType));
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random) {
        return relight(originalModel.getQuads(state, side, random));
    }

    private List<BakedQuad> relight(List<BakedQuad> quads) {
        if (quads.isEmpty()) return quads;
        List<BakedQuad> result = new ArrayList<>(quads.size());
        for (BakedQuad quad : quads) {
            var copy = new BakedQuad(quad.getVertices().clone(), quad.getTintIndex(), quad.getDirection(),
                    quad.getSprite(), shade, quad.hasAmbientOcclusion());
            light.processInPlace(copy);
            result.add(copy);
        }
        return result;
    }
}
