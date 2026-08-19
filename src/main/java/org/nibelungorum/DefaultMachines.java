package org.nibelungorum;

import cn.howxu.mmcr.api.publicapi.machine.MachineDefinition;
import org.nibelungorum.builtin.PublicBuiltinDefinitions;

import java.util.Map;
import net.minecraft.resources.Identifier;

/**
 * Public built-in machine declarations.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class DefaultMachines {
    private DefaultMachines() {
    }

    public static Map<Identifier, MachineDefinition> definitions() {
        return PublicBuiltinDefinitions.machineDefinitions();
    }
}
