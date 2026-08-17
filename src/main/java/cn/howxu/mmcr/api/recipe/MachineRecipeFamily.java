package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.api.machine.MachineRegistration;
import net.minecraft.resources.Identifier;

/**
 * Logical per-machine recipe family used by scripting and recipe viewers.
 * Actual recipes remain reloadable content and are not created by registration.
 *
 * @author howxu <dev@howxu.cn>
 */
public record MachineRecipeFamily(Identifier machineId, Identifier recipeFamilyId) {
    public static MachineRecipeFamily from(MachineRegistration registration) {
        return new MachineRecipeFamily(registration.id(), registration.recipeFamilyId());
    }

    public String kubeRecipeType() {
        return recipeFamilyId.toString();
    }
}
