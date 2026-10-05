package cn.howxu.mmcr.compat.pneumaticcraft;

import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerRegistry;

/** Registers the canonical air declaration regardless of native mod presence.
 * @author howxu <dev@howxu.cn>
 */
public final class PneumaticRecipeTypes {
    private PneumaticRecipeTypes() {
    }

    public static synchronized void register() {
        var existing = RequirementHandlerRegistry.typeFor(PneumaticIds.AIR);
        if (existing == null) RequirementHandlerRegistry.register(AirRequirement.TYPE);
        else if (existing != AirRequirement.TYPE) {
            throw new IllegalStateException("Conflicting air requirement type: " + PneumaticIds.AIR);
        }
    }
}
