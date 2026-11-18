package cn.howxu.mmcr.compat.pneumaticcraft;

import cn.howxu.mmcr.internal.recipe.CompatSearchValues;
import cn.howxu.mmcr.internal.recipe.RecipeSearchSnapshot.Rule;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.resources.ResourceLocation;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import cn.howxu.mmcr.api.compat.pneumaticcraft.AirState;
import java.util.ArrayList;

/** Pressure and signed volume constraints captured without a live pneumatic handler.
 * @author howxu <dev@howxu.cn>
 */
public record AirSearchCondition(float minPressure, List<AirPort> ports) implements CompatSearchValues.Condition {
    public AirSearchCondition { ports = List.copyOf(ports); }
    @Override public boolean samplesQuantity() { return true; }
    private static final class Deltas { final Map<Integer, Long> air = new HashMap<>(); }
    public record AirPort(int id, int sourceIndex, long air, int volume, long capacity) { }

    public static AirPort airPort(int id, int sourceIndex, AirState state) {
        return new AirPort(id, sourceIndex, state.air(), state.volume(), Math.min(Integer.MAX_VALUE,
                (long) Math.floor((double) state.dangerPressure() * state.volume())));
    }

    @Override public ResourceLocation reserve(Rule rule, long parallelism, long requested, long consumedAmount, CompatSearchValues.Budget state) {
        var budget = state.get(Deltas.class, Deltas::new);
        boolean insert = rule.direction() == IOType.OUTPUT;
        if (insert && requested == 0L && rule.amount() > 0L) return null;
        if (ports.isEmpty()) return AirFailureReasons.MISSING_INTERFACE.id();
        long remaining = requested;
        boolean pressureMet = false;
        List<AirAllocation> allocations = new ArrayList<>();
        for (AirPort port : ports) {
            long current = port.air() + budget.air.getOrDefault(port.id(), 0L);
            long available = Math.max(0L, insert ? port.capacity() - current : current);
            if (!insert) {
                long virtualAir = available == 0L ? Math.min(0L, current) : available;
                if ((float) virtualAir / port.volume() < minPressure) continue;
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
        long consumed = insert ? requested : consumedAmount;
        for (AirAllocation allocation : allocations) {
            long moved = Math.min(consumed, allocation.amount());
            budget.air.merge(allocation.port().id(), insert ? moved : -moved, Long::sum);
            consumed -= moved;
        }
        return null;
    }
    private record AirAllocation(AirPort port, long amount) {}
}
