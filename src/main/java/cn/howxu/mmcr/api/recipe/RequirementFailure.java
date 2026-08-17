package cn.howxu.mmcr.api.recipe;

import java.util.List;

/**
 * @author howxu <dev@howxu.cn>
 */
public record RequirementFailure(
        int requirementIndex,
        Kind kind,
        long required,
        long available,
        long shortAmount,
        List<String> searchedComponents,
        List<String> matchedComponents) {

    public RequirementFailure(int requirementIndex, Kind kind, int required, int available) {
        this(requirementIndex, kind, required, available, Math.max(0, required - available), List.of(), List.of());
    }

    public RequirementFailure(int requirementIndex, Kind kind, long required, long available, long shortAmount) {
        this(requirementIndex, kind, required, available, shortAmount, List.of(), List.of());
    }

    public RequirementFailure {
        searchedComponents = List.copyOf(searchedComponents);
        matchedComponents = List.copyOf(matchedComponents);
    }

    public enum Kind {
        MISSING_INPUT,
        MISSING_OUTPUT,
        MISSING_ENERGY,
        COMMIT_LOST_INPUT,
        COMMIT_LOST_OUTPUT,
        TAG_MISMATCH
    }
}
