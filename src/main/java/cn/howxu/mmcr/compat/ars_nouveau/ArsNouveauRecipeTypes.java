package cn.howxu.mmcr.compat.ars_nouveau;

import cn.howxu.mmcr.api.recipe.OutputRegistry;
import cn.howxu.mmcr.api.recipe.OutputType;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerRegistry;
import cn.howxu.mmcr.api.recipe.requirement.RequirementType;

/**
 * Registers canonical source declarations without loading optional Ars classes.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class ArsNouveauRecipeTypes {
    private ArsNouveauRecipeTypes() {
    }

    public static void register() {
        RequirementType<?> requirement = RequirementHandlerRegistry.typeFor(ArsSourceIds.SOURCE);
        if (requirement == null) {
            RequirementHandlerRegistry.register(SourceRequirement.TYPE);
        } else if (requirement != SourceRequirement.TYPE) {
            throw new IllegalArgumentException("Duplicate requirement handler type: " + ArsSourceIds.SOURCE);
        }
        OutputType<?> output = OutputRegistry.typeFor(ArsSourceIds.SOURCE);
        if (output == null) {
            OutputRegistry.register(SourceOutput.TYPE);
        } else if (output != SourceOutput.TYPE) {
            throw new IllegalArgumentException("Duplicate output type: " + ArsSourceIds.SOURCE);
        }
    }
}
