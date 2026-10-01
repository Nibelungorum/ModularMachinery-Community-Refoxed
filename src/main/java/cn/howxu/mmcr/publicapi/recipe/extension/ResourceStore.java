package cn.howxu.mmcr.publicapi.recipe.extension;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;
/** Library-produced typed storage view of an existing capability facet.
 * @author howxu <dev@howxu.cn> */
@ApiStatus.NonExtendable
public interface ResourceStore<R> { Class<R> resourceType(); int size(); @Nullable R resource(int slot); long amount(int slot); long capacity(int slot, @Nullable R resource); boolean isValid(int slot, R resource); }
