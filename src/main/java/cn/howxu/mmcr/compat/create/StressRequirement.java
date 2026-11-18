package cn.howxu.mmcr.compat.create;

import cn.howxu.mmcr.api.capability.async.AsyncCapabilitySnapshot;

import cn.howxu.mmcr.internal.recipe.ConditionCapture;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.compat.create.loaded.StressPortCapability;
import cn.howxu.mmcr.internal.recipe.RecipeSearchSnapshot.Rule;
import cn.howxu.mmcr.internal.recipe.RecipeSearchSnapshot.Kind;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import cn.howxu.mmcr.api.compat.create.CreateFailureReasons;

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

/** Base stress/capacity per recipe lane; RPM is never scaled by parallelism.
 * @author howxu <dev@howxu.cn>
 */
public record StressRequirement(RecipeModifier.IOType io, double stress, double minRpm, double rpm,
                                List<String> tags) implements MachineRequirement, ConditionCapture {
    @Override public boolean searchAvailable() { return CreateBridge.get().available(); }
    @Override public boolean supportsPort(MachineCapability port, AsyncCapabilitySnapshot value) { return port instanceof StressPortCapability; }

    @Override public Rule captureRule(int index, boolean partial, List<Integer> matching, Ports captured) {
        var direction = cn.howxu.mmcr.util.IOType.valueOf(io.name());
        boolean output = !io.isInput();
        List<StressSearchCondition.StressPort> ports = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        for (int occurrence : matching) {
            var capability = (StressPortCapability) captured.get(occurrence);
            var port = captured.snapshot(StressSearchCondition.StressPort.class, occurrence, () -> {
                var network = capability.networkIdentity();
                return StressSearchCondition.stressPort(captured.identity(capability), network == null ? -1 : captured.identity(network),
                        capability.state(), capability.stressEnabled(), true);
            });
            if (!seen.add(port.id())) continue;
            boolean accepts = !output || capability
                    .acceptsGeneratedRpm(null, index, this.rpm());
            ports.add(new StressSearchCondition.StressPort(port.id(), port.network(), port.actualRpm(),
                    port.theoreticalRpm(), port.baseContribution(), port.networkCapacity(), port.networkStress(),
                    port.connected(), port.overstressed(), port.stressEnabled(), accepts));
        }
        return new Rule(index, type().id(), Kind.CONDITION, direction, 0L, 0D,
                1F, 1F, false, true, Set.of(), null, List.of(), CreateFailureReasons.INSUFFICIENT_STRESS.id(),
                new StressSearchCondition(this.stress(), this.minRpm(), this.rpm(), CreateBridge.get().maxRpm(), ports));
    }

    public static final MapCodec<StressRequirement> CODEC = RecordCodecBuilder.<Fields>mapCodec(instance -> instance.group(
            Codec.STRING.validate(value -> "create:stress".equals(value) ? DataResult.success(value)
                    : DataResult.error(() -> "Expected create:stress")).fieldOf("type").forGetter(Fields::type),
            Codec.STRING.<RecipeModifier.IOType>flatXmap(value -> switch (value) {
                case "input" -> DataResult.success(RecipeModifier.IOType.INPUT);
                case "output" -> DataResult.success(RecipeModifier.IOType.OUTPUT);
                default -> DataResult.error(() -> "Unknown stress IO direction: " + value);
            }, value -> DataResult.success(value.getKey())).fieldOf("io").forGetter(Fields::io),
            Codec.DOUBLE.fieldOf("stress").forGetter(Fields::stress),
            Codec.DOUBLE.optionalFieldOf("min_rpm").forGetter(Fields::minRpm),
            Codec.DOUBLE.optionalFieldOf("rpm").forGetter(Fields::rpm),
            Codec.STRING.listOf().optionalFieldOf("tags", List.of()).forGetter(Fields::tags)
    ).apply(instance, Fields::new)).flatXmap(StressRequirement::decode, value -> DataResult.success(new Fields(
            "create:stress", value.io(), value.stress(),
            value.io() == RecipeModifier.IOType.INPUT ? Optional.of(value.minRpm()) : Optional.empty(),
            value.io() == RecipeModifier.IOType.OUTPUT ? Optional.of(value.rpm()) : Optional.empty(), value.tags())));
    public static final RequirementType<StressRequirement> TYPE = new RequirementType.Definition<>(
            CreateRecipeTypes.STRESS, CODEC, new StressRequirementHandler(), value -> new StressRequirement(
            value.io(), value.stress(), value.minRpm(), value.rpm(), value.tags()), RecipeSyncCodec.json(CODEC.codec()));

    public StressRequirement {
        if (io == null) throw new IllegalArgumentException("stress direction is required");
        if (!Double.isFinite(stress) || stress <= 0D) throw new IllegalArgumentException("stress must be finite and positive");
        if (!Double.isFinite(minRpm) || minRpm < 0D) throw new IllegalArgumentException("min_rpm must be finite and non-negative");
        if (io == RecipeModifier.IOType.OUTPUT && (!Double.isFinite(rpm) || rpm == 0D)) {
            throw new IllegalArgumentException("output rpm must be finite and nonzero");
        }
        if (io == RecipeModifier.IOType.INPUT && rpm != 0D || io == RecipeModifier.IOType.OUTPUT && minRpm != 0D) {
            throw new IllegalArgumentException("rotation field does not match stress direction");
        }
        tags = tags == null ? List.of() : List.copyOf(tags);
        if (tags.size() > 1024) throw new IllegalArgumentException("too many stress tags");
    }

    public static StressRequirement input(double stress, double minRpm, List<String> tags) {
        return new StressRequirement(RecipeModifier.IOType.INPUT, stress, minRpm, 0D, tags);
    }

    public static StressRequirement input(double stress, double minRpm) {
        return input(stress, minRpm, List.of());
    }

    public static StressRequirement output(double stress, double rpm, List<String> tags) {
        return new StressRequirement(RecipeModifier.IOType.OUTPUT, stress, 0D, rpm, tags);
    }

    public static StressRequirement output(double stress, double rpm) {
        return output(stress, rpm, List.of());
    }

    @Override
    public long batchAmount(int duration) { return 0; }

    @Override
    public float consumptionProbability() { return 1F; }

    @Override
    public RequirementExecutionPolicy execution() { return io.isInput() ? RequirementExecutionPolicy.LEGACY : RequirementExecutionPolicy.AFTER_PAYMENT_OUTPUT; }

    @Override
    public Object derivationVersion() { return type().handler(); }

    @Override
    public RequirementType<StressRequirement> type() {
        return TYPE;
    }

    private static DataResult<StressRequirement> decode(Fields fields) {
        if (fields.io() == RecipeModifier.IOType.INPUT && fields.rpm().isPresent()
                || fields.io() == RecipeModifier.IOType.OUTPUT && (fields.minRpm().isPresent() || fields.rpm().isEmpty())) {
            return DataResult.error(() -> "rotation field does not match stress direction");
        }
        try {
            return DataResult.success(new StressRequirement(fields.io(), fields.stress(),
                    fields.minRpm().orElse(0D), fields.rpm().orElse(0D), fields.tags()));
        } catch (IllegalArgumentException exception) {
            return DataResult.error(exception::getMessage);
        }
    }

    /** Codec fields retain presence to reject wrong-direction fields even when zero.
     * @author howxu <dev@howxu.cn>
     */
    private record Fields(String type, RecipeModifier.IOType io, double stress, Optional<Double> minRpm,
                          Optional<Double> rpm, List<String> tags) {
    }
}
