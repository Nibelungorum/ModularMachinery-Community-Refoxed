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

    @Test
    void lineKeysOnlyIncludeStructureForTickMachine() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("tickMachine", true);
        tag.putBoolean("formed", true);
        tag.putBoolean("active", true);
        tag.putString("activeRecipe", "mmcr:recipe");

        assertThat(MachineControllerComponentProvider.lineKeys(
                MachineControllerComponentProvider.Snapshot.from(tag)))
                .containsExactly("structure");
    }
}
