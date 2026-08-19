package cn.howxu.mmcr.api.recipe.helper;

import cn.howxu.mmcr.api.recipe.RequirementFailure;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public final class CraftCheck {

    public enum ResultType {
        SUCCESS,
        PARTIAL_SUCCESS,
        FAILURE_MISSING_INPUT,
        INVALID_SKIP
    }

    private static final CraftCheck SUCCESS = new CraftCheck(ResultType.SUCCESS, "", null);
    private static final CraftCheck PARTIAL_SUCCESS = new CraftCheck(ResultType.PARTIAL_SUCCESS, "", null);
    private static final CraftCheck INVALID_SKIP = new CraftCheck(ResultType.INVALID_SKIP, "", null);

    private final ResultType type;
    private final String unlocalizedMessage;
    private final @Nullable RequirementFailure requirementFailure;

    protected CraftCheck(ResultType type, String unlocalizedMessage) {
        this(type, unlocalizedMessage, null);
    }

    protected CraftCheck(ResultType type, String unlocalizedMessage, @Nullable RequirementFailure requirementFailure) {
        this.type = type;
        this.unlocalizedMessage = unlocalizedMessage;
        this.requirementFailure = requirementFailure;
    }

    public static CraftCheck success() {
        return SUCCESS;
    }

    public static CraftCheck partialSuccess() {
        return PARTIAL_SUCCESS;
    }

    public static CraftCheck skipComponent() {
        return INVALID_SKIP;
    }

    public static CraftCheck failure(String unlocMessage) {
        return new CraftCheck(ResultType.FAILURE_MISSING_INPUT, unlocMessage, null);
    }

    public static CraftCheck failure(String unlocMessage, RequirementFailure requirementFailure) {
        return new CraftCheck(ResultType.FAILURE_MISSING_INPUT, unlocMessage, requirementFailure);
    }

    public ResultType getType() {
        return type;
    }

    public String getUnlocalizedMessage() {
        return unlocalizedMessage;
    }

    public @Nullable RequirementFailure getRequirementFailure() {
        return requirementFailure;
    }

    public boolean isSuccess() {
        return this.type == ResultType.SUCCESS;
    }

    public boolean isInvalid() {
        return this.type == ResultType.INVALID_SKIP;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof CraftCheck other)) return false;
        return type == other.type
                && unlocalizedMessage.equals(other.unlocalizedMessage)
                && Objects.equals(requirementFailure, other.requirementFailure);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, unlocalizedMessage, requirementFailure);
    }
}
