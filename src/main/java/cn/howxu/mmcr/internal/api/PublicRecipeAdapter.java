package cn.howxu.mmcr.internal.api;

import cn.howxu.mmcr.api.publicapi.machine.LevelRequirement;
import cn.howxu.mmcr.api.publicapi.machine.ModifierDefinition;
import cn.howxu.mmcr.api.publicapi.ApiRegistrationException;
import cn.howxu.mmcr.api.publicapi.recipe.FluidInput;
import cn.howxu.mmcr.api.publicapi.recipe.FluidOutput;
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
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.api.recipe.requirement.FluidRequirement;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.SmartInterfaceRequirement;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.minecraft.core.registries.BuiltInRegistries;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.Identifier;

/** Internal conversion boundary for public recipe declarations.
 * @author howxu <dev@howxu.cn>
 */
public final class PublicRecipeAdapter {
    private PublicRecipeAdapter() {
    }

    public static MachineRecipe toRecipe(MachineRecipeDefinition definition,
            MMCRMachineStructuresEvent.Snapshot snapshot) {
        Map<Identifier, ModifierDefinition> modifiers = snapshot.modifiers();
        Map<Identifier, cn.howxu.mmcr.api.machine.level.MachineLevel> levels = snapshot.levels();
        List<MachineIngredient> inputs = new ArrayList<>();
        for (ItemInput input : definition.itemInputs()) {
            inputs.add(new MachineIngredient.ItemIngredient(input.ingredient(), input.count(),
                    toInternalComponents(input.components()), input.consumeChance()));
        }
        for (FluidInput input : definition.fluidInputs()) {
            inputs.add(new MachineIngredient.FluidIngredient(input.ingredient(), input.amount()));
        }
        for (var input : definition.energyInputs()) {
            inputs.add(new MachineIngredient.EnergyIngredient((int) input.fePerTick()));
        }
        List<ItemStack> outputs = definition.itemOutputs().stream().map(ItemOutput::stack).toList();
        List<FluidStack> fluidOutputs = definition.fluidOutputs().stream().map(FluidOutput::stack).toList();
        List<MachineRequirement> requirements = new ArrayList<>();
        for (RecipeRequirement value : definition.requirements()) {
            requirements.add(toRequirement(value));
        }
        List<RecipeModifier> recipeModifiers = definition.modifierIds().stream().map(id -> {
            ModifierDefinition modifier = modifiers.get(id);
            if (modifier == null) throw new ApiRegistrationException("Recipe " + definition.id()
                    + " refers to unknown machine modifier " + id);
            return modifier.modifiers();
        }).flatMap(List::stream).toList();
        definition.levelRequirements().forEach(level -> {
            if (!levels.containsKey(level.levelId())) throw new ApiRegistrationException("Recipe " + definition.id()
                    + " refers to unknown machine level " + level.levelId());
        });
        return new MachineRecipe(definition.id(), definition.machineId(), definition.tickTime(), inputs, outputs,
                recipeModifiers, definition.priority(), definition.maxThreads(), definition.cancelRecipeOnPerTickFailure(),
                fluidOutputs, requirements, definition.parallelized(), definition.levelRequirements().stream()
                        .map(PublicRecipeAdapter::toInternalLevel).toList(), definition.allowPartialOutputs(), definition.requiredHostIds());
    }

    private static MachineRequirement toRequirement(RecipeRequirement value) {
        if (value instanceof cn.howxu.mmcr.api.publicapi.recipe.ItemRequirement item) {
            return new ItemRequirement(toInternalIo(item.io()), item.ingredient(), item.count(), item.stack(), item.chance(),
                    List.of(), toInternalComponents(item.components()), item.consumeChance());
        }
        if (value instanceof cn.howxu.mmcr.api.publicapi.recipe.FluidRequirement fluid) {
            return new FluidRequirement(toInternalIo(fluid.io()), fluid.ingredient(), fluid.amount(), fluid.stack(), fluid.chance(), List.of());
        }
        if (value instanceof cn.howxu.mmcr.api.publicapi.recipe.EnergyRequirement energy) {
            return new EnergyRequirement(toInternalIo(energy.io()), (int) energy.fePerTick());
        }
        if (value instanceof cn.howxu.mmcr.api.publicapi.recipe.SmartInterfaceRequirement smart) {
            return new SmartInterfaceRequirement(toInternalIo(smart.io()), smart.interfaceType(), smart.minValue(), smart.maxValue());
        }
        throw new IllegalArgumentException("Unsupported public recipe requirement: " + value);
    }

    private static cn.howxu.mmcr.api.recipe.component.DataComponentPredicateSet toInternalComponents(
            DataComponentPredicateSet components) {
        if (components.values().isEmpty()) return cn.howxu.mmcr.api.recipe.component.DataComponentPredicateSet.EMPTY;
        Map<net.minecraft.core.component.DataComponentType<?>, cn.howxu.mmcr.api.recipe.component.ComponentPredicate> values = new java.util.HashMap<>();
        components.values().forEach((id, predicate) -> {
            var type = BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(id);
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
        if (predicate instanceof ComponentPredicate.MapValue map) {
            Map<String, cn.howxu.mmcr.api.recipe.component.ComponentPredicate> values = new java.util.HashMap<>();
            map.values().forEach((key, value) -> values.put(key, toInternalPredicate(value)));
            return cn.howxu.mmcr.api.recipe.component.ComponentPredicate.map(values);
        }
        if (predicate instanceof ComponentPredicate.ListValue list) {
            return cn.howxu.mmcr.api.recipe.component.ComponentPredicate.list(
                    list.values().stream().map(PublicRecipeAdapter::toInternalPredicate).toList());
        }
        if (predicate instanceof ComponentPredicate.Range range) {
            return cn.howxu.mmcr.api.recipe.component.ComponentPredicate.range(range.min(), range.max());
        }
        ComponentPredicate.TextValue text = (ComponentPredicate.TextValue) predicate;
        return cn.howxu.mmcr.api.recipe.component.ComponentPredicate.text(text.value(),
                cn.howxu.mmcr.api.recipe.component.ComponentPredicate.TextMode.valueOf(text.mode().name()));
    }

    private static RecipeModifier.IOType toInternalIo(RecipeIo io) {
        return io == RecipeIo.INPUT ? RecipeModifier.IOType.INPUT : RecipeModifier.IOType.OUTPUT;
    }

    private static cn.howxu.mmcr.api.recipe.LevelRequirement toInternalLevel(LevelRequirement level) {
        return new cn.howxu.mmcr.api.recipe.LevelRequirement(level.typeId(), level.levelId());
    }
}
