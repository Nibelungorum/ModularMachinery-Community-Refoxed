package cn.howxu.mmcr.api.recipe;

/**
 * @author howxu <dev@howxu.cn>
 */
public record RequirementFailure(int requirementIndex, Kind kind, int required, int available, int shortAmount) {

    public RequirementFailure(int requirementIndex, Kind kind, int required, int available) {
        this(requirementIndex, kind, required, available, Math.max(0, required - available));
    }

    public enum Kind {
        MISSING_INPUT,
        MISSING_OUTPUT,
        MISSING_ENERGY
    }
}
