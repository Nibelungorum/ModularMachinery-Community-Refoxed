package cn.howxu.mmcr.api.publicapi.recipe;

import java.util.Objects;

/** Immutable public smart-interface recipe requirement with an inclusive value range.
 * @author howxu <dev@howxu.cn>
 */
public record SmartInterfaceRequirement(RecipeIo io, String interfaceType, float minValue, float maxValue)
        implements RecipeRequirement {
    public SmartInterfaceRequirement {
        Objects.requireNonNull(io, "io");
        if (interfaceType == null || interfaceType.isBlank()) throw new IllegalArgumentException("interfaceType must not be blank");
        if (!Float.isFinite(minValue) || !Float.isFinite(maxValue) || minValue > maxValue) {
            throw new IllegalArgumentException("Smart interface range must be finite and ordered");
        }
    }

    public static SmartInterfaceRequirement input(String type, float value) { return input(type, value, value); }
    public static SmartInterfaceRequirement input(String type, float min, float max) { return new SmartInterfaceRequirement(RecipeIo.INPUT, type, min, max); }
    public static SmartInterfaceRequirement output(String type, float value) { return new SmartInterfaceRequirement(RecipeIo.OUTPUT, type, value, value); }
}
