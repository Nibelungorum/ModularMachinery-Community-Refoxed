package cn.howxu.mmcr.compat.pneumaticcraft;

import cn.howxu.mmcr.api.capability.async.AsyncCapabilitySnapshot;

import cn.howxu.mmcr.internal.recipe.ConditionCapture;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.compat.pneumaticcraft.loaded.AirPortCapability;
import cn.howxu.mmcr.internal.recipe.RecipeSearchSnapshot.Rule;
import cn.howxu.mmcr.internal.recipe.RecipeSearchSnapshot.Kind;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import cn.howxu.mmcr.api.recipe.requirement.RequirementExecutionPolicy;

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
                              float chance, float consumeChance, List<String> tags) implements MachineRequirement, ConditionCapture {
    @Override public boolean searchAvailable() { return PneumaticCraftBridge.get().available(); }
    @Override public boolean supportsPort(MachineCapability port, AsyncCapabilitySnapshot value) { return port instanceof AirPortCapability; }

    @Override public Rule captureRule(int index, boolean partial, List<Integer> matching, Ports captured) {
        var direction = cn.howxu.mmcr.util.IOType.valueOf(io.name());
        boolean output = !io.isInput();
        List<AirSearchCondition.AirPort> ports = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        for (int occurrence : matching) {
            var capability = (AirPortCapability) captured.get(occurrence);
            var port = captured.snapshot(AirSearchCondition.AirPort.class, occurrence, () ->
                    AirSearchCondition.airPort(captured.identity(capability.queryIdentity()), occurrence, capability.state()));
            if (seen.add(port.id())) ports.add(port);
        }
        return new Rule(index, type().id(), Kind.CONDITION, direction, this.airPerTick(), 0D,
                this.chance(), this.consumeChance(), output && partial, true, Set.of(), null, List.of(),
                (output ? AirFailureReasons.OUTPUT_BLOCKED : AirFailureReasons.INSUFFICIENT_AIR).id(),
                new AirSearchCondition(this.minPressure(), ports));
    }

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
            Codec.floatRange(0F, 1F).optionalFieldOf("chance", 1F).forGetter(Fields::chance),
            Codec.floatRange(0F, 1F).optionalFieldOf("consume_chance", 1F).forGetter(Fields::consumeChance),
            Codec.STRING.listOf().optionalFieldOf("tags", List.of()).forGetter(Fields::tags)
    ).apply(instance, Fields::new)).flatXmap(AirRequirement::decode, value -> DataResult.success(new Fields(
            "pneumaticcraft:air", value.io(), value.airPerTick(),
            value.io() == RecipeModifier.IOType.INPUT ? Optional.of(value.minPressure()) : Optional.empty(),
            value.chance(), value.consumeChance(), value.tags())));
    public static final RequirementType<AirRequirement> TYPE = new RequirementType.Definition<>(
            PneumaticIds.AIR, CODEC, new AirRequirementHandler(), value -> new AirRequirement(
            value.io(), value.airPerTick(), value.minPressure(), value.chance(), value.consumeChance(), value.tags()),
            RecipeSyncCodec.json(CODEC.codec()));

    public AirRequirement(RecipeModifier.IOType io, long airPerTick, float minPressure, List<String> tags) {
        this(io, airPerTick, minPressure, 1F, 1F, tags);
    }

    public AirRequirement {
        if (io == null) throw new IllegalArgumentException("air direction is required");
        if (airPerTick < 0L) throw new IllegalArgumentException("air_per_tick must be non-negative");
        if (!Float.isFinite(chance) || chance < 0F || chance > 1F
                || !Float.isFinite(consumeChance) || consumeChance < 0F || consumeChance > 1F) {
            throw new IllegalArgumentException("chance and consume_chance must be between 0 and 1");
        }
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
        return input(airPerTick, minPressure, 1F, tags);
    }

    public static AirRequirement input(long airPerTick, float minPressure, float consumeChance) {
        return input(airPerTick, minPressure, consumeChance, List.of());
    }

    public static AirRequirement input(long airPerTick, float minPressure, float consumeChance, List<String> tags) {
        return new AirRequirement(RecipeModifier.IOType.INPUT, airPerTick, minPressure, 1F, consumeChance, tags);
    }

    public static AirRequirement output(long airPerTick) {
        return output(airPerTick, List.of());
    }

    public static AirRequirement output(long airPerTick, List<String> tags) {
        return output(airPerTick, 1F, tags);
    }

    public static AirRequirement output(long airPerTick, float chance) {
        return output(airPerTick, chance, List.of());
    }

    public static AirRequirement output(long airPerTick, float chance, List<String> tags) {
        return new AirRequirement(RecipeModifier.IOType.OUTPUT, airPerTick, 0F, chance, 1F, tags);
    }

    @Override
    public RequirementExecutionPolicy execution() { return io.isInput() ? RequirementExecutionPolicy.PREFLIGHT_INPUT : RequirementExecutionPolicy.TICK_IO; }

    @Override
    public long batchAmount(int duration) { return airPerTick; }

    @Override
    public float consumptionProbability() { return consumeChance; }

    @Override
    public Object derivationVersion() { return type().handler(); }

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
                    fields.minPressure().orElse(0F), fields.chance(), fields.consumeChance(), fields.tags()));
        } catch (IllegalArgumentException exception) {
            return DataResult.error(exception::getMessage);
        }
    }

    /** Presence-preserving codec fields.
     * @author howxu <dev@howxu.cn>
     */
    private record Fields(String type, RecipeModifier.IOType io, long airPerTick,
                          Optional<Float> minPressure, float chance, float consumeChance, List<String> tags) {
    }
}
