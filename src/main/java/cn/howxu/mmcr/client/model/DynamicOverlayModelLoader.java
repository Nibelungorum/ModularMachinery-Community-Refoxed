package cn.howxu.mmcr.client.model;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.MachineAppearanceSpec;
import cn.howxu.mmcr.client.controller.ControllerSpecCache;
import cn.howxu.mmcr.compat.athena.AthenaModelBridge;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import com.google.common.collect.ImmutableList;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelDebugName;
import net.minecraft.client.resources.model.SimpleModelWrapper;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.QuadCollection;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.data.AtlasIds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.DynamicBlockStateModel;
import net.neoforged.neoforge.client.model.block.CustomUnbakedBlockStateModel;
import net.neoforged.neoforge.client.model.pipeline.QuadBakingVertexConsumer;
import net.neoforged.neoforge.model.data.ModelData;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Dynamic block-state model for shared machine controller and I/O port overlays.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class DynamicOverlayModelLoader implements DynamicBlockStateModel {
    public static final Identifier CONTROLLER_ID = MMCR.id("dynamic_controller_overlay");
    public static final Identifier PORT_ID = MMCR.id("dynamic_port_overlay");
    public static final MapCodec<Unbaked> CONTROLLER_CODEC = MapCodec.unit(() -> new Unbaked(DynamicOverlayBakedModel.Kind.CONTROLLER));
    public static final MapCodec<Unbaked> PORT_CODEC = MapCodec.unit(() -> new Unbaked(DynamicOverlayBakedModel.Kind.PORT));

    private static final Material FALLBACK_PARTICLE = new Material(MMCR.id("block/ctm/basic_casing/particle"));
    // Only item models grow overlays. World layers share the base quad's vertices.
    static final float OVERLAY_GROW = 0.0003f;
    private static final Set<Identifier> MISSING_BASE_TEXTURES = ConcurrentHashMap.newKeySet();

    private final DynamicOverlayBakedModel.Kind kind;
    private final Function<Identifier, Material.Baked> runtimeMaterialGetter;
    private final Material.Baked particle;

    DynamicOverlayModelLoader(DynamicOverlayBakedModel.Kind kind, Material.Baked particle,
                              Function<Identifier, Material.Baked> runtimeMaterialGetter) {
        this.kind = kind;
        this.runtimeMaterialGetter = runtimeMaterialGetter;
        this.particle = particle;
    }

    @Override
    public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random,
                             List<BlockStateModelPart> parts) {
        ModelData modelData = level.getModelData(pos);
        DynamicOverlayBakedModel.TextureSet textures = textures(state, modelData);
        Direction overlayFace = overlayFace(state);
        Direction rollFacing = rollFacing(state);
        ImmutableList<Identifier> overlays = textures.overlays();
        if (kind == DynamicOverlayBakedModel.Kind.CONTROLLER) {
            Identifier machineId = machineId(state, modelData);
            boolean easterEgg = ControllerIdleEasterEggManager.trackAndIsActive(level, pos, state, machineId);
            var stateOverlay = DynamicOverlayBakedModel.controllerStateOverlay(machineId,
                    state.getValue(MachineControllerBlock.ACTIVE), easterEgg);
            overlays = ImmutableList.<Identifier>builder().addAll(overlays).add(stateOverlay).build();
        }

        int first = parts.size();
        collectBaseParts(level, pos, state, modelData, random, parts, textures.base());
        var materials = overlays.stream().map(runtimeMaterialGetter).toList();
        for (int i = first; i < parts.size(); i++) {
            parts.set(i, new LayeredFacePart(parts.get(i), overlayFace, rollFacing, materials));
        }
    }

    private void collectBaseParts(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData modelData,
                                  RandomSource random, List<BlockStateModelPart> parts, DynamicOverlayBakedModel.FaceTextures textures) {
        var bridge = AthenaModelBridge.get();
        if (bridge.available()) {
            var sourceState = DynamicOverlayBakedModel.sourceState(ctmSource(kind, state, modelData), level, pos);
            if (sourceState.isPresent()) {
                var appearance = sourceState.get();
                BlockStateModel sourceModel = Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(appearance);
                if (bridge.collectParts(sourceModel, level, pos, appearance, random, parts)) return;
            }
        }

        var quads = new QuadCollection.Builder();
        for (Direction direction : Direction.values()) {
            addFace(quads, direction, baseMaterial(textures.forFace(direction)), 0.0f, true);
        }
        parts.add(new SimpleModelWrapper(quads.build(), true, material(textures.forFace(Direction.NORTH))));
    }

    @Override
    public Material.Baked particleMaterial() {
        return particle;
    }

    @Override
    public int materialFlags() {
        return BakedQuad.FLAG_TRANSLUCENT | BakedQuad.FLAG_ANIMATED;
    }

    private DynamicOverlayBakedModel.TextureSet textures(BlockState state, ModelData modelData) {
        Identifier machineId = machineId(state, modelData);
        if (kind == DynamicOverlayBakedModel.Kind.CONTROLLER) {
            return DynamicOverlayBakedModel.controllerTextures(machineId);
        }
        var source = modelData.get(MachineModelDataKeys.PORT_TEXTURE_SOURCE);
        return DynamicOverlayBakedModel.portTextures(machineId, source, portOverlayTextures(state));
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
        Identifier machineId = machineId(state, modelData);
        MachineAppearanceSpec appearance = MachineAppearanceCache.specFor(machineId);
        return DynamicOverlayBakedModel.controllerCtmEligible(machineId, appearance,
                ControllerSpecCache.specFor(machineId)) ? appearance.controllerTextureSource() : null;
    }

    private Direction overlayFace(BlockState state) {
        if (kind == DynamicOverlayBakedModel.Kind.CONTROLLER && state.hasProperty(MachineControllerBlock.FACING)) {
            return state.getValue(MachineControllerBlock.FACING);
        }
        return null;
    }

    private Direction rollFacing(BlockState state) {
        if (kind == DynamicOverlayBakedModel.Kind.CONTROLLER && state.hasProperty(MachineControllerBlock.ROLL_FACING)) {
            return state.getValue(MachineControllerBlock.ROLL_FACING);
        }
        return Direction.NORTH;
    }

    private static Identifier machineId(BlockState state, ModelData modelData) {
        Identifier modelDataId = modelData.get(MachineModelDataKeys.MACHINE_ID);
        if (modelDataId != null) {
            return modelDataId;
        }
        if (state.getBlock() instanceof MachineControllerBlock controller) {
            return controller.machineId();
        }
        return null;
    }

    private static ImmutableList<Identifier> portOverlayTextures(BlockState state) {
        RuntimeBlockModelDefinition definition = RuntimeMachineModelRegistry.definition(state.getBlock());
        return definition == null
                ? ImmutableList.of(DynamicOverlayBakedModel.defaultPortOverlayTexture())
                : definition.itemDescription().overlayTextures();
    }

    static float overlayGrow(int index) {
        return OVERLAY_GROW * (index + 1);
    }

    static ImmutableList<OverlayLayer> overlayLayers(List<Identifier> overlays, @Nullable Identifier stateOverlay) {
        ImmutableList.Builder<OverlayLayer> layers = ImmutableList.builder();
        for (int index = 0; index < overlays.size(); index++) {
            layers.add(new OverlayLayer(overlays.get(index), overlayGrow(index)));
        }
        if (stateOverlay != null) {
            layers.add(new OverlayLayer(stateOverlay, overlayGrow(overlays.size())));
        }
        return layers.build();
    }

    record OverlayLayer(Identifier texture, float grow) {
    }

    private Material.Baked material(Identifier texture) {
        return runtimeMaterialGetter.apply(texture);
    }

    static Material.Baked runtimeMaterial(Identifier texture) {
        return new Material.Baked(Minecraft.getInstance().getAtlasManager()
                .getAtlasOrThrow(AtlasIds.BLOCKS).getSprite(texture), false);
    }

    private Material.Baked baseMaterial(Identifier texture) {
        Material.Baked baked = material(texture);
        if (!baked.sprite().contents().name().equals(MissingTextureAtlasSprite.getLocation())) {
            return baked;
        }
        if (MISSING_BASE_TEXTURES.add(texture)) {
            MMCR.LOG.warn("Missing dynamic base texture {}; using fallback", texture);
        }
        return material(FALLBACK_PARTICLE.sprite());
    }

    static void clearMissingBaseTextureWarnings() {
        MISSING_BASE_TEXTURES.clear();
    }

    static void addFace(QuadCollection.Builder quads, Direction direction, Material.Baked material,
                        float grow, boolean culled) {
        addFace(quads, direction, Direction.NORTH, material, grow, culled);
    }

    static void addFace(QuadCollection.Builder quads, Direction direction, Direction rollFacing, Material.Baked material,
                        float grow, boolean culled) {
        QuadBakingVertexConsumer builder = new QuadBakingVertexConsumer();
        builder.setSprite(material);
        builder.setDirection(direction);
        builder.setShade(true);

        Vec3i normal = direction.getUnitVec3i();
        for (Vector3f vertex : vertices(direction, grow)) {
            float[] uv = uv(direction, rollFacing, vertex);
            putVertex(builder, material, normal, vertex.x(), vertex.y(), vertex.z(), uv[0], uv[1]);
        }

        if (culled) {
            quads.addCulledFace(direction, builder.bakeQuad());
        } else {
            quads.addUnculledFace(builder.bakeQuad());
        }
    }

    private static void putVertex(QuadBakingVertexConsumer builder, Material.Baked material, Vec3i normal,
                                  float x, float y, float z, float u, float v) {
        var sprite = material.sprite();
        builder.addVertex(x, y, z);
        builder.setColor(1.0f, 1.0f, 1.0f, 1.0f);
        builder.setNormal(normal.getX(), normal.getY(), normal.getZ());
        builder.setUv(sprite.getU(u), sprite.getV(v));
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

    static float[] uv(Direction direction, Direction rollFacing, Vector3fc vertex) {
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

    private static float u(Direction direction, Vector3fc vertex) {
        return switch (direction) {
            case EAST -> 1.0f - vertex.z();
            case WEST -> vertex.z();
            case NORTH -> 1.0f - vertex.x();
            default -> vertex.x();
        };
    }

    private static float v(Direction direction, Vector3fc vertex) {
        return direction.getAxis().isVertical() ? vertex.z() : 1.0f - vertex.y();
    }

    public record Unbaked(DynamicOverlayBakedModel.Kind kind) implements CustomUnbakedBlockStateModel {
        @Override
        public BlockStateModel bake(ModelBaker baker) {
            ModelDebugName debugName = () -> DynamicOverlayModelLoader.class.toString();
            Material.Baked particle = baker.materials().get(FALLBACK_PARTICLE, debugName);
            // Runtime lookups must not feed CTM's baking-time texture scraper for this shared model.
            return new DynamicOverlayModelLoader(kind, particle, DynamicOverlayModelLoader::runtimeMaterial);
        }

        @Override
        public void resolveDependencies(Resolver resolver) {
        }

        @Override
        public MapCodec<Unbaked> codec() {
            return kind == DynamicOverlayBakedModel.Kind.CONTROLLER ? CONTROLLER_CODEC : PORT_CODEC;
        }
    }

}
