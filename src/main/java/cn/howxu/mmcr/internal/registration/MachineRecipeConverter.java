package cn.howxu.mmcr.internal.registration;

import cn.howxu.mmcr.api.machine.level.MachineLevel;
import cn.howxu.mmcr.api.machine.modifier.MachineModifier;
import cn.howxu.mmcr.api.publicapi.machine.ModifierDefinition;
import cn.howxu.mmcr.api.publicapi.ApiRegistrationException;
import cn.howxu.mmcr.api.publicapi.recipe.FluidInput;
import cn.howxu.mmcr.api.publicapi.recipe.FluidOutput;
import cn.howxu.mmcr.api.publicapi.recipe.CustomRecipeIo;
import cn.howxu.mmcr.api.publicapi.recipe.RecipeIo;
import cn.howxu.mmcr.api.publicapi.recipe.RecipeRequirement;
import cn.howxu.mmcr.api.publicapi.recipe.ItemInput;
import cn.howxu.mmcr.api.publicapi.recipe.ItemOutput;
import cn.howxu.mmcr.api.publicapi.recipe.MachineRecipeBuilder;
import cn.howxu.mmcr.api.publicapi.recipe.MachineRecipeDefinition;
import cn.howxu.mmcr.api.publicapi.recipe.component.ComponentPredicate;
import cn.howxu.mmcr.api.publicapi.recipe.component.DataComponentPredicateSet;
import cn.howxu.mmcr.api.publicapi.event.MMCRMachineStructuresEvent;
import cn.howxu.mmcr.api.recipe.MachineIngredient;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.OutputRegistry;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.api.recipe.requirement.FluidRequirement;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.recipe.requirement.LevelRequirement;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerRegistry;
import cn.howxu.mmcr.api.recipe.requirement.SmartInterfaceRequirement;
import cn.howxu.mmcr.api.recipe.requirement.StageRequirement;
import com.google.gson.JsonElement;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Objects;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.minecraft.core.registries.BuiltInRegistries;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/** Internal conversion boundary for public recipe declarations.
 * @author howxu <dev@howxu.cn>
 */
public final class MachineRecipeConverter {
    private MachineRecipeConverter() {
    }

    public static MachineRecipe toRecipe(MachineRecipeDefinition definition,
            MMCRMachineStructuresEvent.Snapshot snapshot) {
        Map<ResourceLocation, ModifierDefinition> modifiers = snapshot.modifiers();
        Map<ResourceLocation, MachineLevel> levels = snapshot.levels();
        List<MachineRequirement> requirements = new ArrayList<>();
        for (RecipeRequirement value : definition.requirements()) {
            requirements.add(toRequirement(value));
        }
        List<MachineOutput> outputs = new ArrayList<>(requirements.stream()
                .map(OutputRegistry::fromRequirement).filter(Objects::nonNull).toList());
        for (CustomRecipeIo custom : definition.customOutputs()) {
            MachineOutput output = toOutput(custom);
            outputs.add(output);
            MachineRequirement requirement = OutputRegistry.tryToRequirement(output, List.of());
            if (requirement != null) requirements.add(requirement);
        }
        List<RecipeModifier> recipeModifiers = definition.modifierIds().stream().map(id -> {
            ModifierDefinition modifier = modifiers.get(id);
            if (modifier == null) throw new ApiRegistrationException("Recipe " + definition.id()
                    + " refers to unknown machine modifier " + id);
            return MachineModifier.recipeModifiers(modifier.modifiers());
        }).flatMap(List::stream).toList();
        requirements.stream().filter(LevelRequirement.class::isInstance).map(LevelRequirement.class::cast).forEach(level -> {
            if (!levels.containsKey(level.levelId())) throw new ApiRegistrationException("Recipe " + definition.id()
                    + " refers to unknown machine level " + level.levelId());
        });
        return MachineRecipe.fromCanonical(definition.id(), definition.recipePoolId(), definition.tickTime(), requirements,
                outputs, recipeModifiers, definition.priority(), definition.maxThreads(),
                definition.cancelRecipeOnPerTickFailure(), definition.parallelized(), definition.allowPartialOutputs(),
                definition.requiredHostIds());
    }

    public static MachineRequirement toRequirement(RecipeRequirement value) {
        if (value instanceof CustomRecipeIo custom) {
            if (custom.ioType().isInput()) {
                MachineRequirement requirement = MachineRequirement.CODEC.parse(JsonOps.INSTANCE, custom.payload()).getOrThrow();
                if (!custom.typeId().equals(requirement.type().id()) || requirement.io() != RecipeModifier.IOType.INPUT) {
                    throw new IllegalArgumentException("Custom recipe input does not match registered type: " + custom.typeId());
                }
                return requirement;
            }
            MachineRequirement requirement = MachineRequirement.CODEC.parse(JsonOps.INSTANCE, custom.payload()).getOrThrow();
            if (!custom.typeId().equals(requirement.type().id()) || requirement.io() != RecipeModifier.IOType.OUTPUT) {
                throw new IllegalArgumentException("Custom recipe output does not match registered type: " + custom.typeId());
            }
            return requirement;
        }
        if (value instanceof cn.howxu.mmcr.api.publicapi.recipe.ItemRequirement item) {
            return new ItemRequirement(toInternalIo(item.io()), item.ingredient(), item.count(), item.stack(), item.chance(),
                    List.of(), toInternalComponents(item.components()), item.consumeChance());
        }
        if (value instanceof cn.howxu.mmcr.api.publicapi.recipe.FluidRequirement fluid) {
            return new FluidRequirement(toInternalIo(fluid.io()), fluid.ingredient(), fluid.amount(), fluid.stack(), fluid.chance(), List.of(), fluid.consumeChance());
        }
        if (value instanceof cn.howxu.mmcr.api.publicapi.recipe.EnergyRequirement(RecipeIo io2, long fePerTick)) {
            return new EnergyRequirement(toInternalIo(io2), fePerTick);
        }
        if (value instanceof cn.howxu.mmcr.api.publicapi.recipe.SmartInterfaceRequirement(
                RecipeIo io1, String interfaceType, float minValue, float maxValue
        )) {
            return new SmartInterfaceRequirement(toInternalIo(io1), interfaceType, minValue, maxValue);
        }
        if (value instanceof cn.howxu.mmcr.api.publicapi.recipe.LevelRequirement(
                RecipeIo io, ResourceLocation typeId, ResourceLocation levelId
        )) {
            return new LevelRequirement(toInternalIo(io), typeId, levelId);
        }
        if (value instanceof cn.howxu.mmcr.api.publicapi.recipe.StageRequirement stage) {
            return StageRequirement.input(stage.minStage());
        }
        throw new IllegalArgumentException("Unsupported public recipe requirement: " + value);
    }

    public static RecipeRequirement toPublicRequirement(MachineRequirement value) {
        Objects.requireNonNull(value, "value");
        RecipeIo io = value.io() == RecipeModifier.IOType.INPUT ? RecipeIo.INPUT : RecipeIo.OUTPUT;
        if (!value.tags().isEmpty()) return codecBackedRequirement(value, io);
        if (value instanceof ItemRequirement item) {
            return new cn.howxu.mmcr.api.publicapi.recipe.ItemRequirement(io, item.item(), item.count(), item.stack(),
                    item.chance(), toPublicComponents(item.components()), item.consumeChance());
        }
        if (value instanceof FluidRequirement fluid) {
            return new cn.howxu.mmcr.api.publicapi.recipe.FluidRequirement(io, fluid.fluid(), fluid.amount(),
                    fluid.stack(), fluid.chance(), fluid.consumeChance());
        }
        if (value instanceof EnergyRequirement energy) {
            return new cn.howxu.mmcr.api.publicapi.recipe.EnergyRequirement(io, energy.fePerTick());
        }
        if (value instanceof SmartInterfaceRequirement smart) {
            return new cn.howxu.mmcr.api.publicapi.recipe.SmartInterfaceRequirement(io, smart.interfaceType(),
                    smart.minValue(), smart.maxValue());
        }
        if (value instanceof LevelRequirement level) {
            return new cn.howxu.mmcr.api.publicapi.recipe.LevelRequirement(io, level.typeId(), level.levelId());
        }
        if (value instanceof StageRequirement stage) {
            return new cn.howxu.mmcr.api.publicapi.recipe.StageRequirement(stage.minStage());
        }
        return codecBackedRequirement(value, io);
    }

    private static CustomRecipeIo codecBackedRequirement(MachineRequirement value, RecipeIo io) {
        return new CustomRecipeIo(value.type().id(), io,
                MachineRequirement.CODEC.encodeStart(JsonOps.INSTANCE, value).getOrThrow());
    }

    public static List<RecipeRequirement> toPublicRequirements(List<MachineRequirement> values) {
        return values.stream().map(MachineRecipeConverter::toPublicRequirement).toList();
    }

    private static DataComponentPredicateSet toPublicComponents(
            cn.howxu.mmcr.api.recipe.component.DataComponentPredicateSet components) {
        if (components.isEmpty()) return DataComponentPredicateSet.EMPTY;
        Map<ResourceLocation, ComponentPredicate> values = new LinkedHashMap<>();
        components.values().forEach((type, predicate) -> values.put(
                Objects.requireNonNull(BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type),
                        "Unregistered data component type"), toPublicPredicate(predicate)));
        return new DataComponentPredicateSet(values);
    }

    private static ComponentPredicate toPublicPredicate(
            cn.howxu.mmcr.api.recipe.component.ComponentPredicate predicate) {
        if (predicate instanceof cn.howxu.mmcr.api.recipe.component.ComponentPredicate.Exact(Dynamic<?> value1)) {
            return ComponentPredicate.exact(value1.convert(JsonOps.INSTANCE).getValue());
        }
        if (predicate instanceof cn.howxu.mmcr.api.recipe.component.ComponentPredicate.MapValue(
                Map<String, cn.howxu.mmcr.api.recipe.component.ComponentPredicate> values1
        )) {
            Map<String, ComponentPredicate> values = new LinkedHashMap<>();
            values1.forEach((key, value) -> values.put(key, toPublicPredicate(value)));
            return ComponentPredicate.map(values);
        }
        if (predicate instanceof cn.howxu.mmcr.api.recipe.component.ComponentPredicate.ListValue(
                List<cn.howxu.mmcr.api.recipe.component.ComponentPredicate> values
        )) {
            return ComponentPredicate.list(values.stream().map(MachineRecipeConverter::toPublicPredicate).toList());
        }
        if (predicate instanceof cn.howxu.mmcr.api.recipe.component.ComponentPredicate.Range(double min, double max)) {
            return ComponentPredicate.range(min, max);
        }
        var text = (cn.howxu.mmcr.api.recipe.component.ComponentPredicate.TextValue) predicate;
        return ComponentPredicate.text(text.value().getString(), ComponentPredicate.TextMode.valueOf(text.mode().name()));
    }

    private static cn.howxu.mmcr.api.recipe.component.DataComponentPredicateSet toInternalComponents(
            DataComponentPredicateSet components) {
        if (components.values().isEmpty()) return cn.howxu.mmcr.api.recipe.component.DataComponentPredicateSet.EMPTY;
        Map<DataComponentType<?>, cn.howxu.mmcr.api.recipe.component.ComponentPredicate> values = new HashMap<>();
        components.values().forEach((id, predicate) -> {
            var type = BuiltInRegistries.DATA_COMPONENT_TYPE.get(id);
            if (type == null) throw new IllegalArgumentException("Unknown data component type " + id);
            values.put(type, toInternalPredicate(predicate));
        });
        return new cn.howxu.mmcr.api.recipe.component.DataComponentPredicateSet(values);
    }

    private static cn.howxu.mmcr.api.recipe.component.ComponentPredicate toInternalPredicate(ComponentPredicate predicate) {
        if (predicate instanceof ComponentPredicate.Exact exact) {
            return cn.howxu.mmcr.api.recipe.component.ComponentPredicate.exact(
                    new Dynamic<>(JsonOps.INSTANCE, exact.value().deepCopy()));
        }
        if (predicate instanceof ComponentPredicate.MapValue(Map<String, ComponentPredicate> values1)) {
            Map<String, cn.howxu.mmcr.api.recipe.component.ComponentPredicate> values = new HashMap<>();
            values1.forEach((key, value) -> values.put(key, toInternalPredicate(value)));
            return cn.howxu.mmcr.api.recipe.component.ComponentPredicate.map(values);
        }
        if (predicate instanceof ComponentPredicate.ListValue(List<ComponentPredicate> values)) {
            return cn.howxu.mmcr.api.recipe.component.ComponentPredicate.list(
                    values.stream().map(MachineRecipeConverter::toInternalPredicate).toList());
        }
        if (predicate instanceof ComponentPredicate.Range(double min, double max)) {
            return cn.howxu.mmcr.api.recipe.component.ComponentPredicate.range(min, max);
        }
        ComponentPredicate.TextValue text = (ComponentPredicate.TextValue) predicate;
        return cn.howxu.mmcr.api.recipe.component.ComponentPredicate.text(text.value(),
                cn.howxu.mmcr.api.recipe.component.ComponentPredicate.TextMode.valueOf(text.mode().name()));
    }

    private static RecipeModifier.IOType toInternalIo(RecipeIo io) {
        return io == RecipeIo.INPUT ? RecipeModifier.IOType.INPUT : RecipeModifier.IOType.OUTPUT;
    }

    public static MachineOutput toOutput(CustomRecipeIo custom) {
        if (custom.ioType().isInput()) throw new IllegalArgumentException("Custom recipe input is not an output");
        var outputType = OutputRegistry.typeFor(custom.typeId());
        if (outputType == null) throw new IllegalArgumentException("Unknown recipe output type: " + custom.typeId());
        MachineOutput output = MachineOutput.CODEC.parse(JsonOps.INSTANCE, custom.payload()).getOrThrow();
        if (output.outputType() != outputType) {
            throw new IllegalArgumentException("Custom recipe output does not match registered type: " + custom.typeId());
        }
        return output;
    }

}
