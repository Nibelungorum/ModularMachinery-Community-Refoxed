package cn.howxu.mmcr.api.recipe.helper;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.status.FailureOccurrence;
import cn.howxu.mmcr.api.capability.status.FailurePhase;
import cn.howxu.mmcr.api.recipe.FailureAdapters;
import cn.howxu.mmcr.api.recipe.RequirementFailure;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public final class CraftCheck {

    public enum ResultType {
        SUCCESS,
        PARTIAL_SUCCESS,
        FAILURE_MISSING_INPUT,
        INVALID_SKIP
    }

    private static final ResourceLocation SOURCE = MMCR.id("craft_check");
    private static final CraftCheck SUCCESS = new CraftCheck(ResultType.SUCCESS, null);
    private static final CraftCheck PARTIAL_SUCCESS = new CraftCheck(ResultType.PARTIAL_SUCCESS, null);
    private static final CraftCheck INVALID_SKIP = new CraftCheck(ResultType.INVALID_SKIP, null);

    private final ResultType type;
    private final @Nullable FailureOccurrence failure;

    private CraftCheck(ResultType type, @Nullable FailureOccurrence failure) {
        this.type = Objects.requireNonNull(type, "type");
        this.failure = failure;
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
        return new CraftCheck(ResultType.FAILURE_MISSING_INPUT,
                FailureAdapters.legacyMessage(unlocMessage, SOURCE, FailurePhase.REQUIREMENT_PLAN));
    }

    public static CraftCheck failure(String unlocMessage, RequirementFailure requirementFailure) {
        return new CraftCheck(ResultType.FAILURE_MISSING_INPUT,
                FailureAdapters.occurrence(requirementFailure, SOURCE, FailurePhase.REQUIREMENT_PLAN));
    }

    public ResultType getType() {
        return type;
    }

    public String getUnlocalizedMessage() {
        return failure == null || failure.reason() == null ? "" : failure.reason().translationKey();
    }

    public @Nullable FailureOccurrence getFailure() {
        return failure;
    }

    public boolean isSuccess() {
        return this.type == ResultType.SUCCESS;
    }

    public boolean isFailure() {
        return this.type == ResultType.FAILURE_MISSING_INPUT;
    }

    public boolean isInvalid() {
        return this.type == ResultType.INVALID_SKIP;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof CraftCheck other)) return false;
        return type == other.type
                && Objects.equals(failure, other.failure);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, failure);
    }
}
