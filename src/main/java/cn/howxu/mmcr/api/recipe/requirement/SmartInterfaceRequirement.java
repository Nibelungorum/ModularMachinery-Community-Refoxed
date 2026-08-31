package cn.howxu.mmcr.api.recipe.requirement;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

/**
 * @author howxu <dev@howxu.cn>
 */
public record SmartInterfaceRequirement(RecipeModifier.IOType io, String interfaceType, float minValue, float maxValue)
        implements MachineRequirement {
    private static final Identifier TYPE_ID = MMCR.id("smart_interface");
    public static final MapCodec<SmartInterfaceRequirement> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.fieldOf("type").forGetter(value -> TYPE_ID.toString()),
            RecipeModifier.IO_TYPE_CODEC.optionalFieldOf("io", RecipeModifier.IOType.INPUT)
                    .forGetter(SmartInterfaceRequirement::io),
            Codec.STRING.fieldOf("interface_type").forGetter(SmartInterfaceRequirement::interfaceType),
            Codec.FLOAT.fieldOf("min_value").forGetter(SmartInterfaceRequirement::minValue),
            Codec.FLOAT.fieldOf("max_value").forGetter(SmartInterfaceRequirement::maxValue)
    ).apply(instance, (ignored, io, interfaceType, minValue, maxValue) ->
            new SmartInterfaceRequirement(io, interfaceType, minValue, maxValue)));
    private static final RequirementHandler<SmartInterfaceRequirement> HANDLER = new SmartInterfaceRequirementHandler();
    public static final RequirementType<SmartInterfaceRequirement> TYPE =
            new RequirementType.Definition<>(TYPE_ID, CODEC, HANDLER, SmartInterfaceRequirement::copy);

    public SmartInterfaceRequirement {
        if (io == null) throw new IllegalArgumentException("io null");
        if (interfaceType == null || interfaceType.isBlank()) throw new IllegalArgumentException("interfaceType blank");
        if (!Float.isFinite(minValue) || !Float.isFinite(maxValue) || minValue > maxValue) {
            throw new IllegalArgumentException("invalid smart interface value range");
        }
    }

    private static SmartInterfaceRequirement copy(SmartInterfaceRequirement requirement) {
        return new SmartInterfaceRequirement(requirement.io(), requirement.interfaceType(),
                requirement.minValue(), requirement.maxValue());
    }

    @Override
    public RequirementType<SmartInterfaceRequirement> type() {
        return TYPE;
    }

    public static SmartInterfaceRequirement input(String type, float value) {
        return input(type, value, value);
    }

    public static SmartInterfaceRequirement input(String type, float minValue, float maxValue) {
        return new SmartInterfaceRequirement(RecipeModifier.IOType.INPUT, type, minValue, maxValue);
    }

    public static SmartInterfaceRequirement output(String type, float value) {
        return new SmartInterfaceRequirement(RecipeModifier.IOType.OUTPUT, type, value, value);
    }

}
