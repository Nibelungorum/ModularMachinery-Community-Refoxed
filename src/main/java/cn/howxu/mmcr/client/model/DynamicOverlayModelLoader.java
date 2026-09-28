package cn.howxu.mmcr.client.model;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.MachineAppearanceSpec;
import cn.howxu.mmcr.client.controller.ControllerSpecCache;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import com.google.common.collect.ImmutableList;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.NeoForgeRenderTypes;
import net.neoforged.neoforge.client.model.IDynamicBakedModel;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import net.neoforged.neoforge.client.model.geometry.IGeometryBakingContext;
import net.neoforged.neoforge.client.model.geometry.IGeometryLoader;
import net.neoforged.neoforge.client.model.geometry.IUnbakedGeometry;
import net.neoforged.neoforge.client.model.pipeline.QuadBakingVertexConsumer;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * 1.21.1 geometry loader and baked model for dynamic machine overlays.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class DynamicOverlayModelLoader implements IGeometryLoader<DynamicOverlayModelLoader.Unbaked> {
    public static final ResourceLocation CONTROLLER_ID = MMCR.id("dynamic_controller_overlay");
    public static final ResourceLocation PORT_ID = MMCR.id("dynamic_port_overlay");
    public static final DynamicOverlayModelLoader CONTROLLER = new DynamicOverlayModelLoader(DynamicOverlayBakedModel.Kind.CONTROLLER);
    public static final DynamicOverlayModelLoader PORT = new DynamicOverlayModelLoader(DynamicOverlayBakedModel.Kind.PORT);

    private static final ResourceLocation FALLBACK_TEXTURE = MMCR.id("block/basic_casing");
    private static final ModelProperty<CtmContext> CTM_CONTEXT = new ModelProperty<>();
    static final float OVERLAY_GROW = 0.0005f;
    private static final Set<ResourceLocation> MISSING_BASE_TEXTURES = ConcurrentHashMap.newKeySet();

    private final DynamicOverlayBakedModel.Kind kind;

    private DynamicOverlayModelLoader(DynamicOverlayBakedModel.Kind kind) {
        this.kind = kind;
    }

    @Override
    public Unbaked read(JsonObject json, JsonDeserializationContext context) {
        ResourceLocation blockId = json.has("block")
                ? ResourceLocation.parse(json.get("block").getAsString()) : null;
        return new Unbaked(kind, blockId);
    }

    static @Nullable MachineAppearanceSpec.TextureSource ctmSource(DynamicOverlayBakedModel.Kind kind,
                                                                    BlockState state, ModelData modelData) {
        if (kind == DynamicOverlayBakedModel.Kind.PORT) {
            MachineAppearanceSpec.TextureSource source = modelData.get(MachineModelDataKeys.PORT_TEXTURE_SOURCE);
            return Boolean.TRUE.equals(modelData.get(MachineModelDataKeys.PORT_LINKED))
                    && source != null && source.overrideTexture() == null ? source : null;
        }
        if (!state.getValue(MachineControllerBlock.FORMED)) {
            return null;
        }
        ResourceLocation machineId = machineId(state, modelData);
        MachineAppearanceSpec appearance = MachineAppearanceCache.specFor(machineId);
        return DynamicOverlayBakedModel.controllerCtmEligible(machineId, appearance,
                ControllerSpecCache.specFor(machineId)) ? appearance.controllerTextureSource() : null;
    }

    private static ResourceLocation machineId(BlockState state, ModelData modelData) {
        ResourceLocation modelDataId = modelData.get(MachineModelDataKeys.MACHINE_ID);
        if (modelDataId != null) {
            return modelDataId;
        }
        if (state.getBlock() instanceof MachineControllerBlock controller) {
            return controller.machineId();
        }
        return null;
    }

    private static ImmutableList<ResourceLocation> portOverlayTextures(BlockState state) {
        RuntimeBlockModelDefinition definition = RuntimeMachineModelRegistry.definition(state.getBlock());
        return definition == null
                ? ImmutableList.of(DynamicOverlayBakedModel.defaultPortOverlayTexture())
                : definition.itemDescription().overlayTextures();
    }

    static float overlayGrow(int index) {
        return OVERLAY_GROW * (index + 1);
    }

    static ImmutableList<OverlayLayer> overlayLayers(List<ResourceLocation> overlays,
                                                      @Nullable ResourceLocation stateOverlay) {
        ImmutableList.Builder<OverlayLayer> layers = ImmutableList.builder();
        for (int index = 0; index < overlays.size(); index++) {
            layers.add(new OverlayLayer(overlays.get(index), overlayGrow(index)));
        }
        if (stateOverlay != null) {
            layers.add(new OverlayLayer(stateOverlay, overlayGrow(overlays.size())));
        }
        return layers.build();
    }

    record OverlayLayer(ResourceLocation texture, float grow) {
    }

    static void clearMissingBaseTextureWarnings() {
        MISSING_BASE_TEXTURES.clear();
    }

    private static List<Vector3f> vertices(Direction direction, float grow) {
        float min = -grow;
        float max = 1.0f + grow;
        return switch (direction) {
            case EAST -> List.of(new Vector3f(max, max, max), new Vector3f(max, min, max), new Vector3f(max, min, min), new Vector3f(max, max, min));
            case WEST -> List.of(new Vector3f(min, max, min), new Vector3f(min, min, min), new Vector3f(min, min, max), new Vector3f(min, max, max));
            case UP -> List.of(new Vector3f(min, max, max), new Vector3f(max, max, max), new Vector3f(max, max, min), new Vector3f(min, max, min));
            case DOWN -> List.of(new Vector3f(min, min, min), new Vector3f(max, min, min), new Vector3f(max, min, max), new Vector3f(min, min, max));
            case SOUTH -> List.of(new Vector3f(min, max, max), new Vector3f(min, min, max), new Vector3f(max, min, max), new Vector3f(max, max, max));
            case NORTH -> List.of(new Vector3f(max, max, min), new Vector3f(max, min, min), new Vector3f(min, min, min), new Vector3f(min, max, min));
        };
    }

    static float[] uv(Direction direction, Direction rollFacing, Vector3f vertex) {
        if (direction.getAxis().isVertical()) {
            return switch (rollFacing) {
                case EAST -> new float[]{1.0f - vertex.z(), vertex.x()};
                case SOUTH -> new float[]{vertex.x(), vertex.z()};
                case WEST -> new float[]{vertex.z(), 1.0f - vertex.x()};
                default -> new float[]{vertex.x(), vertex.z()};
            };
        }
        return new float[]{u(direction, vertex), v(direction, vertex)};
    }

    private static float u(Direction direction, Vector3f vertex) {
        return switch (direction) {
            case EAST -> 1.0f - vertex.z();
            case WEST -> vertex.z();
            case NORTH -> 1.0f - vertex.x();
            default -> vertex.x();
        };
    }

    private static float v(Direction direction, Vector3f vertex) {
        return direction.getAxis().isVertical() ? vertex.z() : 1.0f - vertex.y();
    }

    public record Unbaked(DynamicOverlayBakedModel.Kind kind,
                          @Nullable ResourceLocation itemBlockId) implements IUnbakedGeometry<Unbaked> {
        @Override
        public BakedModel bake(IGeometryBakingContext context, ModelBaker baker,
                               Function<Material, TextureAtlasSprite> spriteGetter,
                               ModelState modelState, ItemOverrides overrides) {
            DynamicOverlayItemModel.Description description = null;
            if (itemBlockId != null) {
                Block block = BuiltInRegistries.BLOCK.get(itemBlockId);
                description = block == null ? DynamicOverlayItemModel.Description.staticItem()
                        : DynamicOverlayItemModel.describeBlock(block);
            }
            return new DynamicModel(kind, description, spriteGetter, context.getTransforms(), overrides);
        }
    }

    private record CtmContext(BakedModel model, BlockState state, ModelData data) {
    }

    private static final class DynamicModel implements IDynamicBakedModel {
        private final DynamicOverlayBakedModel.Kind kind;
        private final @Nullable DynamicOverlayItemModel.Description itemDescription;
        private final Function<Material, TextureAtlasSprite> spriteGetter;
        private final ItemTransforms transforms;
        private final ItemOverrides overrides;
        private final TextureAtlasSprite particle;

        private DynamicModel(DynamicOverlayBakedModel.Kind kind,
                             @Nullable DynamicOverlayItemModel.Description itemDescription,
                             Function<Material, TextureAtlasSprite> spriteGetter,
                             ItemTransforms transforms, ItemOverrides overrides) {
            this.kind = kind;
            this.itemDescription = itemDescription;
            this.spriteGetter = spriteGetter;
            this.transforms = transforms;
            this.overrides = overrides;
            this.particle = sprite(FALLBACK_TEXTURE);
        }

        @Override
        public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData modelData) {
            MachineAppearanceSpec.TextureSource source = ctmSource(kind, state, modelData);
            var sourceState = DynamicOverlayBakedModel.sourceState(source, level, pos);
            if (sourceState.isEmpty()) {
                return modelData;
            }
            BlockState appearance = sourceState.get();
            BakedModel sourceModel = Minecraft.getInstance().getModelManager()
                    .getBlockModelShaper().getBlockModel(appearance);
            ModelData sourceData = sourceModel.getModelData(level, pos, appearance, modelData);
            return modelData.derive().with(CTM_CONTEXT, new CtmContext(sourceModel, appearance, sourceData)).build();
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random,
                                        ModelData modelData, @Nullable RenderType renderType) {
            if (state == null) {
                return itemQuads(side);
            }

            List<BakedQuad> quads = new ArrayList<>();
            CtmContext ctm = modelData.get(CTM_CONTEXT);
            if (ctm != null) {
                quads.addAll(ctm.model().getQuads(ctm.state(), side, random, ctm.data(), renderType));
            } else if (side != null) {
                DynamicOverlayBakedModel.TextureSet textures = textures(state, modelData);
                quads.add(face(side, Direction.NORTH, baseSprite(textures.base().forFace(side)), 0.0f));
            }
            if (side == null) {
                addBlockOverlays(quads, state, modelData);
            }
            return quads;
        }

        private List<BakedQuad> itemQuads(@Nullable Direction side) {
            if (side != null || itemDescription == null || itemDescription.kind() == null) {
                return List.of();
            }
            DynamicOverlayBakedModel.FaceTextures base = DynamicOverlayBakedModel.resolveBase(
                    itemDescription.baseTextureSource());
            List<BakedQuad> quads = new ArrayList<>();
            for (Direction direction : Direction.values()) {
                quads.add(face(direction, Direction.NORTH, baseSprite(base.forFace(direction)), 0.0f));
            }
            for (OverlayLayer layer : overlayLayers(itemDescription.overlayTextures(),
                    itemDescription.stateOverlayTexture())) {
                for (Direction direction : itemDescription.overlayFaces()) {
                    quads.add(face(direction, Direction.NORTH, sprite(layer.texture()), layer.grow()));
                }
            }
            return quads;
        }

        private void addBlockOverlays(List<BakedQuad> quads, BlockState state, ModelData modelData) {
            DynamicOverlayBakedModel.TextureSet textures = textures(state, modelData);
            Direction overlayFace = kind == DynamicOverlayBakedModel.Kind.CONTROLLER
                    && state.hasProperty(MachineControllerBlock.FACING)
                    ? state.getValue(MachineControllerBlock.FACING) : null;
            Direction rollFacing = kind == DynamicOverlayBakedModel.Kind.CONTROLLER
                    && state.hasProperty(MachineControllerBlock.ROLL_FACING)
                    ? state.getValue(MachineControllerBlock.ROLL_FACING) : Direction.NORTH;
            ResourceLocation stateOverlay = kind == DynamicOverlayBakedModel.Kind.CONTROLLER
                    ? DynamicOverlayBakedModel.controllerStateOverlay(machineId(state, modelData),
                    state.getValue(MachineControllerBlock.ACTIVE)) : null;
            for (OverlayLayer layer : overlayLayers(textures.overlays(), stateOverlay)) {
                for (Direction direction : Direction.values()) {
                    if (overlayFace == null || direction == overlayFace) {
                        quads.add(face(direction, rollFacing, sprite(layer.texture()), layer.grow()));
                    }
                }
            }
        }

        private DynamicOverlayBakedModel.TextureSet textures(BlockState state, ModelData modelData) {
            ResourceLocation id = machineId(state, modelData);
            if (kind == DynamicOverlayBakedModel.Kind.CONTROLLER) {
                return DynamicOverlayBakedModel.controllerTextures(id);
            }
            return DynamicOverlayBakedModel.portTextures(id,
                    modelData.get(MachineModelDataKeys.PORT_TEXTURE_SOURCE), portOverlayTextures(state));
        }

        private TextureAtlasSprite sprite(ResourceLocation texture) {
            return spriteGetter.apply(new Material(TextureAtlas.LOCATION_BLOCKS, texture));
        }

        private TextureAtlasSprite baseSprite(ResourceLocation texture) {
            TextureAtlasSprite sprite = sprite(texture);
            if (!sprite.contents().name().equals(MissingTextureAtlasSprite.getLocation())) {
                return sprite;
            }
            if (MISSING_BASE_TEXTURES.add(texture)) {
                MMCR.LOG.warn("Missing dynamic base texture {}; using fallback", texture);
            }
            return sprite(FALLBACK_TEXTURE);
        }

        private BakedQuad face(Direction direction, Direction rollFacing, TextureAtlasSprite sprite,
                               float grow) {
            QuadBakingVertexConsumer builder = new QuadBakingVertexConsumer();
            builder.setSprite(sprite);
            builder.setDirection(direction);
            builder.setShade(true);
            Vec3i normal = direction.getNormal();
            for (Vector3f vertex : vertices(direction, grow)) {
                float[] uv = uv(direction, rollFacing, vertex);
                builder.addVertex(vertex.x(), vertex.y(), vertex.z());
                builder.setColor(255, 255, 255, 255);
                builder.setNormal(normal.getX(), normal.getY(), normal.getZ());
                builder.setUv(sprite.getU(uv[0]), sprite.getV(uv[1]));
            }
            return builder.bakeQuad();
        }

        @Override
        public boolean useAmbientOcclusion() {
            return true;
        }

        @Override
        public boolean isGui3d() {
            return true;
        }

        @Override
        public boolean usesBlockLight() {
            return true;
        }

        @Override
        public boolean isCustomRenderer() {
            return false;
        }

        @Override
        public TextureAtlasSprite getParticleIcon() {
            return particle;
        }

        @Override
        public ItemTransforms getTransforms() {
            return transforms;
        }

        @Override
        public ItemOverrides getOverrides() {
            return overrides;
        }

        @Override
        public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource random, ModelData data) {
            return ChunkRenderTypeSet.of(RenderType.translucent());
        }

        @Override
        public List<RenderType> getRenderTypes(ItemStack stack, boolean fabulous) {
            return List.of(NeoForgeRenderTypes.ITEM_UNSORTED_TRANSLUCENT.get());
        }
    }
}
