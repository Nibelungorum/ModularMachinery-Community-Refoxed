package cn.howxu.mmcr.client.model;

import cn.howxu.mmcr.api.machine.MachineAppearanceSpec;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.client.controller.ControllerSpecCache;
import net.minecraft.resources.Identifier;

/**
 * Resolves dynamic cube base and overlay textures for machine block models.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class DynamicOverlayBakedModel {
    private static final Identifier DEFAULT_PORT_OVERLAY_TEXTURE = Identifier.withDefaultNamespace("block/copper_block");

    private DynamicOverlayBakedModel() {
    }

    public record TextureSet(Identifier base, Identifier overlay) {
        public TextureSet {
            if (base == null) throw new IllegalArgumentException("base null");
            if (overlay == null) throw new IllegalArgumentException("overlay null");
        }
    }

    public enum Kind {
        CONTROLLER,
        PORT
    }

    public record CacheKey(
            Kind kind,
            Identifier machineId,
            Identifier baseTexture,
            Identifier overlayTexture,
            Identifier explicitPortBaseTexture,
            long controllerRevision,
            long appearanceRevision) {
        public CacheKey {
            if (kind == null) throw new IllegalArgumentException("kind null");
            if (baseTexture == null) throw new IllegalArgumentException("baseTexture null");
            if (overlayTexture == null) throw new IllegalArgumentException("overlayTexture null");
        }
    }

    public static TextureSet controllerTextures(Identifier machineId) {
        MachineAppearanceSpec appearance = MachineAppearanceCache.specFor(machineId);
        MachineControllerSpec controller = ControllerSpecCache.specFor(machineId);
        return new TextureSet(appearance.controllerBaseTexture(), controller.frontTexture());
    }

    public static TextureSet portTextures(Identifier machineId, Identifier explicitBaseTexture, Identifier overlayTexture) {
        Identifier base = explicitBaseTexture != null
                ? explicitBaseTexture
                : MachineAppearanceCache.specFor(machineId).formedPortBaseTexture();
        return new TextureSet(base, overlayTexture);
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
                textures.overlay(),
                null,
                ControllerSpecCache.revision(),
                MachineAppearanceCache.revision());
    }

    public static CacheKey portCacheKey(Identifier machineId, Identifier explicitBaseTexture, Identifier overlayTexture) {
        TextureSet textures = portTextures(machineId, explicitBaseTexture, overlayTexture);
        return new CacheKey(
                Kind.PORT,
                machineId,
                textures.base(),
                textures.overlay(),
                explicitBaseTexture,
                ControllerSpecCache.revision(),
                MachineAppearanceCache.revision());
    }

    public static void clearCache() {
    }
}
