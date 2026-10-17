package cn.howxu.mmcr.compat.ae2lt;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;
import cn.howxu.mmcr.api.capability.status.FailureReason;
import cn.howxu.mmcr.api.capability.status.FailureReasonRegistry;
import java.util.List;

/** Native-independent diagnostics for lightning planning.
 * @author howxu <dev@howxu.cn>
 */
public final class LightningFailureReasons {
    public static final FailureReason UNAVAILABLE = reason("lightning_unavailable", 0);
    public static final FailureReason INPUT_MISSING = reason("lightning_input_missing", BuiltinFailureReasons.MISSING_INPUT.priority());
    public static final FailureReason OUTPUT_BLOCKED = reason("lightning_output_blocked", BuiltinFailureReasons.MISSING_OUTPUT.priority());

    private LightningFailureReasons() {}

    public static void register() {
        List.of(UNAVAILABLE, INPUT_MISSING, OUTPUT_BLOCKED).forEach(FailureReasonRegistry::register);
    }

    private static FailureReason reason(String path, int priority) {
        return new FailureReason(MMCR.id(path), "gui.mmcr.failure." + path, priority);
    }
}
