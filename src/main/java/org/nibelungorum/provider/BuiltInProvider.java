package org.nibelungorum.provider;

import cn.howxu.mmcr.api.publicapi.MachineDefinitionProvider;
import cn.howxu.mmcr.api.publicapi.event.MMCRMachineDefinationsEvent;
import org.nibelungorum.builtin.*;

/** Provides built-in machine definitions before dynamic controller registration.
 * @author howxu <dev@howxu.cn>
 */
public final class BuiltInProvider implements MachineDefinitionProvider {
    @Override
    public void register(MMCRMachineDefinationsEvent event) {
        BLAST_FURNACE.registerDefinitions(event);
        ALLOY_FURNACE.registerDefinitions(event);
        CRACKER.registerDefinitions(event);
        THERMAL_SMELTING_FURNACE.registerDefinitions(event);
        PURPUR_FURNACE.registerDefinitions(event);
        DISTILLATION_TOWER.registerDefinitions(event);
        SPACE.registerDefinitions(event);
        MONSTER_FARM.registerDefinitions(event);
    }
}
