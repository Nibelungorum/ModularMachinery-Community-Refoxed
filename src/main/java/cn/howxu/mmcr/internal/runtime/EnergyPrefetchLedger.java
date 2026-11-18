package cn.howxu.mmcr.internal.runtime;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.facet.RecipeEnergyPrefetchFacet;
import cn.howxu.mmcr.api.capability.plan.CapabilityResult;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.recipe.ActiveMachineRecipe;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.internal.recipe.EnergyPrefetchPlanner.Allocation;
import cn.howxu.mmcr.util.SaturatingLong;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/** Owns the per-execution energy reservation balance, including save/restore and transaction rollback.
 * @author howxu <dev@howxu.cn>
 */
final class EnergyPrefetchLedger {
    private List<ActivePrefetch> activePrefetches = List.of();
    private long prefetchedEnergyPerTick;
    private long prefetchedEnergyRemaining;
    private final PrefetchJournal prefetchJournal = new PrefetchJournal();
    private static final String PREFETCH_RESERVATION_KEY = "prefetched_energy_reservation";
    private static final String PREFETCH_ALLOCATIONS_KEY = "prefetched_energy_allocations";
    private static final String PREFETCH_ALLOCATION_KEY = "key";
    private static final String PREFETCH_ALLOCATION_AMOUNT = "amount";

    boolean isEmpty() { return activePrefetches.isEmpty(); }

    static void savePrepared(ActiveMachineRecipe saved, List<Allocation> reserved) {
        if (!reserved.isEmpty()) {
            Map<String, Long> allocations = new LinkedHashMap<>();
            for (Allocation prefetch : reserved) {
                allocations.merge(prefetch.reservationKey(), prefetch.plan().amount(), SaturatingLong::add);
            }
            ListTag tags = new ListTag();
            allocations.forEach((key, amount) -> {
                CompoundTag tag = new CompoundTag();
                tag.putString(PREFETCH_ALLOCATION_KEY, key);
                tag.putLong(PREFETCH_ALLOCATION_AMOUNT, amount);
                tags.add(tag);
            });
            saved.getDataCompound().put(PREFETCH_ALLOCATIONS_KEY, tags);
        }
    }

    void activate(List<Allocation> prefetches, List<MachineRequirement> requirements,
                                    long parallelism) {
        Map<String, ActivePrefetch> allocations = new LinkedHashMap<>();
        for (Allocation prefetch : prefetches) {
            ActivePrefetch existing = allocations.get(prefetch.reservationKey());
            long remaining = existing == null ? prefetch.plan().amount()
                    : SaturatingLong.add(existing.remaining(), prefetch.plan().amount());
            allocations.put(prefetch.reservationKey(), new ActivePrefetch(prefetch.reservationKey(), prefetch.facet(),
                    remaining));
        }
        activePrefetches = List.copyOf(allocations.values());
        prefetchedEnergyPerTick = prefetchedEnergyPerTick(requirements, parallelism);
        prefetchedEnergyRemaining = activePrefetches.stream()
                .mapToLong(ActivePrefetch::remaining).reduce(0L, SaturatingLong::add);
    }

    boolean restore(ActiveMachineRecipe restored, List<MachineRequirement> requirements,
                    Supplier<List<RecipeEnergyPrefetchFacet>> facets, boolean errorPaused) {
        activePrefetches = List.of();
        prefetchedEnergyPerTick = 0L;
        prefetchedEnergyRemaining = 0L;
        CompoundTag data = restored.getDataCompound();
        var storedAllocations = data.getList(PREFETCH_ALLOCATIONS_KEY);
        if (!data.contains(PREFETCH_ALLOCATIONS_KEY)) {
            return !data.getBooleanOr(PREFETCH_RESERVATION_KEY, false);
        }
        if (storedAllocations.isEmpty()) return false;
        List<StoredPrefetch> allocations = new ArrayList<>();
        Set<String> storedKeys = new HashSet<>();
        ListTag allocationList = storedAllocations.get();
        for (int index = 0; index < allocationList.size(); index++) {
            CompoundTag allocation = allocationList.getCompound(index).orElse(null);
            if (allocation == null) return false;
            String reservationKey = allocation.getString(PREFETCH_ALLOCATION_KEY).orElse("");
            if (!(allocation.get(PREFETCH_ALLOCATION_AMOUNT) instanceof LongTag longTag)) return false;
            long amount = longTag.longValue();
            if (reservationKey.isBlank() || !storedKeys.add(reservationKey) || amount < 0L) return false;
            allocations.add(new StoredPrefetch(reservationKey, amount));
        }
        if (allocations.isEmpty()) return false;

        Map<String, RecipeEnergyPrefetchFacet> facetsByKey = new LinkedHashMap<>();
        for (RecipeEnergyPrefetchFacet facet : facets.get()) {
            String reservationKey;
            try {
                reservationKey = facet.reservationKey();
            } catch (RuntimeException exception) {
                return false;
            }
            if (reservationKey == null || reservationKey.isBlank() || facetsByKey.putIfAbsent(reservationKey, facet) != null) {
                return false;
            }
        }
        long perTick = prefetchedEnergyPerTick(requirements, restored.getParallelism());
        long total = SaturatingLong.multiply(perTick, restored.getTotalTick());
        int committedTicks = restored.getTick() + (restored.isFinishPending() ? 1 : 0);
        long remaining = Math.max(0L, total - SaturatingLong.multiply(perTick, committedTicks));
        long allocationTotal = 0L;
        List<ActivePrefetch> restoredPrefetches = new ArrayList<>(allocations.size());
        for (StoredPrefetch allocation : allocations) {
            if (!facetsByKey.containsKey(allocation.reservationKey())) return false;
            try {
                allocationTotal = Math.addExact(allocationTotal, allocation.amount());
            } catch (ArithmeticException exception) {
                return false;
            }
        }
        if (allocationTotal != remaining && !(errorPaused && remaining >= perTick && allocationTotal == remaining - perTick)) return false;
        try {
            for (StoredPrefetch allocation : allocations) {
                RecipeEnergyPrefetchFacet facet = facetsByKey.get(allocation.reservationKey());
                ActivePrefetch restoredPrefetch = new ActivePrefetch(allocation.reservationKey(), facet,
                        allocation.amount());
                restoredPrefetches.add(restoredPrefetch);
                if (allocation.amount() > 0L) facet.restoreReservation(allocation.amount());
            }
        } catch (RuntimeException exception) {
            rollbackRestoredPrefetches(restoredPrefetches);
            return false;
        }
        activePrefetches = List.copyOf(restoredPrefetches);
        prefetchedEnergyPerTick = perTick;
        prefetchedEnergyRemaining = allocationTotal;
        return true;
    }

    void save(ActiveMachineRecipe activeRecipe) {
        if (activeRecipe == null) return;
        CompoundTag data = activeRecipe.getDataCompound();
        data.remove(PREFETCH_RESERVATION_KEY);
        data.remove(PREFETCH_ALLOCATIONS_KEY);
        if (activePrefetches.isEmpty()) return;
        ListTag allocations = new ListTag();
        for (ActivePrefetch prefetch : activePrefetches) {
            CompoundTag allocation = new CompoundTag();
            allocation.putString(PREFETCH_ALLOCATION_KEY, prefetch.reservationKey());
            allocation.putLong(PREFETCH_ALLOCATION_AMOUNT, prefetch.remaining());
            allocations.add(allocation);
        }
        data.put(PREFETCH_ALLOCATIONS_KEY, allocations);
    }

    private static void rollbackRestoredPrefetches(List<ActivePrefetch> restoredPrefetches) {
        for (ActivePrefetch prefetch : restoredPrefetches) {
            if (prefetch.remaining() <= 0L) continue;
            try {
                prefetch.facet().releaseReservation(prefetch.remaining());
            } catch (RuntimeException ignored) {
                // Continue releasing the remaining facets after one rollback failure.
            }
        }
    }

    private static long prefetchedEnergyPerTick(List<MachineRequirement> requirements, long parallelism) {
        long total = 0L;
        for (MachineRequirement requirement : requirements) {
            total = SaturatingLong.add(total, SaturatingLong.multiply(requirement.prefetchEnergyPerTick().orElse(0L), parallelism));
        }
        return total;
    }

    @Nullable ExecutionStatus consume(Supplier<ExecutionStatus> missingEnergy) {
        long remaining = Math.min(prefetchedEnergyPerTick, prefetchedEnergyRemaining);
        if (remaining <= 0L) return null;
        List<ActivePrefetch> next = activePrefetches.size() == 1 ? activePrefetches : new ArrayList<>(activePrefetches.size());
        try (Transaction transaction = Transaction.openRoot()) {
            for (ActivePrefetch prefetch : activePrefetches) {
                long consumed = Math.min(remaining, prefetch.remaining());
                if (consumed > 0L) {
                    CapabilityResult result = prefetch.facet().consumeReservation(consumed, transaction);
                    if (result == null || !result.success()) {
                        return result == null || result.status() == null
                                ? missingEnergy.get() : result.status();
                    }
                }
                ActivePrefetch updated = new ActivePrefetch(prefetch.reservationKey(), prefetch.facet(), prefetch.remaining() - consumed);
                if (activePrefetches.size() == 1) next = List.of(updated);
                else next.add(updated);
                remaining -= consumed;
            }
            prefetchJournal.updateSnapshots(transaction);
            activePrefetches = List.copyOf(next);
            prefetchedEnergyRemaining = Math.max(0L, prefetchedEnergyRemaining - prefetchedEnergyPerTick);
            transaction.commit();
        }
        return null;
    }

    void release(BlockPos controller) {
        List<ActivePrefetch> prefetches = activePrefetches;
        activePrefetches = List.of();
        prefetchedEnergyPerTick = 0L;
        prefetchedEnergyRemaining = 0L;
        for (ActivePrefetch prefetch : prefetches) {
            if (prefetch.remaining() <= 0L) continue;
            try {
                prefetch.facet().releaseReservation(prefetch.remaining());
            } catch (RuntimeException exception) {
                MMCR.LOG.warn("Failed to release prefetched recipe energy: controller={} key={}",
                        controller, prefetch.reservationKey(), exception);
            }
        }
    }

    /** Keeps reservation bookkeeping authoritative during root notification/save reentry. @author howxu <dev@howxu.cn> */
    private final class PrefetchJournal extends SnapshotJournal<PrefetchState> {
        @Override
        protected PrefetchState createSnapshot() { return new PrefetchState(activePrefetches, prefetchedEnergyRemaining); }
        @Override
        protected void revertToSnapshot(PrefetchState state) {
            activePrefetches = state.allocations();
            prefetchedEnergyRemaining = state.remaining();
        }
    }

    /** Transaction-local reservation bookkeeping. @author howxu <dev@howxu.cn> */
    private record PrefetchState(List<ActivePrefetch> allocations, long remaining) { }

    private record StoredPrefetch(String reservationKey, long amount) {
    }

    private record ActivePrefetch(String reservationKey, RecipeEnergyPrefetchFacet facet, long remaining) {
    }
}
