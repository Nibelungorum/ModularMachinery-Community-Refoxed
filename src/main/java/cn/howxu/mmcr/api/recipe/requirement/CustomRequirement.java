package cn.howxu.mmcr.api.recipe.requirement;

/**
 * Open extension point for requirements supplied outside the built-in requirement set.
 *
 * @author howxu <dev@howxu.cn>
 */
public interface CustomRequirement extends MachineRequirement {
    @Override
    RequirementType<? extends CustomRequirement> type();
}
