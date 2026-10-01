package cn.howxu.mmcr.publicapi.recipe;
import cn.howxu.mmcr.internal.api.facade.recipe.OutputExtensionAdapter;
/** Registers outputs in the existing canonical output registry. @author howxu <dev@howxu.cn> */
public final class OutputKinds {
    private OutputKinds() {}
    public static <O> OutputKind<O> register(OutputExtension<O> extension) { return OutputExtensionAdapter.register(extension); }
}
