package cn.howxu.mmcr.compat.botania;

import cn.howxu.mmcr.api.recipe.OutputRegistry;
import cn.howxu.mmcr.api.recipe.OutputType;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerRegistry;
import cn.howxu.mmcr.api.recipe.requirement.RequirementType;

/**
 * Registers canonical mana declarations without loading optional Botania classes.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class BotaniaRecipeTypes {
    private BotaniaRecipeTypes() {
    }

    public static void register() {
        RequirementType<?> requirement = RequirementHandlerRegistry.typeFor(BotaniaManaIds.MANA);
        if (requirement == null) {
            RequirementHandlerRegistry.register(ManaRequirement.TYPE);
        } else if (requirement != ManaRequirement.TYPE) {
            throw new IllegalArgumentException("Duplicate requirement handler type: " + BotaniaManaIds.MANA);
        }
        OutputType<?> output = OutputRegistry.typeFor(BotaniaManaIds.MANA);
        if (output == null) {
            OutputRegistry.register(ManaOutput.TYPE);
        } else if (output != ManaOutput.TYPE) {
            throw new IllegalArgumentException("Duplicate output type: " + BotaniaManaIds.MANA);
        }
    }
}
