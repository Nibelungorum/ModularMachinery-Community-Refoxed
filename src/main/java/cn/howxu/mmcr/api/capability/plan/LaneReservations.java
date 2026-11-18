package cn.howxu.mmcr.api.capability.plan;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Owns transient, releasable contributions for exactly one lane execution.
 * @author howxu <dev@howxu.cn>
 */
public final class LaneReservations {
    public interface Holding {
        void releaseOutputs();
        void releaseAll();
        void onRecipeFinished();
        void cancel();
    }

    private final Map<Class<? extends Holding>, Holding> holdings = new LinkedHashMap<>();

    public <H extends Holding> H holding(Class<H> type, Supplier<H> factory) {
        return type.cast(holdings.computeIfAbsent(type, ignored -> factory.get()));
    }

    /** Distinguish a write owned by this lane from an irreversible write in the same plan. */
    public CapabilityOperation owned(CapabilityOperation operation) { return new Owned(this, operation); }

    public boolean owns(CapabilityOperation operation) {
        return operation instanceof Owned owned && owned.owner == this;
    }

    private record Owned(LaneReservations owner, CapabilityOperation operation) implements CapabilityOperation {
        public CapabilityResult commit() { return operation.commit(); }
    }

    public void releaseOutputs() { List.copyOf(holdings.values()).forEach(Holding::releaseOutputs); }
    public void releaseAll() { List.copyOf(holdings.values()).forEach(Holding::releaseAll); }
    public void onRecipeFinished() { List.copyOf(holdings.values()).forEach(Holding::onRecipeFinished); }
    public void cancel() { List.copyOf(holdings.values()).forEach(Holding::cancel); }
}
