package cn.howxu.mmcr.internal.recipe;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.facet.RecipeEnergyPrefetchFacet;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.util.SaturatingLong;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

/** Shared allocation algorithm for recipe feasibility and actual start reservations.
 * @author howxu <dev@howxu.cn>
 */
public final class EnergyPrefetchPlanner {
    private EnergyPrefetchPlanner() {}

    public record Allocation(String reservationKey, RecipeEnergyPrefetchFacet facet,
                             RecipeEnergyPrefetchFacet.PrefetchPlan plan) {}

    public record Result(List<Allocation> allocations, int failedIndex) {}

    public static List<MachineRequirement> startRequirements(List<MachineRequirement> requirements, boolean prefetch) {
        return prefetch ? requirements.stream().filter(r -> r.prefetchEnergyPerTick().isEmpty()).toList() : requirements;
    }

    /** Same requirement order as a real reservation, but all allocations are local to this calculation. */
    public static boolean previewFits(List<MachineRequirement> requirements, int duration, long parallelism,
                                      List<RecipeEnergyPrefetchFacet> facets) {
        var planned = new HashMap<String, Long>();
        for (var requirement : requirements) {
            var energy = requirement.prefetchEnergyPerTick();
            if (energy.isEmpty()) continue;
            if (planned.isEmpty()) {
                for (var facet : facets) {
                    String key = facet.reservationKey();
                    if (key == null || key.isBlank() || planned.put(key, 0L) != null) return false;
                }
            }
            long needed = SaturatingLong.multiply(SaturatingLong.multiply(energy.getAsLong(), duration), parallelism);
            for (var facet : facets) {
                if (needed == 0) break;
                String key = facet.reservationKey();
                long used = planned.get(key);
                long amount = facet.previewPrefetch(needed, used);
                if (amount < 0 || amount > needed || amount > Long.MAX_VALUE - used) return false;
                planned.put(key, used + amount);
                needed -= amount;
            }
            if (needed > 0) return false;
        }
        return true;
    }

    public static Result reserve(List<MachineRequirement> requirements, int duration, long parallelism,
                                 List<RecipeEnergyPrefetchFacet> facets) {
        if (facets.isEmpty()) return new Result(List.of(), -1);
        List<Allocation> reserved = new ArrayList<>();
        boolean complete = false;
        try {
            boolean checkedKeys = false;
            for (int i = 0; i < requirements.size(); i++) {
                var energy = requirements.get(i).prefetchEnergyPerTick();
                if (energy.isEmpty()) continue;
                if (!checkedKeys) {
                    Set<String> keys = new HashSet<>();
                    for (var facet : facets) {
                        String key = facet.reservationKey();
                        if (key == null || key.isBlank() || !keys.add(key)) return new Result(List.of(), i);
                    }
                    checkedKeys = true;
                }
                long remaining = SaturatingLong.multiply(SaturatingLong.multiply(energy.getAsLong(), duration), parallelism);
                for (var facet : facets) {
                    if (remaining <= 0L) break;
                    var candidate = facet.planPrefetch(remaining);
                    if (candidate.isEmpty()) continue;
                    var plan = candidate.get();
                    reserved.add(new Allocation(facet.reservationKey(), facet, plan));
                    if (plan.amount() > remaining) return new Result(List.of(), i);
                    remaining -= plan.amount();
                }
                if (remaining > 0L) return new Result(List.of(), i);
            }
            var result = new Result(List.copyOf(reserved), -1);
            complete = true;
            return result;
        } finally {
            if (!complete) discard(reserved);
        }
    }

    public static void discard(List<Allocation> reserved) {
        for (var allocation : reserved.reversed()) {
            try {
                allocation.facet().restoreReservation(allocation.plan().amount());
            } catch (RuntimeException ex) {
                MMCR.LOG.warn("Unable to restore uncommitted energy reservation {}", allocation.reservationKey(), ex);
            }
        }
    }
}
