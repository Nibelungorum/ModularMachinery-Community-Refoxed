package cn.howxu.mmcr.internal.network;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ControllerSpecSyncTest {
    @Test
    void snapshotDoesNotRequireDeferredControllerHolderBinding() {
        Identifier id = MMCR.id("unbound_snapshot_machine");
        MachineRegistry.register(new Machine() {
            @Override
            public Identifier registryName() {
                return id;
            }

            @Override
            public BlockArray pattern() {
                return new BlockArray(Map.of());
            }

            @Override
            public MachineControllerSpec controller() {
                return MachineControllerSpec.defaultsFor(id);
            }
        });

        assertThat(ControllerSpecSync.createSnapshot()).containsKey(id);
        assertThat(ControllerSpecSync.createAppearanceSnapshot()).containsKey(id);
    }
}
