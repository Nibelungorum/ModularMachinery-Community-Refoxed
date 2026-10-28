package cn.howxu.mmcr.internal.recipe;

import cn.howxu.mmcr.api.compat.create.CreateFailureReasons;
import cn.howxu.mmcr.api.compat.create.StressState;
import cn.howxu.mmcr.api.compat.pneumaticcraft.AirState;
import cn.howxu.mmcr.compat.pneumaticcraft.AirFailureReasons;
import cn.howxu.mmcr.internal.recipe.RecipeSearchSnapshot.Rule;
import cn.howxu.mmcr.util.IOType;
import cn.howxu.mmcr.util.SaturatingLong;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Pure-value search constraints for resources that cannot be represented by unsigned scalar storage.
 * @author howxu <dev@howxu.cn>
 */
final class CompatSearchValues {
    private CompatSearchValues() { }

    sealed interface Condition permits Air, Stress { }

    record Air(float minPressure, List<AirPort> ports) implements Condition {
        Air { ports = List.copyOf(ports); }
    }

    record AirPort(int id, int sourceIndex, long air, int volume, long capacity) { }

    static AirPort airPort(int id, int sourceIndex, AirState state) {
        return new AirPort(id, sourceIndex, state.air(), state.volume(), Math.min(Integer.MAX_VALUE,
                (long) Math.floor((double) state.dangerPressure() * state.volume())));
    }

    record Stress(double base, double minRpm, double rpm, double maxRpm, List<StressPort> ports) implements Condition {
        Stress { ports = List.copyOf(ports); }
    }

    record StressPort(int id, int network, double actualRpm, double theoreticalRpm, double baseContribution,
                      double networkCapacity, double networkStress, boolean connected, boolean overstressed,
                      boolean stressEnabled, boolean acceptsRpm) { }

    static StressPort stressPort(int id, int network, StressState state, boolean stressEnabled, boolean acceptsRpm) {
        return new StressPort(id, network, state.actualRpm(), state.theoreticalRpm(), state.baseContribution(),
                state.networkCapacity(), state.networkStress(), state.connected(), state.overstressed(),
                stressEnabled, acceptsRpm);
    }

    /** Attempt-local deltas; IDs are main-thread-assigned physical identities, never native objects.
     * @author howxu <dev@howxu.cn>
     */
    static final class Budget {
        final Map<Integer, Long> air = new HashMap<>();
        final Map<Integer, Double> stress = new HashMap<>();
        final Map<Integer, Double> rpm = new HashMap<>();

        void clear() {
            air.clear();
            stress.clear();
            rpm.clear();
        }
    }

    static @Nullable ResourceLocation reserveAir(Rule rule, long parallelism, long consumedBatches, Budget budget) {
        Air air = (Air) rule.condition();
        if (air.ports().isEmpty()) return AirFailureReasons.MISSING_INTERFACE.id();
        boolean insert = rule.direction() == IOType.OUTPUT;
        long requested = SaturatingLong.multiply(rule.amount(), parallelism);
        long remaining = requested;
        boolean pressureMet = false;
        List<AirAllocation> allocations = new ArrayList<>();
        for (AirPort port : air.ports()) {
            long current = port.air() + budget.air.getOrDefault(port.id(), 0L);
            long available = Math.max(0L, insert ? port.capacity() - current : current);
            if (!insert) {
                long virtualAir = available == 0L ? Math.min(0L, current) : available;
                if ((float) virtualAir / port.volume() < air.minPressure()) continue;
                pressureMet = true;
            }
            long moved = Math.min(Integer.MAX_VALUE, Math.min(remaining, available));
            if (requested == 0L) {
                allocations.add(new AirAllocation(port, 0L));
                break;
            }
            if (insert && port.capacity() - available + moved < -(long) port.volume()) continue;
            if (moved <= 0L) continue;
            allocations.add(new AirAllocation(port, moved));
            remaining -= moved;
            if (remaining == 0L) break;
        }
        if (allocations.isEmpty() || remaining > 0L && !(insert && rule.partial())) {
            return (insert ? AirFailureReasons.OUTPUT_BLOCKED
                    : pressureMet ? AirFailureReasons.INSUFFICIENT_AIR : AirFailureReasons.INSUFFICIENT_PRESSURE).id();
        }
        long consumed = insert ? requested : SaturatingLong.multiply(rule.amount(), consumedBatches);
        for (AirAllocation allocation : allocations) {
            long moved = Math.min(consumed, allocation.amount());
            budget.air.merge(allocation.port().id(), insert ? moved : -moved, Long::sum);
            consumed -= moved;
        }
        return null;
    }

    static @Nullable ResourceLocation reserveStress(Rule rule, long parallelism, Budget budget) {
        Stress stress = (Stress) rule.condition();
        boolean input = rule.direction() == IOType.INPUT;
        if (!input && (!Double.isFinite(stress.rpm()) || !finiteNonzeroFloat(stress.rpm())
                || !Double.isFinite(stress.maxRpm()) || stress.maxRpm() <= 0D
                || Math.abs(stress.rpm()) > stress.maxRpm())) return CreateFailureReasons.INVALID_OUTPUT_RPM.id();
        ResourceLocation failure = (input ? CreateFailureReasons.MISSING_ROTATION
                : CreateFailureReasons.INCOMPATIBLE_OUTPUT_RPM).id();
        double remaining = stress.base() * parallelism;
        if (!Double.isFinite(remaining) || remaining <= 0D) return CreateFailureReasons.INSUFFICIENT_STRESS.id();
        for (StressPort port : stress.ports()) {
            if (input) {
                if (port.network() < 0 || !port.connected()) {
                    failure = CreateFailureReasons.MISSING_ROTATION.id();
                    continue;
                }
                if (port.overstressed()) {
                    failure = CreateFailureReasons.INSUFFICIENT_STRESS.id();
                    continue;
                }
                if (!finiteNonzeroFloat(port.actualRpm()) || !finiteNonzeroFloat(port.theoreticalRpm())) {
                    failure = CreateFailureReasons.MISSING_ROTATION.id();
                    continue;
                }
                if (Math.abs(port.actualRpm()) < stress.minRpm()) {
                    failure = CreateFailureReasons.INSUFFICIENT_RPM.id();
                    continue;
                }
            } else if (!port.acceptsRpm() || budget.rpm.containsKey(port.id())
                    && budget.rpm.get(port.id()) != stress.rpm()) continue;
            double rpm = input ? port.theoreticalRpm() : stress.rpm();
            if (!finiteNonnegativeFloat(port.baseContribution())) {
                failure = CreateFailureReasons.INSUFFICIENT_STRESS.id();
                continue;
            }
            float speed = Math.abs((float) rpm);
            float limit = (float) Math.min((double) Float.MAX_VALUE, (double) Float.MAX_VALUE / speed);
            if (!Float.isFinite(limit * speed)) limit = Math.nextDown(limit);
            double availableBase = limit - port.baseContribution();
            double reservedBase = budget.stress.getOrDefault(port.id(), 0D);
            double base = Math.min(remaining, Math.max(0D, availableBase - reservedBase));
            double availableActual = 0D;
            if (input) {
                if (!finiteNonnegativeFloat(port.networkCapacity()) || !finiteNonnegativeFloat(port.networkStress())) {
                    failure = CreateFailureReasons.INSUFFICIENT_STRESS.id();
                    continue;
                }
                double networkLimit = port.stressEnabled() ? port.networkCapacity() : Float.MAX_VALUE;
                availableActual = networkLimit - port.networkStress();
                base = Math.min(base, Math.max(0D, availableActual - budget.stress.getOrDefault(port.network(), 0D))
                        / Math.abs(port.theoreticalRpm()));
            }
            double next = reservedBase + base;
            double aggregate = port.baseContribution() + next;
            if (!positiveFloat(base) || next > availableBase || !positiveFloat(aggregate)
                    || !positiveFloat((float) aggregate * Math.abs((float) rpm))) {
                failure = CreateFailureReasons.INSUFFICIENT_STRESS.id();
                continue;
            }
            if (input) {
                double actual = base * Math.abs(rpm);
                double total = budget.stress.getOrDefault(port.network(), 0D) + actual;
                if (!Double.isFinite(total) || total > availableActual) {
                    failure = CreateFailureReasons.INSUFFICIENT_STRESS.id();
                    continue;
                }
                budget.stress.put(port.network(), total);
            }
            budget.stress.put(port.id(), next);
            if (!input) budget.rpm.put(port.id(), stress.rpm());
            remaining -= base;
            if (remaining <= 0D) return null;
        }
        return failure;
    }

    private static boolean finiteNonnegativeFloat(double value) {
        return Double.isFinite(value) && value >= 0D && Float.isFinite((float) value);
    }

    private static boolean finiteNonzeroFloat(double value) {
        return Double.isFinite(value) && Float.isFinite((float) value) && (float) value != 0F;
    }

    private static boolean positiveFloat(double value) {
        return finiteNonnegativeFloat(value) && (float) value > 0F;
    }

    /** @author howxu <dev@howxu.cn> */
    private record AirAllocation(AirPort port, long amount) { }
}
