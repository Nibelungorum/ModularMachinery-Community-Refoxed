package cn.howxu.mmcr.internal.recipe;

import cn.howxu.mmcr.api.capability.async.AsyncCapabilitySnapshot;

import cn.howxu.mmcr.api.capability.MachineCapability;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Server-side capture protocol for immutable, non-scalar worker budgets.
 * @author howxu <dev@howxu.cn>
 */
public interface ConditionCapture {
    boolean searchAvailable();
    default AsyncRequirementPlanner.Requirement commitRequest(int index, long parallelism) { return null; }
    default boolean matchesPort(MachineCapability port) { return true; }
    boolean supportsPort(MachineCapability port, AsyncCapabilitySnapshot value);
    RecipeSearchSnapshot.Rule captureRule(int index, boolean partial, List<Integer> matching, Ports ports);

    final class Ports {
        private final List<MachineCapability> capabilities;
        private final Map<Object, Integer> identities = new IdentityHashMap<>();
        private final Map<Class<?>, Map<Integer, Object>> snapshots = new HashMap<>();
        private final Map<Integer, AsyncCapabilitySnapshot> values;
        Ports(List<MachineCapability> capabilities, Map<Integer, AsyncCapabilitySnapshot> values) {
            this.capabilities = capabilities;
            this.values = values;
        }
        public AsyncCapabilitySnapshot value(int index) { return values.get(index); }
        public MachineCapability get(int index) { return capabilities.get(index); }
        public int identity(Object identity) { return identities.computeIfAbsent(identity, ignored -> identities.size()); }
        public <T> T snapshot(Class<T> type, int index, Supplier<T> capture) {
            return type.cast(snapshots.computeIfAbsent(type, ignored -> new HashMap<>()).computeIfAbsent(index, ignored -> capture.get()));
        }
    }
}
