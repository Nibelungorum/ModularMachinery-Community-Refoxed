package cn.howxu.mmcr.api.compat.create;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;
import cn.howxu.mmcr.api.capability.status.FailureReason;
import cn.howxu.mmcr.api.capability.status.FailureReasonRegistry;
import java.util.List;

/** Stable failures shared by planner, native ports and recipe lifecycle.
 * @author howxu <dev@howxu.cn>
 */
public final class CreateFailureReasons {
    public static final FailureReason CREATE_UNAVAILABLE = reason("create_unavailable");
    public static final FailureReason MISSING_ROTATION = reason("missing_rotation");
    public static final FailureReason INSUFFICIENT_RPM = reason("insufficient_rpm");
    public static final FailureReason INSUFFICIENT_STRESS = reason("insufficient_stress");
    public static final FailureReason INCOMPATIBLE_OUTPUT_RPM = reason("incompatible_output_rpm");
    public static final FailureReason INVALID_OUTPUT_RPM = reason("invalid_output_rpm");
    public static final FailureReason MISSING_RECIPE_SCOPE = reason("missing_recipe_scope");
    private static final List<FailureReason> ALL = List.of(CREATE_UNAVAILABLE, MISSING_ROTATION,
            INSUFFICIENT_RPM, INSUFFICIENT_STRESS, INCOMPATIBLE_OUTPUT_RPM, INVALID_OUTPUT_RPM,
            MISSING_RECIPE_SCOPE);

    private CreateFailureReasons() {
    }

    public static synchronized void register() {
        for (FailureReason reason : ALL) {
            FailureReason existing = FailureReasonRegistry.find(reason.id());
            if (existing == null) FailureReasonRegistry.register(reason);
            else if (existing != reason) throw new IllegalStateException("Conflicting Create failure: " + reason.id());
        }
    }

    public static boolean isPowerFailure(FailureReason reason) {
        return MISSING_ROTATION.equals(reason) || INSUFFICIENT_RPM.equals(reason)
                || INSUFFICIENT_STRESS.equals(reason);
    }

    private static FailureReason reason(String path) {
        return new FailureReason(MMCR.id(path), "gui.mmcr.failure." + path,
                BuiltinFailureReasons.MISSING_ENERGY.priority());
    }
}
