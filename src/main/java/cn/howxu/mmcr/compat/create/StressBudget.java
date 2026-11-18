package cn.howxu.mmcr.compat.create;

import cn.howxu.mmcr.api.capability.plan.PlanningReservations;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

/** Trial allocations and reclaimable contributions for one planning branch.
 * @author howxu <dev@howxu.cn>
 */
public final class StressBudget {
    private StressBudget() {}
    public static StressBudget of(PlanningReservations reservations) { return reservations.state(KEY); }

    static final PlanningReservations.StateKey<StressBudget> KEY = new PlanningReservations.StateKey<>() {
        public StressBudget create() { return new StressBudget(); }
        public StressBudget copy(StressBudget state) { return state.copy(); }
    };

    private Map<Object, Double> stress;
    private Map<Object, Double> generatedRpm;
    private Map<Object, Map<Object, Map<Integer, Double>>> stressCredits;

    public double reservedStress(Object identity) {
        return stress == null ? 0D : stress.getOrDefault(identity, 0D);
    }

    public boolean reserveStress(Object identity, double amount, double available) {
        if (identity == null || !Double.isFinite(amount) || amount < 0D || !Double.isFinite(available)) return false;
        double next = reservedStress(identity) + amount;
        if (!Double.isFinite(next) || next > available) return false;
        if (stress == null) stress = new IdentityHashMap<>();
        stress.put(identity, next);
        return true;
    }

    /** Credit once per identity/owner/index: actual load for networks, base load for stress facets. */
    public boolean creditStress(Object identity, Object owner, int index, double amount) {
        if (identity == null || owner == null || !Double.isFinite(amount) || amount < 0D) return false;
        if (stressCredits == null) stressCredits = new IdentityHashMap<>();
        stressCredits.computeIfAbsent(identity, ignored -> new IdentityHashMap<>())
                .computeIfAbsent(owner, ignored -> new HashMap<>()).putIfAbsent(index, amount);
        return Double.isFinite(creditedStress(identity));
    }

    public double creditedStress(Object identity) {
        var owners = stressCredits == null ? null : stressCredits.get(identity);
        if (owners == null) return 0D;
        double total = 0D;
        for (var indexes : owners.values()) for (double amount : indexes.values()) total += amount;
        return total;
    }

    public boolean reserveGeneratedRpm(Object identity, double rpm) {
        if (!acceptsGeneratedRpm(identity, rpm)) return false;
        if (generatedRpm == null) generatedRpm = new IdentityHashMap<>();
        generatedRpm.put(identity, rpm);
        return true;
    }

    public boolean acceptsGeneratedRpm(Object identity, double rpm) {
        if (identity == null || !Double.isFinite(rpm) || rpm == 0D || !Float.isFinite((float) rpm)
                || (float) rpm == 0F) return false;
        Double previous = generatedRpm == null ? null : generatedRpm.get(identity);
        return previous == null || previous == rpm;
    }

    private StressBudget copy() {
        var copy = new StressBudget();
        if (stress != null) copy.stress = new IdentityHashMap<>(stress);
        if (generatedRpm != null) copy.generatedRpm = new IdentityHashMap<>(generatedRpm);
        if (stressCredits != null) {
            copy.stressCredits = new IdentityHashMap<>();
            stressCredits.forEach((identity, owners) -> {
                Map<Object, Map<Integer, Double>> copiedOwners = new IdentityHashMap<>();
                owners.forEach((owner, indexes) -> copiedOwners.put(owner, new HashMap<>(indexes)));
                copy.stressCredits.put(identity, copiedOwners);
            });
        }
        return copy;
    }
}
