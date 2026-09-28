package cn.howxu.mmcr.client.model;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.MachineAppearanceSpec;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.client.controller.ControllerSpecCache;
import com.google.common.collect.ImmutableList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves dynamic cube base and overlay textures for machine block models.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class DynamicOverlayBakedModel {
    private static final ResourceLocation DEFAULT_PORT_OVERLAY_TEXTURE = ResourceLocation.withDefaultNamespace("block/copper_block");
    private static final ResourceLocation FALLBACK_BASE_TEXTURE = MMCR.id("block/basic_casing");
    private static final Map<MachineAppearanceSpec.TextureSource, FaceTextures> BASE_TEXTURES = new ConcurrentHashMap<>();

    private DynamicOverlayBakedModel() {
    }

    public record FaceTextures(ResourceLocation down, ResourceLocation up, ResourceLocation north, ResourceLocation south, ResourceLocation west,
                               ResourceLocation east) {
        public ResourceLocation forFace(Direction direction) {
            return switch (direction) {
                case DOWN -> down;
                case UP -> up;
                case NORTH -> north;
                case SOUTH -> south;
                case WEST -> west;
                case EAST -> east;
            };
        }

        public static FaceTextures uniform(ResourceLocation texture) {
            return new FaceTextures(texture, texture, texture, texture, texture, texture);
        }
    }

    public record TextureSet(FaceTextures base, ImmutableList<ResourceLocation> overlays) {
        public TextureSet {
            if (base == null) throw new IllegalArgumentException("base null");
            if (overlays == null || overlays.isEmpty()) throw new IllegalArgumentException("overlays empty");
            overlays = ImmutableList.copyOf(overlays);
        }

        public TextureSet(ResourceLocation base, ResourceLocation overlay) {
            this(FaceTextures.uniform(base), ImmutableList.of(overlay));
        }
    }

    public enum Kind {
        CONTROLLER,
        PORT
    }

    public record CacheKey(
            Kind kind,
            ResourceLocation machineId,
            FaceTextures baseTextures,
            ImmutableList<ResourceLocation> overlayTextures,
            MachineAppearanceSpec.TextureSource explicitPortTextureSource,
            long controllerRevision,
            long appearanceRevision) {
        public CacheKey {
            if (kind == null) throw new IllegalArgumentException("kind null");
            if (baseTextures == null) throw new IllegalArgumentException("baseTextures null");
            if (overlayTextures == null || overlayTextures.isEmpty()) {
                throw new IllegalArgumentException("overlayTextures empty");
            }
            overlayTextures = ImmutableList.copyOf(overlayTextures);
        }
    }

    public static TextureSet controllerTextures(ResourceLocation machineId) {
        MachineAppearanceSpec appearance = machineId == null
                ? MachineAppearanceSpec.defaults()
                : MachineAppearanceCache.specFor(machineId);
        MachineControllerSpec controller = machineId == null
                ? MachineControllerSpec.defaultsFor(MMCR.id("unknown"))
                : ControllerSpecCache.specFor(machineId);
        return new TextureSet(resolveBase(appearance.controllerTextureSource()), ImmutableList.of(controller.frontTexture()));
    }

    public static ResourceLocation controllerStateOverlay(ResourceLocation machineId, boolean active) {
        MachineAppearanceSpec appearance = machineId == null
                ? MachineAppearanceSpec.defaults()
                : MachineAppearanceCache.specFor(machineId);
        return active ? appearance.controllerActiveOverlayTexture() : appearance.controllerIdleOverlayTexture();
    }

    static boolean controllerCtmEligible(ResourceLocation machineId, MachineAppearanceSpec appearance,
                                         MachineControllerSpec controller) {
        if (appearance.controllerTextureSource().overrideTexture() != null) {
            return false;
        }
        MachineControllerSpec defaults = MachineControllerSpec.defaultsFor(machineId);
        return controller.frontTexture().equals(defaults.frontTexture())
                && controller.sideTexture().equals(defaults.sideTexture())
                && controller.topTexture().equals(defaults.topTexture())
                && controller.bottomTexture().equals(defaults.bottomTexture());
    }

    static Optional<BlockState> sourceState(MachineAppearanceSpec.TextureSource source, BlockGetter level,
                                            BlockPos pos) {
        if (source == null || source.overrideTexture() != null) {
            return Optional.empty();
        }
        Block block = BuiltInRegistries.BLOCK.get(source.blockId());
        if (block == null) {
            return Optional.empty();
        }
        BlockState state = block.defaultBlockState();
        return Block.isShapeFullBlock(state.getShape(level, pos)) ? Optional.of(state) : Optional.empty();
    }

    public static TextureSet portTextures(ResourceLocation machineId, MachineAppearanceSpec.TextureSource explicitSource,
                                          ImmutableList<ResourceLocation> overlayTextures) {
        MachineAppearanceSpec.TextureSource source = explicitSource != null
                ? explicitSource
                : machineId == null ? MachineAppearanceSpec.defaults().formedPortTextureSource()
                : MachineAppearanceCache.specFor(machineId).formedPortTextureSource();
        return new TextureSet(resolveBase(source), overlayTextures);
    }

    public static TextureSet portTextures(ResourceLocation machineId, ResourceLocation explicitBaseTexture,
                                          ImmutableList<ResourceLocation> overlayTextures) {
        return portTextures(machineId, explicitBaseTexture == null ? null : new MachineAppearanceSpec.TextureSource(
                MachineAppearanceSpec.defaults().machineBasicBlock(), explicitBaseTexture), overlayTextures);
    }

    public static ResourceLocation defaultPortOverlayTexture() {
        return DEFAULT_PORT_OVERLAY_TEXTURE;
    }

    public static CacheKey controllerCacheKey(ResourceLocation machineId) {
        TextureSet textures = controllerTextures(machineId);
        return new CacheKey(
                Kind.CONTROLLER,
                machineId,
                textures.base(),
                textures.overlays(),
                null,
                ControllerSpecCache.revision(),
                MachineAppearanceCache.revision());
    }

    public static CacheKey portCacheKey(ResourceLocation machineId, MachineAppearanceSpec.TextureSource explicitSource,
                                        ImmutableList<ResourceLocation> overlayTextures) {
        TextureSet textures = portTextures(machineId, explicitSource, overlayTextures);
        return new CacheKey(
                Kind.PORT,
                machineId,
                textures.base(),
                textures.overlays(),
                explicitSource,
                ControllerSpecCache.revision(),
                MachineAppearanceCache.revision());
    }

    public static void clearCache() {
        BASE_TEXTURES.clear();
        DynamicOverlayModelLoader.clearMissingBaseTextureWarnings();
    }

    static FaceTextures resolveBase(MachineAppearanceSpec.TextureSource source) {
        if (source.overrideTexture() != null) {
            return FaceTextures.uniform(source.overrideTexture());
        }
        try {
            return BASE_TEXTURES.computeIfAbsent(source, DynamicOverlayBakedModel::resolveSourceBlockTextures);
        } catch (NullPointerException exception) {
            return FaceTextures.uniform(FALLBACK_BASE_TEXTURE);
        }
    }

    private static FaceTextures resolveSourceBlockTextures(MachineAppearanceSpec.TextureSource source) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getModelManager() == null) {
            return FaceTextures.uniform(FALLBACK_BASE_TEXTURE);
        }
        var block = BuiltInRegistries.BLOCK.get(source.blockId());
        if (block == null) {
            MMCR.LOG.warn("Missing appearance source block {}", source.blockId());
            return FaceTextures.uniform(FALLBACK_BASE_TEXTURE);
        }
        if (!Block.isShapeFullBlock(block.defaultBlockState().getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO))) {
            MMCR.LOG.warn("Appearance source block {} is not a full cube; using fallback", source.blockId());
            return FaceTextures.uniform(FALLBACK_BASE_TEXTURE);
        }

        BlockState state = block.defaultBlockState();
        BakedModel model = minecraft.getModelManager().getBlockModelShaper().getBlockModel(state);
        EnumMap<Direction, ResourceLocation> textures = new EnumMap<>(Direction.class);
        for (Direction direction : Direction.values()) {
            ResourceLocation texture = textureForFace(model, state, direction);
            if (texture == null) {
                MMCR.LOG.warn("Appearance source block {} has no {} face texture; using fallback", source.blockId(), direction);
                return FaceTextures.uniform(FALLBACK_BASE_TEXTURE);
            }
            textures.put(direction, texture);
        }
        return completeOrFallback(textures);
    }

    static FaceTextures completeOrFallback(Map<Direction, ResourceLocation> textures) {
        if (textures.size() != Direction.values().length || textures.values().stream().anyMatch(texture -> texture == null)) {
            return FaceTextures.uniform(FALLBACK_BASE_TEXTURE);
        }
        return new FaceTextures(textures.get(Direction.DOWN), textures.get(Direction.UP), textures.get(Direction.NORTH),
                textures.get(Direction.SOUTH), textures.get(Direction.WEST), textures.get(Direction.EAST));
    }

    private static ResourceLocation textureForFace(BakedModel model, BlockState state, Direction direction) {
        List<BakedQuad> quads = model.getQuads(state, direction, RandomSource.create(0L), ModelData.EMPTY, null);
        if (!quads.isEmpty()) {
            return quads.getFirst().getSprite().contents().name();
        }
        for (BakedQuad quad : model.getQuads(state, null, RandomSource.create(0L), ModelData.EMPTY, null)) {
            if (quad.getDirection() == direction) {
                return quad.getSprite().contents().name();
            }
        }
        return null;
    }
}
