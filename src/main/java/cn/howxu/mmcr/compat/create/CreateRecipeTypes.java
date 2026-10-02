package cn.howxu.mmcr.compat.create;

import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerRegistry;
import net.minecraft.resources.ResourceLocation;

/** Registers the canonical declaration regardless of Create availability.
 * @author howxu <dev@howxu.cn>
 */
public final class CreateRecipeTypes {
    public static final ResourceLocation STRESS = ResourceLocation.fromNamespaceAndPath("create", "stress");

    private CreateRecipeTypes() {
    }

    public static synchronized void register() {
        var existing = RequirementHandlerRegistry.typeFor(STRESS);
        if (existing == null) RequirementHandlerRegistry.register(StressRequirement.TYPE);
        else if (existing != StressRequirement.TYPE) {
            throw new IllegalStateException("Conflicting Create requirement type: " + STRESS);
        }
    }
}
