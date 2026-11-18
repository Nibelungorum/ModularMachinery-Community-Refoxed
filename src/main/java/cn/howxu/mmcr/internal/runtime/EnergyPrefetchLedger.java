package cn.howxu.mmcr.internal.runtime;

import cn.howxu.mmcr.api.capability.facet.RecipeEnergyPrefetchFacet;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.util.SaturatingLong;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/** Allocates tentative FE reservations. Only the caller taking a successful allocation may commit it.
 * Committed and uncertain native operations remain under the execution ledger.
 * @author howxu <dev@howxu.cn>
 */
public final class EnergyPrefetchLedger {
    private EnergyPrefetchLedger() {}

    public record Candidate(String key, RecipeEnergyPrefetchFacet facet, RecipeEnergyPrefetchFacet.PrefetchPlan plan) {}
    public record Allocation(List<Candidate> candidates, int failureIndex) {}

    public static Allocation allocate(List<MachineRequirement> requirements, int duration, long parallelism,
                                      List<RecipeEnergyPrefetchFacet> facets, boolean probe) {
        List<Candidate> candidates = new ArrayList<>();
        boolean transferred = false;
        try {
            long total = 0;
            boolean checkedKeys = false;
            for (int index = 0; index < requirements.size() && !facets.isEmpty(); index++) {
                if (!(requirements.get(index) instanceof EnergyRequirement energy) || !energy.io().isInput()) continue;
                if (!checkedKeys) {
                    var keys = new HashSet<String>();
                    for (var facet : facets) {
                        String key = facet.reservationKey();
                        if (key == null || key.isBlank() || !keys.add(key)) return new Allocation(List.of(), index);
                    }
                    checkedKeys = true;
                }
                long remaining;
                try {
                    remaining = SaturatingLong.multiply(SaturatingLong.multiply(energy.fePerTick(), duration), parallelism);
                    total = Math.addExact(total, remaining);
                } catch (ArithmeticException overflow) {
                    return new Allocation(List.of(), index);
                }
                for (var facet : facets) {
                    if (remaining == 0) break;
                    var plan = facet.planPrefetch(remaining).orElse(null);
                    if (plan == null) continue;
                    candidates.add(new Candidate(facet.reservationKey(), facet, plan));
                    if (plan.amount() > remaining) return new Allocation(List.of(), index);
                    remaining -= plan.amount();
                }
                if (remaining != 0) return new Allocation(List.of(), index);
            }
            var result = new Allocation(probe ? List.of() : List.copyOf(candidates), -1);
            transferred = !probe;
            return result;
        } finally {
            if (!transferred) {
                for (int i = candidates.size() - 1; i >= 0; i--) {
                    var candidate = candidates.get(i);
                    candidate.facet().restoreReservation(candidate.plan().amount());
                }
            }
        }
    }
}
