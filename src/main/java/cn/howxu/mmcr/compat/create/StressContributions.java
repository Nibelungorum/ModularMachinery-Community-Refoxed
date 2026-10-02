package cn.howxu.mmcr.compat.create;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;

/** Main-thread contribution ledger, keyed by lane identity and original recipe index.
 * @author howxu <dev@howxu.cn>
 */
public final class StressContributions {
    private final Map<StressSession, Map<Integer, Contribution>> contributions = new IdentityHashMap<>();

    /** One local base load/capacity and optional signed generated rotation.
     * @author howxu <dev@howxu.cn>
     */
    public record Contribution(double baseStress, double generatedRpm) {
        public Contribution {
            if (!positiveFloat(baseStress) || !Double.isFinite(generatedRpm)
                    || !Float.isFinite((float) generatedRpm) || generatedRpm != 0D && (float) generatedRpm == 0F) {
                throw new IllegalArgumentException("Invalid Create contribution");
            }
        }
    }

    public boolean accepts(StressSession session, int index, double generatedRpm) {
        if (!Double.isFinite(generatedRpm) || !Float.isFinite((float) generatedRpm)
                || generatedRpm != 0D && (float) generatedRpm == 0F) return false;
        if (generatedRpm == 0D) return true;
        for (var owner : contributions.entrySet()) {
            for (var entry : owner.getValue().entrySet()) {
                if (owner.getKey() == session && entry.getKey() == index) continue;
                double other = entry.getValue().generatedRpm();
                if (other != 0D && other != generatedRpm) return false;
            }
        }
        return true;
    }

    public void replace(StressSession session, int index, Contribution contribution) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(contribution, "contribution");
        if (!accepts(session, index, contribution.generatedRpm())) {
            throw new IllegalArgumentException("Conflicting generated RPM");
        }
        Contribution previous = get(session, index);
        double total = baseStress() - (previous == null ? 0D : previous.baseStress()) + contribution.baseStress();
        if (!positiveFloat(total)) throw new IllegalArgumentException("Create base contribution overflow");
        contributions.computeIfAbsent(session, ignored -> new HashMap<>()).put(index, contribution);
    }

    public Contribution get(StressSession session, int index) {
        var owned = contributions.get(session);
        return owned == null ? null : owned.get(index);
    }

    public void release(StressSession session) {
        contributions.remove(session);
    }

    public void release(StressSession session, int index) {
        var owned = contributions.get(session);
        if (owned == null) return;
        owned.remove(index);
        if (owned.isEmpty()) contributions.remove(session);
    }

    public double baseStress() {
        double total = 0D;
        for (var owned : contributions.values()) {
            for (var contribution : owned.values()) total += contribution.baseStress();
        }
        return total;
    }

    public double generatedRpm() {
        for (var owned : contributions.values()) {
            for (var contribution : owned.values()) {
                if (contribution.generatedRpm() != 0D) return contribution.generatedRpm();
            }
        }
        return 0D;
    }

    static boolean positiveFloat(double value) {
        return Double.isFinite(value) && value > 0D && Float.isFinite((float) value) && (float) value > 0F;
    }
}
