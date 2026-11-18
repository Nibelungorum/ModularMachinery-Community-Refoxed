package cn.howxu.mmcr.compat.mekanism.loaded;

import cn.howxu.mmcr.internal.recipe.ConditionCapture;
import cn.howxu.mmcr.internal.recipe.AsyncRequirementPlanner;
import cn.howxu.mmcr.api.capability.async.AsyncCapabilityRequest;
import cn.howxu.mmcr.internal.recipe.RecipeSearchSnapshot.Rule;
import cn.howxu.mmcr.internal.recipe.RecipeSearchSnapshot.Kind;
import cn.howxu.mmcr.api.capability.async.AsyncCapabilitySnapshot;

import cn.howxu.mmcr.api.recipe.RecipeSemantics;

import java.util.Set;

import cn.howxu.mmcr.api.recipe.requirement.RequirementExecutionPolicy;

import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.IOType;

import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.plan.PlanningContext;
import cn.howxu.mmcr.api.capability.plan.RequirementPlan;
import cn.howxu.mmcr.api.compat.mekanism.HeatRequirement;
import cn.howxu.mmcr.api.compat.mekanism.MekanismFailureReasons;
import cn.howxu.mmcr.compat.mekanism.MekanismModifiers;
import cn.howxu.mmcr.api.recipe.RecipeSyncCodec;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandler;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandler.ResourceWakeup;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerSupport;
import cn.howxu.mmcr.api.recipe.requirement.RequirementType;
import cn.howxu.mmcr.compat.mekanism.MekanismRecipeTypes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Objects;

/**
 * Internal heat requirement payload shared by the optional bridge handlers.
 *
 * @author howxu <dev@howxu.cn>
 */
public record LoadedHeatRequirement(RecipeModifier.IOType io, HeatRequirement heat, List<String> tags)
        implements MachineRequirement, ConditionCapture {
    @Override public AsyncRequirementPlanner.Requirement commitRequest(int index, long parallelism) {
        boolean minimumTemperature = heat.kind() == HeatRequirement.Kind.MINIMUM_TEMPERATURE;
        double value = minimumTemperature ? heat.value() : saturatingHeatMultiply(heat.value(), parallelism);
        if (value < 0D) return null;
        long accountingAmount = Math.max(1L, value >= Long.MAX_VALUE ? Long.MAX_VALUE : (long) Math.ceil(value));
        return new AsyncRequirementPlanner.Requirement(index, accountingAmount,
                minimumTemperature ? cn.howxu.mmcr.util.IOType.INPUT : cn.howxu.mmcr.util.IOType.OUTPUT,
                List.of(new AsyncCapabilityRequest.Heat(MekanismRecipeTypes.HEAT, parallelism, value,
                    minimumTemperature, accountingAmount)));
    }

    private static double saturatingHeatMultiply(double amount, long multiplier) {
        if (amount <= 0D || multiplier <= 0L) return 0D;
        return amount >= Double.MAX_VALUE / multiplier ? Double.MAX_VALUE : amount * multiplier;
    }

    @Override public boolean searchAvailable() { return supportsSearchSnapshot(); }
    @Override public boolean matchesPort(MachineCapability port) { return LoadedMekanismBridge.acceptsHeatSearchPort(port); }
    @Override public boolean supportsPort(MachineCapability port, AsyncCapabilitySnapshot value) {
        return value instanceof AsyncCapabilitySnapshot.Heat;
    }

    @Override public Rule captureRule(int index, boolean partial, List<Integer> matching, Ports ports) {
        boolean threshold = heat.kind() == HeatRequirement.Kind.MINIMUM_TEMPERATURE;
        boolean satisfied = !matching.isEmpty() && (!threshold || matching.stream().anyMatch(i ->
                ((AsyncCapabilitySnapshot.Heat) ports.value(i)).temperature() >= heat.value()));
        var failure = threshold ? MekanismFailureReasons.HEAT_TEMPERATURE_INSUFFICIENT : MekanismFailureReasons.HEAT_OUTPUT_BLOCKED;
        return new Rule(index, type().id(), Kind.NOOP, cn.howxu.mmcr.util.IOType.valueOf(io.name()),
                0L, 0D, 1F, 1F, false, heat.value() >= 0D && satisfied, Set.of(), null, List.of(), failure.id());
    }

    private static final RequirementHandler<LoadedHeatRequirement> UNAVAILABLE =
            (requirement, capabilities, context) -> unavailable(requirement, context);
    private static volatile Object version = new Object();
    private static volatile RequirementHandler<LoadedHeatRequirement> delegate = UNAVAILABLE;
    private static final RequirementHandler<LoadedHeatRequirement> DELEGATING_HANDLER = new RequirementHandler<>() {
        @Override
        public RequirementPlan plan(LoadedHeatRequirement requirement, List<MachineCapability> capabilities,
                                    PlanningContext context) {
            return delegate.plan(requirement, capabilities, context);
        }

        @Override
        public List<ResourceWakeup> resourceWakeups(LoadedHeatRequirement requirement) {
            return delegate.resourceWakeups(requirement);
        }

        @Override
        public LoadedHeatRequirement applyModifiers(LoadedHeatRequirement requirement,
                                                     List<RecipeModifier> modifiers) {
            return delegate.applyModifiers(requirement, modifiers);
        }
    };

    public static final RequirementType<LoadedHeatRequirement> TEMPERATURE_TYPE = type(
            MekanismRecipeTypes.HEAT_TEMPERATURE, HeatRequirement.Kind.MINIMUM_TEMPERATURE);
    public static final RequirementType<LoadedHeatRequirement> HEAT_TYPE = type(
            MekanismRecipeTypes.HEAT, HeatRequirement.Kind.OUTPUT_HEAT);
    public static final RequirementType<LoadedHeatRequirement> TYPE = TEMPERATURE_TYPE;

    public LoadedHeatRequirement {
        Objects.requireNonNull(io, "io");
        Objects.requireNonNull(heat, "heat");
        tags = tags == null ? List.of() : List.copyOf(tags);
        if (io == RecipeModifier.IOType.INPUT && heat.kind() != HeatRequirement.Kind.MINIMUM_TEMPERATURE) {
            throw new IllegalArgumentException("heat inputs must specify a minimum temperature");
        }
        if (io == RecipeModifier.IOType.OUTPUT && heat.kind() != HeatRequirement.Kind.OUTPUT_HEAT) {
            throw new IllegalArgumentException("heat outputs must specify output heat");
        }
    }

    public static LoadedHeatRequirement minimumTemperature(double temperature) {
        return new LoadedHeatRequirement(RecipeModifier.IOType.INPUT,
                HeatRequirement.minimumTemperature(temperature), List.of());
    }

    public static LoadedHeatRequirement outputHeat(double heat) {
        return new LoadedHeatRequirement(RecipeModifier.IOType.OUTPUT,
                HeatRequirement.outputHeat(heat), List.of());
    }

    @Override
    public long batchAmount(int duration) { return 0; }

    @Override
    public float consumptionProbability() { return 1F; }

    @Override
    public RequirementExecutionPolicy execution() { return io.isInput() ? RequirementExecutionPolicy.LEGACY : RequirementExecutionPolicy.TICK_IO; }

    @Override
    public Object derivationVersion() { return supportsSearchSnapshot() ? version : null; }

    @Override
    public RequirementType<LoadedHeatRequirement> type() {
        return heat.kind() == HeatRequirement.Kind.OUTPUT_HEAT ? HEAT_TYPE : TEMPERATURE_TYPE;
    }

    public static void installHandler(RequirementHandler<LoadedHeatRequirement> handler) {
        delegate = Objects.requireNonNull(handler, "handler");
        version = new Object();
        RecipeSemantics.changed();
    }

    /** True only for the built-in handler whose heat rules can be captured by value. */
    public static boolean supportsSearchSnapshot() {
        return delegate != UNAVAILABLE && LoadedMekanismBridge.isNativeHeatHandler(delegate);
    }

    public static void installUnavailableHandler() {
        delegate = UNAVAILABLE;
        version = new Object();
        RecipeSemantics.changed();
    }

    public static LoadedHeatRequirement applyModifiers(LoadedHeatRequirement requirement,
                                                        List<RecipeModifier> modifiers) {
        double value = MekanismModifiers.applyHeat(modifiers, requirement.heat().value(), requirement.io());
        HeatRequirement heat = requirement.heat().kind() == HeatRequirement.Kind.OUTPUT_HEAT
                ? HeatRequirement.outputHeat(value) : HeatRequirement.minimumTemperature(value);
        return new LoadedHeatRequirement(requirement.io(), heat, requirement.tags());
    }

    private static RequirementType<LoadedHeatRequirement> type(ResourceLocation id, HeatRequirement.Kind kind) {
        MapCodec<LoadedHeatRequirement> codec = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.STRING.fieldOf("type").forGetter(ignored -> id.toString()),
                RecipeModifier.IO_TYPE_CODEC.optionalFieldOf("io", kind == HeatRequirement.Kind.OUTPUT_HEAT
                                ? RecipeModifier.IOType.OUTPUT : RecipeModifier.IOType.INPUT)
                        .forGetter(LoadedHeatRequirement::io),
                Codec.DOUBLE.fieldOf("value").forGetter(value -> value.heat().value()),
                Codec.STRING.listOf().optionalFieldOf("tags", List.of()).forGetter(LoadedHeatRequirement::tags)
        ).apply(instance, (ignored, io, value, tags) -> new LoadedHeatRequirement(io,
                kind == HeatRequirement.Kind.OUTPUT_HEAT ? HeatRequirement.outputHeat(value)
                        : HeatRequirement.minimumTemperature(value), tags)));
        return new RequirementType.Definition<>(id, codec, DELEGATING_HANDLER,
                LoadedHeatRequirement::copy, RecipeSyncCodec.json(codec.codec())) {
            @Override public Set<ResourceLocation> capabilityIds() { return Set.of(MekanismRecipeTypes.HEAT); }
        };
    }

    private static LoadedHeatRequirement copy(LoadedHeatRequirement requirement) {
        return new LoadedHeatRequirement(requirement.io(), requirement.heat(), requirement.tags());
    }

    private static RequirementPlan unavailable(LoadedHeatRequirement requirement, PlanningContext context) {
        return RequirementHandlerSupport.blockedPlan(requirement, context,
                MekanismFailureReasons.MEKANISM_UNAVAILABLE);
    }
}
