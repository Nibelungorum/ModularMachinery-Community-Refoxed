package cn.howxu.mmcr.compat.mekanism.loaded;

import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.plan.PlanningContext;
import cn.howxu.mmcr.api.capability.plan.RequirementPlan;
import cn.howxu.mmcr.api.compat.mekanism.HeatRequirement;
import cn.howxu.mmcr.api.compat.mekanism.MekanismFailureReasons;
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
        implements MachineRequirement {
    private static final RequirementHandler<LoadedHeatRequirement> UNAVAILABLE =
            (requirement, capabilities, context) -> unavailable(requirement, context);
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
    public RequirementType<LoadedHeatRequirement> type() {
        return heat.kind() == HeatRequirement.Kind.OUTPUT_HEAT ? HEAT_TYPE : TEMPERATURE_TYPE;
    }

    public static void installHandler(RequirementHandler<LoadedHeatRequirement> handler) {
        delegate = Objects.requireNonNull(handler, "handler");
    }

    public static void installUnavailableHandler() {
        delegate = UNAVAILABLE;
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
                LoadedHeatRequirement::copy, RecipeSyncCodec.json(codec.codec()));
    }

    private static LoadedHeatRequirement copy(LoadedHeatRequirement requirement) {
        return new LoadedHeatRequirement(requirement.io(), requirement.heat(), requirement.tags());
    }

    private static RequirementPlan unavailable(LoadedHeatRequirement requirement, PlanningContext context) {
        return RequirementHandlerSupport.blockedPlan(requirement, context,
                MekanismFailureReasons.MEKANISM_UNAVAILABLE);
    }
}
