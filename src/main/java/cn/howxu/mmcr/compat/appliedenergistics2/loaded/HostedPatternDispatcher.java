package cn.howxu.mmcr.compat.appliedenergistics2.loaded;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.stacks.GenericStack;
import appeng.helpers.externalstorage.GenericStackInv;
import cn.howxu.mmcr.api.capability.plan.PlanningReservations;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.adapter.AE2ResourceFamilies;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.storage.HostedPatternInventory;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.storage.HostedPatternInventory.Group;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.PatternInterfaceBlockEntity;
import cn.howxu.mmcr.internal.runtime.PatternStartReservation;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/** Coordinates admission and timing only. Each controller selects its own recipe and its own lane.
 * @author HowXu {@literal <dev@howxu.cn>}
 */
public final class HostedPatternDispatcher {
    private static final Map<ServerLevel, Set<BlockPos>> PENDING = new WeakHashMap<>();
    private final PatternInterfaceBlockEntity host;
    private final Map<AEItemKey, Map<BlockPos, Rejected>> rejected = new HashMap<>();
    private boolean working;

    public HostedPatternDispatcher(PatternInterfaceBlockEntity host) { this.host = host; }
    public boolean working() { return working; }
    public void networkChanged() { rejected.clear(); }

    public static void enqueue(ServerLevel level, BlockPos pos) {
        PENDING.computeIfAbsent(level, ignored -> new LinkedHashSet<>()).add(pos);
    }

    /** After the block entities publish their execution permits, never load an absent controller or host. */
    public static void flush(ServerLevel level) {
        Set<BlockPos> pending = PENDING.remove(level);
        if (pending == null) return;
        for (BlockPos pos : pending) {
            if (level.hasChunkAt(pos) && level.getBlockEntity(pos) instanceof PatternInterfaceBlockEntity host) {
                host.dispatchHostedPatterns();
            }
        }
    }

    public boolean hasLane(AEItemKey key, List<MachineOutput> hints) {
        for (var controller : host.hostedControllers()) {
            if (controller.probeHostedPattern(host.getBlockPos(), hints, null, excluded(key, controller)) != null) return true;
        }
        return false;
    }

    public void run(long now) {
        if (working) return;
        working = true;
        try {
            var inventory = host.hostedPatterns();
            inventory.rebaseClock(now);
            var controllers = host.hostedOnline() ? host.hostedControllers() : List.<MachineControllerBlockEntity>of();
            for (Group initial : inventory.groups()) {
                if (initial.gate() == HostedPatternInventory.Gate.REFUND) continue;
                var hints = PatternInterfaceCraftingMachine.outputs(initial.outputs());
                if (hints.isEmpty()) continue;
                boolean waiting = false;
                for (var controller : controllers) {
                    Group group;
                    while ((group = inventory.group(initial.definition())) != null
                            && group.gate() != HostedPatternInventory.Gate.REFUND && host.hostedOnline()) {
                        var request = new Request(host, group);
                        var probe = controller.probeHostedPattern(host.getBlockPos(), hints, request.capabilities,
                                excluded(group.definition(), controller));
                        if (probe == null) break;
                        Group current = inventory.eligibility(group, true, now);
                        if (!PatternAggregationPolicy.shouldStart(probe.payable(), probe.laneLimit(),
                                now - current.lastDelivery(), current.eligibleAge(now))) {
                            waiting = true;
                            break;
                        }
                        if (!start(controller, probe, hints, current, request)) {
                            if (inventory.group(current.definition()) != current) break;
                            inventory.suspend(current, now);
                            reject(group.definition(), controller, probe.recipeId());
                            // Other recipes with the same hint, and other controllers, still get their chance.
                            continue;
                        }
                        waiting = false; // A payment changed the snapshot any earlier probe observed.
                    }
                }
                Group remaining = inventory.group(initial.definition());
                if (remaining != null && !waiting) inventory.eligibility(remaining, false, now);
            }
            rejected.keySet().removeIf(key -> inventory.group(key) == null);
        } finally {
            working = false;
        }
    }

    private boolean start(MachineControllerBlockEntity controller, MachineControllerBlockEntity.PatternProbe probe,
                          List<MachineOutput> hints, Group group, Request request) {
        var inventory = host.hostedPatterns();
        if (!host.hostedOnline() || inventory.group(group.definition()) != group) return false;
        try (var reservation = controller.reserveHostedPatternStart(host.getBlockPos(), probe.recipeId(), hints,
                probe.payable(), request.capabilities)) {
            if (reservation.status() != PatternStartReservation.Status.RESERVED || inventory.group(group.definition()) != group) return false;
            if (!reservation.debitsEntrustedInput()) return false;
            request.expected = group;
            request.payer = controller;
            return reservation.guard(request::current).commit();
        }
    }

    private Set<ResourceLocation> excluded(AEItemKey key, MachineControllerBlockEntity controller) {
        var failures = rejected.get(key);
        if (failures == null) return Set.of();
        var last = failures.get(controller.getBlockPos());
        if (last == null) return Set.of();
        Group group = host.hostedPatterns().group(key);
        if (group != null && last.materials == group.materials() && last.stamp.equals(Stamp.of(controller))) return last.recipes;
        failures.remove(controller.getBlockPos());
        return Set.of();
    }

    private void reject(AEItemKey key, MachineControllerBlockEntity controller, ResourceLocation recipe) {
        var failures = rejected.computeIfAbsent(key, ignored -> new HashMap<>());
        var stamp = Stamp.of(controller);
        var last = failures.get(controller.getBlockPos());
        Group group = host.hostedPatterns().group(key);
        if (group == null) return;
        if (last == null || last.materials != group.materials() || !last.stamp.equals(stamp)) {
            failures.put(controller.getBlockPos(), last = new Rejected(stamp, group.materials(), new HashSet<>()));
        }
        last.recipes.add(recipe);
    }

    private record Rejected(Stamp stamp, Map<AEKey, Long> materials, Set<ResourceLocation> recipes) {}
    private record Stamp(long life, long structure, long caps, long modifiers, long state, long resources, long content, long upgrades, long catalog) {
        static Stamp of(MachineControllerBlockEntity controller) {
            var runtime = controller.currentRuntimeSnapshot();
            return new Stamp(controller.lifecycleEpoch(), runtime.structure().version(), runtime.capabilityVersion(),
                    runtime.modifierVersion(), runtime.stateVersion(), controller.resourceAvailabilityEpoch(),
                    controller.componentRuntime().capabilityContentRevision(), runtime.upgradeContentRevision(),
                    RecipeRegistry.catalogForPool(controller.currentRecipePoolId()).version());
        }
    }

    /** All installed resource families see the same slots. A native debit is irreversible, so
     * update escrow on each real extraction, before another handler can save or dismantle the port. */
    private static final class Request extends GenericStackInv implements PlanningReservations.EntrustedInput {
        final List<MachineCapability> capabilities;
        final PatternInterfaceBlockEntity host;
        Group expected;
        MachineControllerBlockEntity payer;

        Request(PatternInterfaceBlockEntity host, Group group) {
            super(null, group.materials().size());
            this.host = host;
            this.expected = group;
            int slot = 0;
            for (var entry : group.materials().entrySet()) {
                setStack(slot++, new GenericStack(entry.getKey(), entry.getValue()));
            }
            capabilities = AE2ResourceFamilies.requestCapabilities(this, group.requestCounters());
        }

        @Override public long getMaxAmount(AEKey key) { return Long.MAX_VALUE; }
        @Override public boolean canInsert() { return false; }

        boolean current() {
            return payer != null && expected != null && host.hostedOnline() && host.linkedControllerPositions().contains(payer.getBlockPos())
                    && host.hostedPatterns().group(expected.definition()) == expected;
        }

        @Override public long extract(int slot, AEKey key, long amount, Actionable mode) {
            if (mode == Actionable.MODULATE && !current()) return 0;
            long moved = super.extract(slot, key, amount, mode);
            if (mode == Actionable.MODULATE && moved > 0) {
                var debit = new KeyCounter();
                debit.add(key, moved);
                host.hostedPatterns().consume(expected, debit, mode);
                expected = host.hostedPatterns().group(expected.definition());
            }
            return moved;
        }
    }
}
