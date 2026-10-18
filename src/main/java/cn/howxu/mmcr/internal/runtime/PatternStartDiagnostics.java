package cn.howxu.mmcr.internal.runtime;

import cn.howxu.mmcr.MMCR;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.Arrays;
import java.util.function.Supplier;

/** Limits deep reservation diagnostics to the caller's already sampled synchronous request.
 * @author howxu <dev@howxu.cn>
 */
public final class PatternStartDiagnostics {
    private static final ThreadLocal<Context> CURRENT = new ThreadLocal<>();

    private PatternStartDiagnostics() { }

    public static PatternStartReservation trace(boolean enabled, BlockPos source, BlockPos controller,
                                                 ResourceLocation recipe, long parallelism,
                                                 Supplier<PatternStartReservation> original) {
        Context previous = CURRENT.get();
        if (enabled) CURRENT.set(new Context(source, controller, recipe, parallelism));
        else CURRENT.remove();
        try {
            return original.get();
        } finally {
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
        }
    }

    public static void reject(String reason, Object... details) {
        Context context = CURRENT.get();
        if (context == null) return;
        try {
            MMCR.LOG.info("[PatternBatchDiag] reservation-rejected: host={} controller={} recipe={} parallelism={} reason={} details={}",
                    context.source(), context.controller(), context.recipe(), context.parallelism(), reason,
                    Arrays.toString(details));
        } catch (RuntimeException ignored) {
            // Even a failing logging backend must not skip the caller's reservation cleanup.
        }
    }

    /** @author howxu <dev@howxu.cn> */
    private record Context(BlockPos source, BlockPos controller, ResourceLocation recipe, long parallelism) { }
}
