package cn.howxu.mmcr.test;

import cn.howxu.mmcr.api.recipe.MachineIngredient;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.OutputRegistry;
import cn.howxu.mmcr.api.recipe.LevelRequirement;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Test factories for declaring recipes without relying on removed recipe constructors.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class RecipeTestSupport {
    private RecipeTestSupport() {
    }

    public static MachineRecipe create(Identifier id, Identifier machineId, int tickTime,
                                       List<MachineRequirement> requirements, List<MachineOutput> outputs,
                                       List<RecipeModifier> modifiers, int priority, int maxThreads,
                                       boolean cancelRecipeOnPerTickFailure, boolean parallelized,
                                       List<LevelRequirement> levelRequirements, boolean allowPartialOutputs,
                                       Set<Identifier> requiredHostIds) {
        return new MachineRecipe(id, machineId, tickTime, requirements, outputs, modifiers, priority,
                maxThreads, cancelRecipeOnPerTickFailure, parallelized, levelRequirements,
                allowPartialOutputs, requiredHostIds);
    }

    public static MachineRecipe create(Identifier id, Identifier machineId, int tickTime,
                                       List<?> inputs, List<?> outputs) {
        return create(id, machineId, tickTime, inputs, outputs, List.of(), 0, 1, false,
                List.of(), List.of(), false, List.of(), false, Set.of());
    }

    public static MachineRecipe create(Identifier id, Identifier machineId, int tickTime,
                                       List<?> inputs, List<?> outputs, List<RecipeModifier> modifiers,
                                       int priority, int maxThreads) {
        return create(id, machineId, tickTime, inputs, outputs, modifiers, priority, maxThreads,
                false, List.of(), List.of(), false, List.of(), false, Set.of());
    }

    public static MachineRecipe create(Identifier id, Identifier machineId, int tickTime,
                                       List<?> inputs, List<?> outputs, List<RecipeModifier> modifiers,
                                       int priority, int maxThreads, boolean cancelRecipeOnPerTickFailure) {
        return create(id, machineId, tickTime, inputs, outputs, modifiers, priority, maxThreads,
                cancelRecipeOnPerTickFailure, List.of(), List.of(), false, List.of(), false, Set.of());
    }

    public static MachineRecipe create(Identifier id, Identifier machineId, int tickTime,
                                       List<?> inputs, List<?> outputs, List<RecipeModifier> modifiers,
                                       int priority, int maxThreads, boolean cancelRecipeOnPerTickFailure,
                                       List<?> fluidOutputs) {
        return create(id, machineId, tickTime, inputs, outputs, modifiers, priority, maxThreads,
                cancelRecipeOnPerTickFailure, fluidOutputs, List.of(), false, List.of(), false, Set.of());
    }

    public static MachineRecipe create(Identifier id, Identifier machineId, int tickTime,
                                       List<?> inputs, List<?> outputs, List<RecipeModifier> modifiers,
                                       int priority, int maxThreads, boolean cancelRecipeOnPerTickFailure,
                                       List<?> fluidOutputs, List<?> explicitRequirements) {
        return create(id, machineId, tickTime, inputs, outputs, modifiers, priority, maxThreads,
                cancelRecipeOnPerTickFailure, fluidOutputs, explicitRequirements, false, List.of(), false, Set.of());
    }

    public static MachineRecipe create(Identifier id, Identifier machineId, int tickTime,
                                       List<?> inputs, List<?> outputs, List<RecipeModifier> modifiers,
                                       int priority, int maxThreads, boolean cancelRecipeOnPerTickFailure,
                                       List<?> fluidOutputs, List<?> explicitRequirements, boolean parallelized) {
        return create(id, machineId, tickTime, inputs, outputs, modifiers, priority, maxThreads,
                cancelRecipeOnPerTickFailure, fluidOutputs, explicitRequirements, parallelized, List.of(), false, Set.of());
    }

    public static MachineRecipe create(Identifier id, Identifier machineId, int tickTime,
                                       List<?> inputs, List<?> outputs, List<RecipeModifier> modifiers,
                                       int priority, int maxThreads, boolean cancelRecipeOnPerTickFailure,
                                       List<?> fluidOutputs, List<?> explicitRequirements, boolean parallelized,
                                       List<LevelRequirement> levelRequirements) {
        return create(id, machineId, tickTime, inputs, outputs, modifiers, priority, maxThreads,
                cancelRecipeOnPerTickFailure, fluidOutputs, explicitRequirements, parallelized,
                levelRequirements, false, Set.of());
    }

    public static MachineRecipe create(Identifier id, Identifier machineId, int tickTime,
                                       List<?> inputs, List<?> outputs, List<RecipeModifier> modifiers,
                                       int priority, int maxThreads, boolean cancelRecipeOnPerTickFailure,
                                       List<?> fluidOutputs, List<?> explicitRequirements, boolean parallelized,
                                       List<LevelRequirement> levelRequirements, boolean allowPartialOutputs) {
        return create(id, machineId, tickTime, inputs, outputs, modifiers, priority, maxThreads,
                cancelRecipeOnPerTickFailure, fluidOutputs, explicitRequirements, parallelized,
                levelRequirements, allowPartialOutputs, Set.of());
    }

    public static MachineRecipe create(Identifier id, Identifier machineId, int tickTime,
                                       List<?> inputs, List<?> outputs, List<RecipeModifier> modifiers,
                                       int priority, int maxThreads, boolean cancelRecipeOnPerTickFailure,
                                       List<?> fluidOutputs, List<?> explicitRequirements, boolean parallelized,
                                       List<LevelRequirement> levelRequirements, Set<Identifier> requiredHostIds) {
        return create(id, machineId, tickTime, inputs, outputs, modifiers, priority, maxThreads,
                cancelRecipeOnPerTickFailure, fluidOutputs, explicitRequirements, parallelized,
                levelRequirements, false, requiredHostIds);
    }

    public static MachineRecipe create(Identifier id, Identifier machineId, int tickTime,
                                       List<?> inputs, List<?> outputs, List<RecipeModifier> modifiers,
                                       int priority, int maxThreads, boolean cancelRecipeOnPerTickFailure,
                                       List<?> fluidOutputs, List<?> explicitRequirements, boolean parallelized,
                                       List<LevelRequirement> levelRequirements, boolean allowPartialOutputs,
                                       Set<Identifier> requiredHostIds) {
        boolean hasExplicitRequirements = explicitRequirements != null && !explicitRequirements.isEmpty();
        List<MachineRequirement> requirements = hasExplicitRequirements
                ? castRequirements(explicitRequirements)
                : deriveRequirements(inputs, outputs, fluidOutputs);
        List<MachineOutput> machineOutputs = hasExplicitRequirements
                ? deriveOutputs(requirements)
                : new ArrayList<>();
        appendOutputs(machineOutputs, outputs);
        appendOutputs(machineOutputs, fluidOutputs);
        return MachineRecipe.fromCanonical(id, machineId, tickTime, requirements, machineOutputs, modifiers,
                priority, maxThreads, cancelRecipeOnPerTickFailure, parallelized, levelRequirements,
                allowPartialOutputs, requiredHostIds);
    }

    private static List<MachineRequirement> deriveRequirements(List<?> inputs, List<?> outputs,
                                                                List<?> fluidOutputs) {
        List<MachineRequirement> requirements = new ArrayList<>();
        appendRequirements(requirements, inputs, false);
        appendRequirements(requirements, outputs, true);
        appendRequirements(requirements, fluidOutputs, true);
        return List.copyOf(requirements);
    }

    private static List<MachineRequirement> castRequirements(List<?> values) {
        List<MachineRequirement> requirements = new ArrayList<>(values.size());
        for (Object value : values) {
            if (!(value instanceof MachineRequirement requirement)) {
                throw new IllegalArgumentException("Expected machine requirement, got " + value);
            }
            requirements.add(requirement);
        }
        return List.copyOf(requirements);
    }

    private static void appendRequirements(List<MachineRequirement> requirements, List<?> values, boolean outputs) {
        if (values == null) return;
        for (Object value : values) {
            if (value instanceof MachineRequirement requirement) {
                requirements.add(requirement);
            } else if (value instanceof MachineIngredient ingredient && !outputs) {
                requirements.add(MachineRequirement.fromInput(ingredient));
            } else if (value instanceof ItemStack stack && outputs) {
                requirements.add(MachineRequirement.itemOutput(stack));
            } else if (value instanceof FluidStack stack && outputs) {
                requirements.add(MachineRequirement.fluidOutput(stack));
            }
        }
    }

    private static List<MachineOutput> deriveOutputs(List<MachineRequirement> requirements) {
        List<MachineOutput> outputs = new ArrayList<>();
        for (MachineRequirement requirement : requirements) {
            MachineOutput output = OutputRegistry.fromRequirement(requirement);
            if (output != null) outputs.add(output);
        }
        return outputs;
    }

    private static void appendOutputs(List<MachineOutput> outputs, List<?> values) {
        if (values == null) return;
        for (Object value : values) {
            if (value instanceof MachineOutput output) {
                outputs.add(output);
            } else if (value instanceof ItemStack stack) {
                outputs.add(new MachineOutput.ItemOutput(stack, 1F));
            } else if (value instanceof FluidStack stack) {
                outputs.add(new MachineOutput.FluidOutput(stack, 1F));
            }
        }
    }
}
