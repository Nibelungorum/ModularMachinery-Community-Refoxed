package cn.howxu.mmcr.client.model;

import cn.howxu.mmcr.MMCR;
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

    public static Identifier overlayTextureFor(String blockName) {
        if (blockName.startsWith("item_input_bus")) return overlayTexture(blockName, "item_input_bus", "overlay_inputbus");
        if (blockName.startsWith("item_output_bus")) return overlayTexture(blockName, "item_output_bus", "overlay_outputbus");
        if (blockName.startsWith("fluid_input_hatch")) return overlayTexture(blockName, "fluid_input_hatch", "overlay_fluidinputhatch");
        if (blockName.startsWith("fluid_output_hatch")) return overlayTexture(blockName, "fluid_output_hatch", "overlay_fluidoutputhatch");
        if (blockName.startsWith("energy_input_hatch")) return overlayTexture(blockName, "energy_input_hatch", "overlay_energyinputhatch");
        if (blockName.startsWith("energy_output_hatch")) return overlayTexture(blockName, "energy_output_hatch", "overlay_energyoutputhatch");
        return defaultPortOverlayTexture();
    }

    private static Identifier overlayTexture(String blockName, String baseName, String textureBase) {
        String tier = blockName.equals(baseName) ? "normal" : blockName.substring(baseName.length() + 1);
        return MMCR.id("block/" + textureBase + "_" + tier);
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
