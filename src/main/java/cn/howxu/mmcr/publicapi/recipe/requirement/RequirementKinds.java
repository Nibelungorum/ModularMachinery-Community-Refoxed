package cn.howxu.mmcr.publicapi.recipe.requirement;
import cn.howxu.mmcr.internal.api.facade.recipe.RequirementExtensionAdapter;
/** Registers extensions in the existing canonical requirement registry. @author howxu <dev@howxu.cn> */
public final class RequirementKinds {
    private RequirementKinds() {}
    public static <R> RequirementKind<R> register(RequirementExtension<R> extension) { return RequirementExtensionAdapter.register(extension); }
}
