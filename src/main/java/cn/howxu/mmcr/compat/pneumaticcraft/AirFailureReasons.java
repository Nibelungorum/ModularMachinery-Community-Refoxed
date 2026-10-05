package cn.howxu.mmcr.compat.pneumaticcraft;

import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;
import cn.howxu.mmcr.api.capability.status.FailureReason;
import cn.howxu.mmcr.api.capability.status.FailureReasonRegistry;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

/** Stable failures shared by planning and native operation validation.
 * @author howxu <dev@howxu.cn>
 */
public final class AirFailureReasons {
    public static final FailureReason UNAVAILABLE = reason("pneumaticcraft_unavailable");
    public static final FailureReason MISSING_INTERFACE = reason("missing_air_interface");
    public static final FailureReason INSUFFICIENT_PRESSURE = reason("insufficient_pressure");
    public static final FailureReason INSUFFICIENT_AIR = reason("insufficient_air");
    public static final FailureReason OUTPUT_BLOCKED = reason("air_output_blocked");
    private static final List<FailureReason> ALL = List.of(UNAVAILABLE, MISSING_INTERFACE,
            INSUFFICIENT_PRESSURE, INSUFFICIENT_AIR, OUTPUT_BLOCKED);

    private AirFailureReasons() {
    }

    public static synchronized void register() {
        for (FailureReason reason : ALL) {
            FailureReason existing = FailureReasonRegistry.find(reason.id());
            if (existing == null) FailureReasonRegistry.register(reason);
            else if (existing != reason) throw new IllegalStateException("Conflicting air failure: " + reason.id());
        }
    }

    private static FailureReason reason(String path) {
        return new FailureReason(ResourceLocation.fromNamespaceAndPath("mmcr", path), "failure.mmcr." + path,
                BuiltinFailureReasons.MISSING_ENERGY.priority());
    }
}
