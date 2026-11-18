package cn.howxu.mmcr.api.recipe.requirement;

import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.async.AsyncResourceValue;
import cn.howxu.mmcr.api.capability.status.FailureReason;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.function.Function;

/** Server-thread description of a requirement. Only evaluated values may enter a search snapshot.
 * @author howxu <dev@howxu.cn>
 */
public record SearchDemand(Kind kind, long amount, double heat, float chance, float consumeChance,
                           boolean valid, FailureReason failure, @Nullable Output output, @Nullable Matcher matcher,
                           Ports ports) {
    public enum Kind { RESOURCE, SCALAR, TEMPERATURE, HEAT, NOOP }

    public record Output(Object resource, AsyncResourceValue value) {}

    /** Equal keys within a requirement type must select the same set of resources. */
    public record Matcher(Object key, Function<Resources, Set<AsyncResourceValue>> select) {}

    public interface Resources {
        Set<AsyncResourceValue> all(Identifier family);
        Set<AsyncResourceValue> matching(ItemRequirement item);
        Set<AsyncResourceValue> matching(FluidRequirement fluid);
    }

    /** Ports with an equal key within one type and direction must have identical admission policy. */
    public interface Ports {
        Ports STANDARD = new Ports() {};
        default Object key() { return this; }
        default boolean accepts(MachineCapability port) { return true; }
        default Admission admission(MachineCapability port) { return Admission.UNRESTRICTED; }
        default FailureReason failure(FailureReason reason, boolean missing, @Nullable FailureReason rejection) {
            return reason;
        }
    }

    public record Admission(long inputLimit, boolean outputAllowed, @Nullable FailureReason rejection) {
        public static final Admission UNRESTRICTED = new Admission(Long.MAX_VALUE, true, null);
    }

    public static SearchDemand operation(Kind kind, long amount, double heat, FailureReason failure) {
        return new SearchDemand(kind, amount, heat, 1F, 1F, heat >= 0D, failure, null, null, Ports.STANDARD);
    }
}
