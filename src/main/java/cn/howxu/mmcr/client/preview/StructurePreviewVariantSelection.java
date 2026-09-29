package cn.howxu.mmcr.client.preview;

import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable selected variants for level slots in a structure preview.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class StructurePreviewVariantSelection {
    private static final StructurePreviewVariantSelection DEFAULTS = new StructurePreviewVariantSelection(Map.of());

    private final Map<ResourceLocation, ResourceLocation> variants;

    private StructurePreviewVariantSelection(Map<ResourceLocation, ResourceLocation> variants) {
        Objects.requireNonNull(variants, "variants");
        Map<ResourceLocation, ResourceLocation> copy = new LinkedHashMap<>();
        variants.forEach((slot, variant) -> copy.put(
                Objects.requireNonNull(slot, "slot"), Objects.requireNonNull(variant, "variant")));
        this.variants = Collections.unmodifiableMap(copy);
    }

    public static StructurePreviewVariantSelection defaults() {
        return DEFAULTS;
    }
}
