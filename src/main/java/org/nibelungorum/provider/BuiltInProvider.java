package org.nibelungorum.provider;

import cn.howxu.mmcr.publicapi.registration.MachineDefinitionProvider;
import cn.howxu.mmcr.publicapi.event.RegisterMachineDefinitionsEvent;
import org.nibelungorum.builtin.*;

/** Provides built-in machine definitions before dynamic controller registration.
 * @author howxu <dev@howxu.cn>
 */
public final class BuiltInProvider implements MachineDefinitionProvider {
    @Override
    public void register(RegisterMachineDefinitionsEvent event) {
        BLAST_FURNACE.registerDefinitions(event);
        ALLOY_FURNACE.registerDefinitions(event);
        CRACKER.registerDefinitions(event);
        THERMAL_SMELTING_FURNACE.registerDefinitions(event);
        PURPUR_FURNACE.registerDefinitions(event);
        DISTILLATION_TOWER.registerDefinitions(event);
        SPACE.registerDefinitions(event);
        MONSTER_FARM.registerDefinitions(event);
        ARTIFICIAL_STAR.registerDefinitions(event);
        REACTOR.registerDefinitions(event);
        DATA_STORAGE_MACHINE.registerDefinitions(event);
        NETWORK_PRODUCER_MACHINE.registerDefinitions(event);
        NETWORK_CENTER_MACHINE.registerDefinitions(event);
        PURE_TICK_MACHINE.registerDefinitions(event);
        RECIPE_TICKER.registerDefinitions(event);
    }
}
