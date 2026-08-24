package cn.howxu.mmcr.api.recipe.requirement;

import net.minecraft.resources.Identifier;

/**
 * Identifies a machine requirement and its serialized type id.
 *
 * @param id the stable requirement identifier
 * @author howxu <dev@howxu.cn>
 */
public record RequirementType<R extends MachineRequirement>(Identifier id) {
    public RequirementType {
        if (id == null) throw new IllegalArgumentException("id must not be null");
    }
}
