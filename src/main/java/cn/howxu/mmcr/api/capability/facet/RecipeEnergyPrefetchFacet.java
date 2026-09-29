package cn.howxu.mmcr.api.capability.facet;

import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.api.capability.plan.CapabilityResult;

import java.util.Objects;
import java.util.Optional;

/**
 * Provides an optional prefetch reservation for recipe energy inputs.
 *
 * @author howxu <dev@howxu.cn>
 */
public interface RecipeEnergyPrefetchFacet extends CapabilityFacet {
    /**
     * Returns the stable identity used to persist and restore this facet's reservation allocation.
     *
     * <p>The value must remain stable for the same persisted capability endpoint and must be unique
     * among the prefetch facets visible to one controller. Implementations should derive it from a
     * persistent endpoint identity such as a port position or host identity, not from object identity
     * or discovery order.</p>
     *
     * @return a non-blank, stable reservation identity
     * @author howxu <dev@howxu.cn>
     */
    String reservationKey();

    /**
     * Attempts to reserve energy for a recipe input without committing a transaction.
     * An empty result means that the requested amount cannot be prefetched.
     *
     * @param requestedAmount amount requested by the recipe
     * @return a reservation plan when the amount is available
     * @author howxu <dev@howxu.cn>
     */
    Optional<PrefetchPlan> planPrefetch(long requestedAmount);

    /**
     * Restores a reservation when a candidate plan is discarded or rolled back.
     *
     * @param amount amount whose prefetch reservation should be restored
     * @author howxu <dev@howxu.cn>
     */
    void restoreReservation(long amount);

    /**
     * Releases a consumed prefetch reservation after the recipe input is applied.
     *
     * @param amount amount whose prefetch reservation should be released
     * @return amount actually released
     * @author howxu <dev@howxu.cn>
     */
    long releaseReservation(long amount);

    default CapabilityResult consumeReservation(long amount) {
        return CapabilityResult.successful();
    }

    /**
     * A positive prefetched amount and its transaction-aware operation.
     *
     * @param amount prefetched amount
     * @param operation operation that applies the prefetch
     * @author howxu <dev@howxu.cn>
     */
    record PrefetchPlan(long amount, CapabilityOperation operation) {
        public PrefetchPlan {
            if (amount <= 0L) throw new IllegalArgumentException("amount must be positive");
            Objects.requireNonNull(operation, "operation");
        }
    }
}
