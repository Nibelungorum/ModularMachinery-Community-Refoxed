package cn.howxu.mmcr.api.recipe;

import java.util.concurrent.atomic.AtomicLong;

/** Generation fence for value proofs and derived recipes when a converter or execution delegate changes.
 * @author howxu <dev@howxu.cn>
 */
public final class RecipeSemantics {
    private static final AtomicLong REVISION = new AtomicLong();
    private RecipeSemantics() {}
    public static long revision() { return REVISION.get(); }
    public static void changed() { REVISION.incrementAndGet(); }
}
