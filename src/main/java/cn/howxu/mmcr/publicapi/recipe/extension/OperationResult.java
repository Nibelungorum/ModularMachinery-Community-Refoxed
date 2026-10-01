package cn.howxu.mmcr.publicapi.recipe.extension;
/** Result of an extension's transactional operation. @author howxu <dev@howxu.cn> */
public record OperationResult(boolean success, PlanStatus failure) {
    public static OperationResult successful() { return new OperationResult(true, null); }
    public static OperationResult failure(PlanStatus failure) { return new OperationResult(false, failure); }
}
