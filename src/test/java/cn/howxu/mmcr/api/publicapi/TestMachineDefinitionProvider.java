package cn.howxu.mmcr.api.publicapi;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.publicapi.machine.BlockPredicate;
import cn.howxu.mmcr.api.publicapi.machine.MachineBuilder;
import net.minecraft.world.level.block.Blocks;

/**
 * Service-loaded test provider for the startup lifecycle.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class TestMachineDefinitionProvider implements MachineDefinitionProvider {
    @Override
    public void register() {
        MachineApi.registerMachine(MachineBuilder.machine(MMCR.id("service_loaded_machine")).build());
    }
}
