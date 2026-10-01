package cn.howxu.mmcr.publicapi.recipe.component;

import com.mojang.serialization.Dynamic;
import org.jetbrains.annotations.ApiStatus;

/** Library-produced condition; create with ComponentConditions. @author howxu <dev@howxu.cn> */
@ApiStatus.NonExtendable
public interface ComponentCondition {
    boolean isExact();
    boolean matches(Dynamic<?> candidate);
}
