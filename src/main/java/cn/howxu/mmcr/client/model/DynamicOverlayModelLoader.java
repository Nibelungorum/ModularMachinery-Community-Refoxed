package cn.howxu.mmcr.client.model;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.MachineAppearanceSpec;
import cn.howxu.mmcr.client.controller.ControllerSpecCache;
import cn.howxu.mmcr.compat.athena.AthenaModelBridge;
import cn.howxu.mmcr.compat.fluxnetworks.FluxNetworkOverlay;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.internal.port.MachinePort;
import com.mojang.blaze3d.platform.NativeImage;
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
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.NeoForgeRenderTypes;
import net.neoforged.neoforge.client.model.IDynamicBakedModel;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import net.neoforged.neoforge.client.model.geometry.IGeometryBakingContext;
import net.neoforged.neoforge.client.model.geometry.IGeometryLoader;
import net.neoforged.neoforge.client.model.geometry.IUnbakedGeometry;
import net.neoforged.neoforge.client.model.pipeline.QuadBakingVertexConsumer;
import net.neoforged.neoforge.common.util.TriState;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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

    private static final ResourceLocation FALLBACK_TEXTURE = MMCR.id("block/ctm/basic_casing/particle");
    private static final ModelProperty<CtmContext> CTM_CONTEXT = new ModelProperty<>();
    private static final ModelProperty<WorldAppearance> WORLD_APPEARANCE = new ModelProperty<>();
    private static final FaceLayers.Projection PANEL_UV = DynamicOverlayModelLoader::uv;
    private static final FaceLayers.Projection FLUX_UV = (face, roll, point) -> FluxNetworkOverlay.uv(face, point);
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

    static void clearMissingBaseTextureWarnings() {
        MISSING_BASE_TEXTURES.clear();
    }

    private static List<Vector3f> vertices(Direction direction) {
        float min = 0.0f;
        float max = 1.0f;
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
            boolean upward = direction == Direction.UP;
            return switch (rollFacing) {
                case EAST -> new float[]{upward ? vertex.z() : 1.0f - vertex.z(), 1.0f - vertex.x()};
                case SOUTH -> new float[]{upward ? 1.0f - vertex.x() : vertex.x(), 1.0f - vertex.z()};
                case WEST -> new float[]{upward ? 1.0f - vertex.z() : vertex.z(), vertex.x()};
                default -> new float[]{upward ? vertex.x() : 1.0f - vertex.x(), vertex.z()};
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
            BakedModel shaft = description != null && DynamicOverlayTextures.isStressPort(description.portKind())
                    ? baker.bake(ResourceLocation.parse("create:block/shaft"), BlockModelRotation.X90_Y0) : null;
            TextureAtlasSprite particle = spriteGetter.apply(new Material(TextureAtlas.LOCATION_BLOCKS, FALLBACK_TEXTURE));
            // Runtime lookups must not feed CTM's baking-time texture scraper for this shared model.
            return new DynamicModel(kind, description, particle,
                    texture -> Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(texture),
                    context.getTransforms(), overrides, shaft);
        }
    }

    private record WorldAppearance(DynamicOverlayBakedModel.FaceTextures base, FaceLayers layers) {}

    private record CtmContext(BakedModel model, BlockState state, ModelData data, ChunkRenderTypeSet renderTypes, long seed) {
        List<BakedQuad> quads(@Nullable Direction side, @Nullable RenderType pass) {
            // Replay the same weighted variant when another pass needs this face for its overlays.
            return model.getQuads(state, side, RandomSource.create(seed), data, pass);
        }
    }

    static final class DynamicModel implements IDynamicBakedModel {
        private final DynamicOverlayBakedModel.Kind kind;
        private final @Nullable DynamicOverlayItemModel.Description itemDescription;
        private final Function<ResourceLocation, TextureAtlasSprite> runtimeSpriteGetter;
        private final ItemTransforms transforms;
        private final ItemOverrides overrides;
        private final TextureAtlasSprite particle;
        private final List<BakedModel> itemPasses;
        private final @Nullable BakedModel itemShaft;
        private final Map<TextureAtlasSprite, RenderType> overlayRenderTypes = new ConcurrentHashMap<>();

        DynamicModel(DynamicOverlayBakedModel.Kind kind,
                     @Nullable DynamicOverlayItemModel.Description itemDescription,
                     TextureAtlasSprite particle,
                     Function<ResourceLocation, TextureAtlasSprite> runtimeSpriteGetter,
                     ItemTransforms transforms, ItemOverrides overrides, @Nullable BakedModel itemShaft) {
            this.kind = kind;
            this.itemDescription = itemDescription;
            this.runtimeSpriteGetter = runtimeSpriteGetter;
            this.transforms = transforms;
            this.overrides = overrides;
            this.itemShaft = itemShaft;
            this.particle = particle;
            this.itemPasses = List.of(new ItemPass(this, RenderType.solid()),
                    new ItemPass(this, RenderType.cutout()), new ItemPass(this, RenderType.translucent()));
        }

        @Override
        public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData modelData) {
            boolean easterEgg = kind == DynamicOverlayBakedModel.Kind.CONTROLLER
                    && ControllerIdleEasterEggManager.trackAndIsActive(level, pos, state, machineId(state, modelData));
            var sourceState = DynamicOverlayBakedModel.sourceState(ctmSource(kind, state, modelData), level, pos);
            var client = Minecraft.getInstance();
            if (sourceState.isPresent() && client != null && client.getModelManager() != null) {
                var appearance = sourceState.get();
                var source = client.getModelManager().getBlockModelShaper().getBlockModel(appearance);
                modelData = withCtmSource(source, level, pos, appearance, modelData, state.getSeed(pos));
            }
            // Resolve the current atlas once for this render, not for each of its cull buckets and passes.
            return modelData.derive().with(WORLD_APPEARANCE, worldAppearance(state, modelData, easterEgg)).build();
        }

        static ModelData withCtmSource(BakedModel source, BlockAndTintGetter level, BlockPos pos, BlockState appearance,
                                       ModelData data, long seed) {
            if (source instanceof DynamicModel) return data;
            var bridge = AthenaModelBridge.get();
            var sourceData = bridge.available() ? bridge.modelData(source, level, pos, appearance, data) : null;
            // Other 1.21.1 models (including CTM) can supply geometry without Athena.
            if (sourceData == null) sourceData = source.getModelData(level, pos, appearance, data);
            var passes = source.getRenderTypes(appearance, RandomSource.create(seed), sourceData);
            return data.derive().with(CTM_CONTEXT, new CtmContext(source, appearance, sourceData, passes, seed)).build();
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random,
                                        ModelData modelData, @Nullable RenderType renderType) {
            if (state == null) {
                return itemQuads(side, renderType);
            }

            Direction.Axis shaftAxis = stressAxis(state);
            CtmContext ctm = modelData.get(CTM_CONTEXT);
            if (ctm == null && (side == null ? shaftAxis == null : side.getAxis() == shaftAxis)) return List.of();

            var appearance = modelData.get(WORLD_APPEARANCE);
            if (appearance == null) appearance = worldAppearance(state, modelData, false);
            var layers = appearance.layers();
            var casing = shaftAxis != null || (ctm == null && layers.front() != null && side != layers.front())
                    ? FaceLayers.BASE_ONLY : layers;
            List<BakedQuad> quads = new ArrayList<>();
            var solid = RenderType.solid();
            if (ctm != null) {
                // A null query asks the source for all geometry in this bucket, not once per render pass.
                Iterable<RenderType> passes = renderType == null ? Collections.singletonList(null) : ctm.renderTypes();
                for (var pass : passes) {
                    var basePass = pass == null ? solid : pass;
                    if (!casing.rendersIn(basePass, renderType)) continue;
                    for (var quad : ctm.quads(side, pass)) {
                        if (quad.getDirection().getAxis() != shaftAxis) casing.emit(quads, quad, basePass, renderType);
                    }
                }
            } else if (side != null && casing.rendersIn(solid, renderType)) {
                casing.emit(quads, baseFace(side, baseSprite(appearance.base().forFace(side))), solid, renderType);
            }
            if (shaftAxis != null && side == null && layers.rendersIn(solid, renderType)) {
                for (Direction direction : Direction.values()) {
                    if (direction.getAxis() != shaftAxis) continue;
                    var sprite = baseSprite(appearance.base().forFace(direction));
                    if (renderType == null || renderType == solid) addShaftRim(quads, direction, sprite);
                    layers.emit(quads, shaftPanel(direction, sprite), solid, renderType);
                }
            }
            return quads;
        }

        private List<BakedQuad> itemQuads(@Nullable Direction side, @Nullable RenderType renderType) {
            if (side != null || itemDescription == null || itemDescription.kind() == null) {
                return List.of();
            }
            DynamicOverlayItemModel.Description description = itemDescription.kind()
                    == DynamicOverlayBakedModel.Kind.CONTROLLER
                    ? DynamicOverlayItemModel.Description.controller(itemDescription.machineId()) : itemDescription;
            DynamicOverlayBakedModel.FaceTextures base = DynamicOverlayBakedModel.resolveBase(
                    description.baseTextureSource());
            var layers = faceLayers(null, Direction.NORTH, description.overlayTextures(), description.stateOverlayTexture());
            var solid = RenderType.solid();
            boolean drawBase = renderType == null || renderType == solid;
            // NeoForge selects the pass per model, so keep each face's layers together within that pass.
            List<BakedQuad> quads = new ArrayList<>();
            for (Direction direction : Direction.values()) {
                boolean shaftEnd = itemShaft != null && direction.getAxis() == Direction.Axis.Z;
                var casing = description.overlayFaces().contains(direction) && (itemShaft == null || shaftEnd)
                        ? layers : FaceLayers.BASE_ONLY;
                if (!casing.rendersIn(solid, renderType)) continue;
                var sprite = baseSprite(base.forFace(direction));
                if (shaftEnd && drawBase) addShaftRim(quads, direction, sprite);
                casing.emit(quads, shaftEnd ? shaftPanel(direction, sprite) : baseFace(direction, sprite), solid, renderType);
            }
            if (itemShaft != null && drawBase) {
                quads.addAll(itemShaft.getQuads(null, null, RandomSource.create()));
                for (Direction direction : Direction.values()) {
                    quads.addAll(itemShaft.getQuads(null, direction, RandomSource.create()));
                }
            }
            return quads;
        }

        private WorldAppearance worldAppearance(BlockState state, ModelData modelData, boolean easterEgg) {
            var textures = textures(state, modelData);
            Direction overlayFace = kind == DynamicOverlayBakedModel.Kind.CONTROLLER
                    && state.hasProperty(MachineControllerBlock.FACING)
                    ? state.getValue(MachineControllerBlock.FACING) : null;
            Direction rollFacing = kind == DynamicOverlayBakedModel.Kind.CONTROLLER
                    && state.hasProperty(MachineControllerBlock.ROLL_FACING)
                    ? state.getValue(MachineControllerBlock.ROLL_FACING) : Direction.NORTH;
            ResourceLocation stateOverlay = kind == DynamicOverlayBakedModel.Kind.CONTROLLER
                    ? DynamicOverlayBakedModel.controllerStateOverlay(machineId(state, modelData),
                    state.getValue(MachineControllerBlock.ACTIVE), easterEgg) : null;
            return new WorldAppearance(textures.base(), faceLayers(overlayFace, rollFacing, textures.overlays(), stateOverlay));
        }

        FaceLayers faceLayers(@Nullable Direction front, Direction roll, List<ResourceLocation> overlays,
                              @Nullable ResourceLocation stateOverlay) {
            if (stateOverlay != null) overlays = ImmutableList.<ResourceLocation>builder().addAll(overlays).add(stateOverlay).build();
            var layers = new ArrayList<FaceLayers.Layer>(overlays.size());
            var pass = RenderType.solid();
            for (var texture : overlays) {
                var core = FluxNetworkOverlay.forTexture(texture);
                var sprite = sprite(core == null ? texture : core.worldSprite());
                // A later overlay cannot be submitted before a translucent layer underneath it.
                pass = FaceLayers.later(pass, overlayRenderType(sprite));
                layers.add(new FaceLayers.Layer(sprite, pass, core == null ? -1 : core.tintIndex(),
                        core == null ? PANEL_UV : FLUX_UV));
            }
            return new FaceLayers(front, roll, layers);
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
            return runtimeSpriteGetter.apply(texture);
        }

        private RenderType overlayRenderType(TextureAtlasSprite atlasSprite) {
            // 26.1 chooses the layer per sprite; cache the backported alpha scan per baked model.
            return overlayRenderTypes.computeIfAbsent(atlasSprite, sprite -> {
                NativeImage image = sprite.contents().getOriginalImage();
                boolean transparent = false;
                for (int y = 0; y < image.getHeight(); y++) {
                    for (int x = 0; x < image.getWidth(); x++) {
                        int alpha = image.getPixelRGBA(x, y) >>> 24;
                        if (alpha != 0 && alpha != 255) return RenderType.translucent();
                        transparent |= alpha == 0;
                    }
                }
                return transparent ? RenderType.cutout() : RenderType.solid();
            });
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

        private BakedQuad baseFace(Direction direction, TextureAtlasSprite sprite) {
            return face(direction, sprite, vertices(direction));
        }

        private static @Nullable Direction.Axis stressAxis(BlockState state) {
            return state.getBlock() instanceof MachinePort port && DynamicOverlayTextures.isStressPort(port.kind())
                    ? state.getValue(BlockStateProperties.AXIS) : null;
        }

        private void addShaftRim(List<BakedQuad> quads, Direction direction, TextureAtlasSprite sprite) {
            quads.add(endFace(direction, sprite, 0F, 0F, 1F, 0.125F, 0F));
            quads.add(endFace(direction, sprite, 0F, 0.875F, 1F, 1F, 0F));
            quads.add(endFace(direction, sprite, 0F, 0.125F, 0.125F, 0.875F, 0F));
            quads.add(endFace(direction, sprite, 0.875F, 0.125F, 1F, 0.875F, 0F));
            // Close the recessed opening's inner walls so oblique views cannot see through the casing.
            for (Direction wall : Direction.values()) {
                if (wall.getAxis() == direction.getAxis()) continue;
                Vector3f min = new Vector3f(0.125F);
                Vector3f max = new Vector3f(0.875F);
                int axis = direction.getAxis().ordinal();
                boolean positive = direction.getAxisDirection() == Direction.AxisDirection.POSITIVE;
                min.setComponent(axis, positive ? 1F - 0.95F / 16F : 0F);
                max.setComponent(axis, positive ? 1F : 0.95F / 16F);
                float edge = wall.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 0.125F : 0.875F;
                min.setComponent(wall.getAxis().ordinal(), edge);
                max.setComponent(wall.getAxis().ordinal(), edge);
                List<Vector3f> points = vertices(wall);
                for (Vector3f point : points) {
                    point.set(Mth.lerp(point.x, min.x, max.x), Mth.lerp(point.y, min.y, max.y),
                            Mth.lerp(point.z, min.z, max.z));
                }
                quads.add(face(wall, sprite, points));
            }
        }

        private BakedQuad shaftPanel(Direction direction, TextureAtlasSprite sprite) {
            return endFace(direction, sprite, 0.125F, 0.125F, 0.875F, 0.875F, 0.95F / 16F);
        }

        private BakedQuad endFace(Direction direction, TextureAtlasSprite sprite,
                                  float minU, float minV, float maxU, float maxV, float inset) {
            List<Vector3f> points = vertices(direction);
            for (Vector3f point : points) {
                switch (direction.getAxis()) {
                    case X -> { point.y = Mth.lerp(point.y, minV, maxV); point.z = Mth.lerp(point.z, minU, maxU); }
                    case Y -> { point.x = Mth.lerp(point.x, minU, maxU); point.z = Mth.lerp(point.z, minV, maxV); }
                    case Z -> { point.x = Mth.lerp(point.x, minU, maxU); point.y = Mth.lerp(point.y, minV, maxV); }
                }
                Vec3i normal = direction.getNormal();
                point.add(normal.getX() * -inset, normal.getY() * -inset, normal.getZ() * -inset);
            }
            return face(direction, sprite, points);
        }

        private BakedQuad face(Direction direction, TextureAtlasSprite sprite, List<Vector3f> points) {
            QuadBakingVertexConsumer builder = new QuadBakingVertexConsumer();
            builder.setSprite(sprite);
            builder.setDirection(direction);
            builder.setShade(true);
            builder.setHasAmbientOcclusion(true);
            Vec3i normal = direction.getNormal();
            for (Vector3f vertex : points) {
                float[] uv = uv(direction, Direction.NORTH, vertex);
                builder.addVertex(vertex.x(), vertex.y(), vertex.z());
                builder.setColor(255, 255, 255, 255);
                builder.setNormal(normal.getX(), normal.getY(), normal.getZ());
                builder.setUv(sprite.getU(Mth.lerp(sprite.uvShrinkRatio(), uv[0], 0.5f)),
                        sprite.getV(Mth.lerp(sprite.uvShrinkRatio(), uv[1], 0.5f)));
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
        public TextureAtlasSprite getParticleIcon(ModelData data) {
            var ctm = data.get(CTM_CONTEXT);
            return ctm == null ? particle : ctm.model().getParticleIcon(ctm.data());
        }

        @Override
        public TriState useAmbientOcclusion(BlockState state, ModelData data, RenderType renderType) {
            var ctm = data.get(CTM_CONTEXT);
            return ctm == null ? IDynamicBakedModel.super.useAmbientOcclusion(state, data, renderType)
                    : ctm.model().useAmbientOcclusion(ctm.state(), ctm.data(), renderType);
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
            CtmContext ctm = data.get(CTM_CONTEXT);
            ChunkRenderTypeSet base = ctm == null ? ChunkRenderTypeSet.of(RenderType.solid())
                    : ctm.renderTypes();
            if (stressAxis(state) != null) {
                base = ChunkRenderTypeSet.union(base, ChunkRenderTypeSet.of(RenderType.solid()));
            }
            return ChunkRenderTypeSet.union(base, ChunkRenderTypeSet.of(
                    RenderType.solid(), RenderType.cutout(), RenderType.translucent()));
        }

        @Override
        public List<BakedModel> getRenderPasses(ItemStack stack, boolean fabulous) {
            return itemPasses;
        }
    }

    /**
     * Separate item passes retain the base material's opacity in the item renderer.
     *
     * @author howxu <dev@howxu.cn>
     */
    private static final class ItemPass extends BakedModelWrapper<DynamicModel> {
        private final RenderType renderType;

        private ItemPass(DynamicModel model, RenderType renderType) {
            super(model);
            this.renderType = renderType;
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random) {
            return originalModel.itemQuads(side, renderType);
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random,
                                        ModelData data, @Nullable RenderType renderType) {
            return getQuads(state, side, random);
        }

        @Override
        public List<RenderType> getRenderTypes(ItemStack stack, boolean fabulous) {
            if (renderType == RenderType.translucent()) {
                return List.of(NeoForgeRenderTypes.ITEM_UNSORTED_TRANSLUCENT.get());
            }
            return List.of(renderType == RenderType.cutout() ? NeoForgeRenderTypes.ITEM_LAYERED_CUTOUT.get()
                    : NeoForgeRenderTypes.ITEM_LAYERED_SOLID.get());
        }
    }
}
