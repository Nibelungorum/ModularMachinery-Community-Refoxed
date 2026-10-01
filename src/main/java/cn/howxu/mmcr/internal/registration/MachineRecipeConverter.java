package cn.howxu.mmcr.internal.registration;

import cn.howxu.mmcr.api.registration.StructureRegistration;
import cn.howxu.mmcr.api.machine.definition.ModifierDefinition;
import cn.howxu.mmcr.api.machine.level.MachineLevel;
import cn.howxu.mmcr.api.machine.modifier.MachineModifier;
import cn.howxu.mmcr.api.recipe.CustomRecipeIo;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.MachineRecipeDefinition;
import cn.howxu.mmcr.api.recipe.OutputRegistry;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.LevelRequirement;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.registration.ApiRegistrationException;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/** Compiles a shared recipe declaration into a runtime recipe.
 * @author howxu <dev@howxu.cn>
 */
public final class MachineRecipeConverter {
    private MachineRecipeConverter() {
    }

    public static MachineRecipe toRecipe(MachineRecipeDefinition definition,
                                         StructureRegistration.Snapshot snapshot) {
        Map<ResourceLocation, ModifierDefinition> modifiers = snapshot.modifiers();
        Map<ResourceLocation, MachineLevel> levels = snapshot.levels();
        List<MachineRequirement> requirements = new ArrayList<>(MachineRequirement.copyList(definition.requirements()));
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
