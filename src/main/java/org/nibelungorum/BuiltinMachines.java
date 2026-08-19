package org.nibelungorum;

import org.nibelungorum.builtin.PublicBuiltinDefinitions;

/**
 * Public built-in declaration entrypoint.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class BuiltinMachines {
    private BuiltinMachines() {
    }

    public static void register() {
        PublicBuiltinDefinitions.register();
    }
}
