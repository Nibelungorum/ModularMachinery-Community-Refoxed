package cn.howxu.mmcr.compat.botania;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;
import cn.howxu.mmcr.api.capability.status.FailureReason;
import cn.howxu.mmcr.api.capability.status.FailureReasonRegistry;

import java.util.List;

/**
 * Stable mana diagnostics independent of the optional Botania runtime.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class ManaFailureReasons {
    public static final FailureReason BOTANIA_UNAVAILABLE = reason("botania_unavailable", 0);
    public static final FailureReason INPUT_MISSING = reason(
            "mana_input_missing", BuiltinFailureReasons.MISSING_INPUT.priority());
    public static final FailureReason OUTPUT_BLOCKED = reason(
            "mana_output_blocked", BuiltinFailureReasons.MISSING_OUTPUT.priority());

    private static final List<FailureReason> ALL = List.of(BOTANIA_UNAVAILABLE, INPUT_MISSING, OUTPUT_BLOCKED);

    private ManaFailureReasons() {
    }

    public static void register() {
        ALL.forEach(FailureReasonRegistry::register);
    }

    private static FailureReason reason(String path, int priority) {
        return new FailureReason(MMCR.id(path), "gui.mmcr.failure." + path, priority);
    }
}
