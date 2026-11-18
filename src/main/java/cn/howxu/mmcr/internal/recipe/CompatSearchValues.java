package cn.howxu.mmcr.internal.recipe;

import net.minecraft.resources.ResourceLocation;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/** Pure-value budget rules. Capture creates immutable conditions; a worker owns its mutable trial state.
 * @author howxu <dev@howxu.cn>
 */
public final class CompatSearchValues {
    private CompatSearchValues() {}

    public interface Condition {
        default boolean samplesQuantity() { return false; }
        ResourceLocation reserve(RecipeSearchSnapshot.Rule rule, long parallelism, long requested, long consumed, Budget state);
    }

    public static final class Budget {
        private final Map<Class<?>, Object> deltas = new HashMap<>();
        public <S> S get(Class<S> type, Supplier<S> factory) {
            return type.cast(deltas.computeIfAbsent(type, ignored -> factory.get()));
        }
        void clear() { deltas.clear(); }
    }
}
