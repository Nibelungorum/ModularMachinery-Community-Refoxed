package cn.howxu.mmcr.publicapi.recipe.extension;
import org.jetbrains.annotations.ApiStatus;
/** Typed changed resource delivered by the existing resource notification system.
 * Notifications do not contain a quantity or resource registry ID; no such values are invented.
 * @author howxu <dev@howxu.cn> */
@ApiStatus.NonExtendable
public interface ResourceChangeView<T> { T resource(); ResourceWakeupSpec.Reason reason(); }
