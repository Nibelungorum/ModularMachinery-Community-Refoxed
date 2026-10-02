package cn.howxu.mmcr.compat.ars_nouveau;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;
import cn.howxu.mmcr.api.capability.status.FailureReason;
import cn.howxu.mmcr.api.capability.status.FailureReasonRegistry;

import java.util.List;

/**
 * Stable failure reasons for source requirements, including an absent Ars runtime.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class SourceFailureReasons {
    public static final FailureReason ARS_UNAVAILABLE = reason("ars_nouveau_unavailable", 0);
    public static final FailureReason INPUT_MISSING = reason(
            "source_input_missing", BuiltinFailureReasons.MISSING_INPUT.priority());
    public static final FailureReason OUTPUT_BLOCKED = reason(
            "source_output_blocked", BuiltinFailureReasons.MISSING_OUTPUT.priority());

    private static final List<FailureReason> ALL = List.of(ARS_UNAVAILABLE, INPUT_MISSING, OUTPUT_BLOCKED);

    private SourceFailureReasons() {
    }

    public static void register() {
        ALL.forEach(FailureReasonRegistry::register);
    }

    private static FailureReason reason(String path, int priority) {
        return new FailureReason(MMCR.id(path), "gui.mmcr.failure." + path, priority);
    }
}
