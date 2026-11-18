package cn.howxu.mmcr.api.recipe;

import net.minecraft.resources.ResourceLocation;
import java.util.Set;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** Stable capability families whose inputs are selected by the recipe input scope. Unspecified families are shared.
 * @author howxu <dev@howxu.cn>
 */
public final class InputScopeFamilies {
    private static final Set<ResourceLocation> ISOLATED = ConcurrentHashMap.newKeySet();

    private InputScopeFamilies() {}

    public static ResourceLocation isolated(ResourceLocation id) {
        ISOLATED.add(Objects.requireNonNull(id));
        return id;
    }

    public static boolean isIsolated(ResourceLocation id) { return id != null && ISOLATED.contains(id); }
}
