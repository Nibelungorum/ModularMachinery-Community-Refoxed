package cn.howxu.mmcr.internal.runtime;

import cn.howxu.mmcr.api.publicapi.controller.JadeText;

import java.util.Objects;

/**
 * Selects the runtime Jade text implementation for optional integration.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class JadeTextSupport {
    private static volatile boolean enabled;

    private JadeTextSupport() {
    }

    public static void enable() {
        enabled = true;
    }

    public static JadeText create() {
        return enabled ? new JadeTextState() : JadeText.noop();
    }

    public static JadeTextSnapshot snapshot(JadeText jadeText) {
        Objects.requireNonNull(jadeText, "jadeText");
        return jadeText instanceof JadeTextState state ? state.snapshot() : JadeTextSnapshot.empty();
    }

    static void resetForTesting() {
        enabled = false;
    }
}
