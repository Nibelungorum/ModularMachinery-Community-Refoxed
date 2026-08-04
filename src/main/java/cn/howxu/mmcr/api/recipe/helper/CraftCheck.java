package cn.howxu.mmcr.api.recipe.helper;

public final class CraftCheck {

    public enum ResultType {
        SUCCESS,
        PARTIAL_SUCCESS,
        FAILURE_MISSING_INPUT,
        INVALID_SKIP
    }

    private static final CraftCheck SUCCESS = new CraftCheck(ResultType.SUCCESS, "");
    private static final CraftCheck PARTIAL_SUCCESS = new CraftCheck(ResultType.PARTIAL_SUCCESS, "");
    private static final CraftCheck INVALID_SKIP = new CraftCheck(ResultType.INVALID_SKIP, "");

    private final ResultType type;
    private final String unlocalizedMessage;

    protected CraftCheck(ResultType type, String unlocalizedMessage) {
        this.type = type;
        this.unlocalizedMessage = unlocalizedMessage;
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
        return new CraftCheck(ResultType.FAILURE_MISSING_INPUT, unlocMessage);
    }

    public ResultType getType() {
        return type;
    }

    public String getUnlocalizedMessage() {
        return unlocalizedMessage;
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
        return type == other.type && unlocalizedMessage.equals(other.unlocalizedMessage);
    }

    @Override
    public int hashCode() {
        return 31 * type.hashCode() + unlocalizedMessage.hashCode();
    }
}
