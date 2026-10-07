package cn.howxu.mmcr.client.model;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.MachineAppearanceSpec;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.client.controller.ControllerSpecCache;
import com.google.common.collect.ImmutableList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
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
    private static final Identifier DEFAULT_PORT_OVERLAY_TEXTURE = Identifier.withDefaultNamespace("block/copper_block");
    private static final Identifier FALLBACK_BASE_TEXTURE = MMCR.id("block/ctm/basic_casing/particle");
    private static final Identifier DEFAULT_IDLE_OVERLAY_TEXTURE = MMCR.id("block/overlay_basic_idle");
    private static final Identifier EASTER_EGG_OVERLAY_TEXTURE = MMCR.id("block/overlay_egg");
    private static final Map<MachineAppearanceSpec.TextureSource, FaceTextures> BASE_TEXTURES = new ConcurrentHashMap<>();

    private DynamicOverlayBakedModel() {
    }

    public record FaceTextures(Identifier down, Identifier up, Identifier north, Identifier south, Identifier west,
                               Identifier east) {
        public Identifier forFace(Direction direction) {
            return switch (direction) {
                case DOWN -> down;
                case UP -> up;
                case NORTH -> north;
                case SOUTH -> south;
                case WEST -> west;
                case EAST -> east;
            };
        }

        public static FaceTextures uniform(Identifier texture) {
            return new FaceTextures(texture, texture, texture, texture, texture, texture);
        }
    }

    public record TextureSet(FaceTextures base, ImmutableList<Identifier> overlays) {
        public TextureSet {
            if (base == null) throw new IllegalArgumentException("base null");
            if (overlays == null || overlays.isEmpty()) throw new IllegalArgumentException("overlays empty");
            overlays = ImmutableList.copyOf(overlays);
        }

        public TextureSet(Identifier base, Identifier overlay) {
            this(FaceTextures.uniform(base), ImmutableList.of(overlay));
        }
    }

    public enum Kind {
        CONTROLLER,
        PORT
    }

    public record CacheKey(
            Kind kind,
            Identifier machineId,
            FaceTextures baseTextures,
            ImmutableList<Identifier> overlayTextures,
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

    public static TextureSet controllerTextures(Identifier machineId) {
        MachineAppearanceSpec appearance = machineId == null
                ? MachineAppearanceSpec.defaults()
                : MachineAppearanceCache.specFor(machineId);
        MachineControllerSpec controller = machineId == null
                ? MachineControllerSpec.defaultsFor(MMCR.id("unknown"))
                : ControllerSpecCache.specFor(machineId);
        return new TextureSet(resolveBase(appearance.controllerTextureSource()), ImmutableList.of(controller.frontTexture()));
    }

    public static Identifier controllerStateOverlay(Identifier machineId, boolean active) {
        return controllerStateOverlay(machineId, active, false);
    }

    static Identifier controllerStateOverlay(Identifier machineId, boolean active, boolean idleEasterEgg) {
        MachineAppearanceSpec appearance = machineId == null
                ? MachineAppearanceSpec.defaults()
                : MachineAppearanceCache.specFor(machineId);
        Identifier idleOverlay = appearance.controllerIdleOverlayTexture();
        if (!active && idleEasterEgg && idleOverlay.equals(DEFAULT_IDLE_OVERLAY_TEXTURE)) {
            return EASTER_EGG_OVERLAY_TEXTURE;
        }
        return active ? appearance.controllerActiveOverlayTexture() : idleOverlay;
    }

    static boolean controllerCtmEligible(Identifier machineId, MachineAppearanceSpec appearance,
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
        Block block = BuiltInRegistries.BLOCK.getValue(source.blockId());
        if (block == null) {
            return Optional.empty();
        }
        BlockState state = block.defaultBlockState();
        return Block.isShapeFullBlock(state.getShape(level, pos)) ? Optional.of(state) : Optional.empty();
    }

    public static TextureSet portTextures(Identifier machineId, MachineAppearanceSpec.TextureSource explicitSource,
                                          ImmutableList<Identifier> overlayTextures) {
        MachineAppearanceSpec.TextureSource source = explicitSource != null
                ? explicitSource
                : machineId == null ? MachineAppearanceSpec.defaults().formedPortTextureSource()
                : MachineAppearanceCache.specFor(machineId).formedPortTextureSource();
        return new TextureSet(resolveBase(source), overlayTextures);
    }

    public static TextureSet portTextures(Identifier machineId, Identifier explicitBaseTexture,
                                          ImmutableList<Identifier> overlayTextures) {
        return portTextures(machineId, explicitBaseTexture == null ? null : new MachineAppearanceSpec.TextureSource(
                MachineAppearanceSpec.defaults().machineBasicBlock(), explicitBaseTexture), overlayTextures);
    }

    public static Identifier defaultPortOverlayTexture() {
        return DEFAULT_PORT_OVERLAY_TEXTURE;
    }

    public static CacheKey controllerCacheKey(Identifier machineId) {
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

    public static CacheKey portCacheKey(Identifier machineId, MachineAppearanceSpec.TextureSource explicitSource,
                                        ImmutableList<Identifier> overlayTextures) {
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
        var block = BuiltInRegistries.BLOCK.getValue(source.blockId());
        if (block == null) {
            MMCR.LOG.warn("Missing appearance source block {}", source.blockId());
            return FaceTextures.uniform(FALLBACK_BASE_TEXTURE);
        }
        if (!Block.isShapeFullBlock(block.defaultBlockState().getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO))) {
            MMCR.LOG.warn("Appearance source block {} is not a full cube; using fallback", source.blockId());
            return FaceTextures.uniform(FALLBACK_BASE_TEXTURE);
        }

        List<BlockStateModelPart> parts = new ArrayList<>();
        minecraft.getModelManager().getBlockStateModelSet().get(block.defaultBlockState())
                .collectParts(RandomSource.create(0L), parts);
        EnumMap<Direction, Identifier> textures = new EnumMap<>(Direction.class);
        for (Direction direction : Direction.values()) {
            Identifier texture = textureForFace(parts, direction);
            if (texture == null) {
                MMCR.LOG.warn("Appearance source block {} has no {} face texture; using fallback", source.blockId(), direction);
                return FaceTextures.uniform(FALLBACK_BASE_TEXTURE);
            }
            textures.put(direction, texture);
        }
        return completeOrFallback(textures);
    }

    static FaceTextures completeOrFallback(Map<Direction, Identifier> textures) {
        if (textures.size() != Direction.values().length || textures.values().stream().anyMatch(texture -> texture == null)) {
            return FaceTextures.uniform(FALLBACK_BASE_TEXTURE);
        }
        return new FaceTextures(textures.get(Direction.DOWN), textures.get(Direction.UP), textures.get(Direction.NORTH),
                textures.get(Direction.SOUTH), textures.get(Direction.WEST), textures.get(Direction.EAST));
    }

    private static Identifier textureForFace(List<BlockStateModelPart> parts, Direction direction) {
        for (BlockStateModelPart part : parts) {
            List<BakedQuad> quads = part.getQuads(direction);
            if (!quads.isEmpty()) {
                return quads.getFirst().materialInfo().sprite().contents().name();
            }
            for (BakedQuad quad : part.getQuads(null)) {
                if (quad.direction() == direction) {
                    return quad.materialInfo().sprite().contents().name();
                }
            }
        }
        return null;
    }
}
