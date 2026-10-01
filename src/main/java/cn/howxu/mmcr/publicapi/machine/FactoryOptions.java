package cn.howxu.mmcr.publicapi.machine;

import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.ApiStatus;

/** Producer-created factory configuration. Consumers must not implement it. @author howxu <dev@howxu.cn> */
@ApiStatus.NonExtendable
public interface FactoryOptions {
    FactoryOptions hasFactory(boolean enabled);
    FactoryOptions threadLimit(int limit);
    FactoryOptions thread(String name, ResourceLocation... recipeIds);
    View build();

    /** Producer-created immutable declaration view. @author howxu <dev@howxu.cn> */
    @ApiStatus.NonExtendable
    interface View {
        boolean hasFactory();
        int threadLimit();
        List<ThreadView> threads();
    }

    /** Producer-created lane view. @author howxu <dev@howxu.cn> */
    @ApiStatus.NonExtendable
    interface ThreadView {
        String name();
        List<ResourceLocation> recipeIds();
    }
}
