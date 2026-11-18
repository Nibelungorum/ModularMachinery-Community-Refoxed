package cn.howxu.mmcr.compat.create;

import cn.howxu.mmcr.internal.recipe.CompatSearchValues;
import cn.howxu.mmcr.internal.recipe.RecipeSearchSnapshot.Rule;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.resources.ResourceLocation;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import cn.howxu.mmcr.api.compat.create.CreateFailureReasons;
import cn.howxu.mmcr.api.compat.create.StressState;

/** Numeric network constraints captured without a live Create network.
 * @author howxu <dev@howxu.cn>
 */
public record StressSearchCondition(double base, double minRpm, double rpm, double maxRpm, List<StressPort> ports)
        implements CompatSearchValues.Condition {
    public StressSearchCondition { ports = List.copyOf(ports); }
    private static final class Deltas {
        final Map<Integer, Double> stress = new HashMap<>();
        final Map<Integer, Double> rpm = new HashMap<>();
    }

    public record StressPort(int id, int network, double actualRpm, double theoreticalRpm, double baseContribution,
                      double networkCapacity, double networkStress, boolean connected, boolean overstressed,
                      boolean stressEnabled, boolean acceptsRpm) { }

    public static StressPort stressPort(int id, int network, StressState state, boolean stressEnabled, boolean acceptsRpm) {
        return new StressPort(id, network, state.actualRpm(), state.theoreticalRpm(), state.baseContribution(),
                state.networkCapacity(), state.networkStress(), state.connected(), state.overstressed(),
                stressEnabled, acceptsRpm);
    }

    @Override public ResourceLocation reserve(Rule rule, long parallelism, long requested, long consumedAmount, CompatSearchValues.Budget state) {
        var budget = state.get(Deltas.class, Deltas::new);
        boolean input = rule.direction() == IOType.INPUT;
        if (!input && (!Double.isFinite(rpm) || !finiteNonzeroFloat(rpm)
                || !Double.isFinite(maxRpm) || maxRpm <= 0D
                || Math.abs(rpm) > maxRpm)) return CreateFailureReasons.INVALID_OUTPUT_RPM.id();
        ResourceLocation failure = (input ? CreateFailureReasons.MISSING_ROTATION
                : CreateFailureReasons.INCOMPATIBLE_OUTPUT_RPM).id();
        double remaining = base * parallelism;
        if (!Double.isFinite(remaining) || remaining <= 0D) return CreateFailureReasons.INSUFFICIENT_STRESS.id();
        for (StressPort port : ports) {
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
                if (Math.abs(port.actualRpm()) < minRpm) {
                    failure = CreateFailureReasons.INSUFFICIENT_RPM.id();
                    continue;
                }
            } else if (!port.acceptsRpm() || budget.rpm.containsKey(port.id())
                    && budget.rpm.get(port.id()) != rpm) continue;
            double rpm = input ? port.theoreticalRpm() : this.rpm;
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
            if (!input) budget.rpm.put(port.id(), rpm);
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
}
