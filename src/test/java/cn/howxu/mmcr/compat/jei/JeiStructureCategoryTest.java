package cn.howxu.mmcr.compat.jei;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the structure recipe output contract.
 *
 * @author howxu <dev@howxu.cn>
 */
class JeiStructureCategoryTest {
    @BeforeAll
    static void bootstrap() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void structureOutputIsNamedEnchantedBookWithoutEnchantments() {
        Machine machine = testMachine();
        ItemStack output = MachineStructureCategory.structureOutput(MachineStructureDisplay.from(machine));

        assertThat(output.is(Items.ENCHANTED_BOOK)).isTrue();
        assertThat(output.get(DataComponents.CUSTOM_NAME)).isEqualTo(machine.displayName());
        assertThat(output.get(DataComponents.ENCHANTMENTS)).isNull();
    }

    private static Machine testMachine() {
        return new Machine() {
            @Override public Identifier registryName() { return MMCR.id("structure_output_test"); }
            @Override public BlockArray pattern() { return new BlockArray(Map.of()); }
            @Override public MachineControllerSpec controller() { return MachineControllerSpec.defaultsFor(registryName()); }
        };
    }
}
