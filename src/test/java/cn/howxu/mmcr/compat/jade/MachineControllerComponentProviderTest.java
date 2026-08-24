package cn.howxu.mmcr.compat.jade;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies Jade only displays controller-owned information.
 *
 * @author howxu <dev@howxu.cn>
 */
class MachineControllerComponentProviderTest {

    @Test
    void lineKeysDoNotIncludeMachineIdentifier() {
        CompoundTag tag = new CompoundTag();
        tag.putString("machine", "mmcr:machine");

        assertThat(MachineControllerComponentProvider.lineKeys(
                MachineControllerComponentProvider.Snapshot.from(tag)))
                .doesNotContain("machine");
    }
}
