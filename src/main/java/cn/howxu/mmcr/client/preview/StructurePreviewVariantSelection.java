package cn.howxu.mmcr.client.preview;

import net.minecraft.resources.Identifier;

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

    private final Map<Identifier, Identifier> variants;

    private StructurePreviewVariantSelection(Map<Identifier, Identifier> variants) {
        Objects.requireNonNull(variants, "variants");
        Map<Identifier, Identifier> copy = new LinkedHashMap<>();
        variants.forEach((slot, variant) -> copy.put(
                Objects.requireNonNull(slot, "slot"), Objects.requireNonNull(variant, "variant")));
        this.variants = Collections.unmodifiableMap(copy);
    }

    public static StructurePreviewVariantSelection defaults() {
        return DEFAULTS;
    }
}
