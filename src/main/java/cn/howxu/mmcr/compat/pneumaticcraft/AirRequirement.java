package cn.howxu.mmcr.compat.pneumaticcraft;

import cn.howxu.mmcr.api.recipe.RecipeSyncCodec;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.RequirementType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;

/** Air per successful progress tick; pressure is an unscaled input condition.
 * @author howxu <dev@howxu.cn>
 */
public record AirRequirement(RecipeModifier.IOType io, long airPerTick, float minPressure,
                             List<String> tags) implements MachineRequirement {
    public static final MapCodec<AirRequirement> CODEC = RecordCodecBuilder.<Fields>mapCodec(instance -> instance.group(
            Codec.STRING.validate(value -> "pneumaticcraft:air".equals(value) ? DataResult.success(value)
                    : DataResult.error(() -> "Expected pneumaticcraft:air")).fieldOf("type").forGetter(Fields::type),
            Codec.STRING.<RecipeModifier.IOType>flatXmap(value -> switch (value) {
                case "input" -> DataResult.success(RecipeModifier.IOType.INPUT);
                case "output" -> DataResult.success(RecipeModifier.IOType.OUTPUT);
                default -> DataResult.error(() -> "Unknown air IO direction: " + value);
            }, value -> DataResult.success(value.getKey())).fieldOf("io").forGetter(Fields::io),
            Codec.LONG.fieldOf("air_per_tick").forGetter(Fields::airPerTick),
            Codec.FLOAT.optionalFieldOf("min_pressure").forGetter(Fields::minPressure),
            Codec.STRING.listOf().optionalFieldOf("tags", List.of()).forGetter(Fields::tags)
    ).apply(instance, Fields::new)).flatXmap(AirRequirement::decode, value -> DataResult.success(new Fields(
            "pneumaticcraft:air", value.io(), value.airPerTick(),
            value.io() == RecipeModifier.IOType.INPUT ? Optional.of(value.minPressure()) : Optional.empty(), value.tags())));
    public static final RequirementType<AirRequirement> TYPE = new RequirementType.Definition<>(
            PneumaticIds.AIR, CODEC, new AirRequirementHandler(), value -> new AirRequirement(
            value.io(), value.airPerTick(), value.minPressure(), value.tags()), RecipeSyncCodec.json(CODEC.codec()));

    public AirRequirement {
        if (io == null) throw new IllegalArgumentException("air direction is required");
        if (airPerTick < 0L) throw new IllegalArgumentException("air_per_tick must be non-negative");
        if (!Float.isFinite(minPressure) || minPressure < 0F) {
            throw new IllegalArgumentException("min_pressure must be finite and non-negative");
        }
        if (io == RecipeModifier.IOType.OUTPUT && minPressure != 0F) {
            throw new IllegalArgumentException("output does not support min_pressure");
        }
        tags = tags == null ? List.of() : List.copyOf(tags);
        if (tags.size() > 1024) throw new IllegalArgumentException("too many air tags");
    }

    public static AirRequirement input(long airPerTick, float minPressure) {
        return input(airPerTick, minPressure, List.of());
    }

    public static AirRequirement input(long airPerTick, float minPressure, List<String> tags) {
        return new AirRequirement(RecipeModifier.IOType.INPUT, airPerTick, minPressure, tags);
    }

    public static AirRequirement output(long airPerTick) {
        return output(airPerTick, List.of());
    }

    public static AirRequirement output(long airPerTick, List<String> tags) {
        return new AirRequirement(RecipeModifier.IOType.OUTPUT, airPerTick, 0F, tags);
    }

    @Override
    public RequirementType<AirRequirement> type() {
        return TYPE;
    }

    private static DataResult<AirRequirement> decode(Fields fields) {
        if (fields.io() == RecipeModifier.IOType.OUTPUT && fields.minPressure().isPresent()) {
            return DataResult.error(() -> "output does not support min_pressure");
        }
        try {
            return DataResult.success(new AirRequirement(fields.io(), fields.airPerTick(),
                    fields.minPressure().orElse(0F), fields.tags()));
        } catch (IllegalArgumentException exception) {
            return DataResult.error(exception::getMessage);
        }
    }

    /** Presence-preserving codec fields.
     * @author howxu <dev@howxu.cn>
     */
    private record Fields(String type, RecipeModifier.IOType io, long airPerTick,
                          Optional<Float> minPressure, List<String> tags) {
    }
}
