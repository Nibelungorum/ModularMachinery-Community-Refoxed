package cn.howxu.mmcr.compat.jei;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Map;


import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies JEI structure displays retain only registration-safe metadata.
 *
 * @author howxu <dev@howxu.cn>
 */
class MachineStructureDisplayTest {

    @BeforeAll
    static void bootstrap() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void displayRetainsMachineAndControllerIngredient() {
        Machine machine = testMachine();
        MachineStructureDisplay display = MachineStructureDisplay.from(machine);

        assertThat(display.machine()).isSameAs(machine);
        assertThat(Arrays.stream(MachineStructureDisplay.class.getRecordComponents())
                .map(component -> component.getName()))
                .containsExactly("machine", "ingredients");
        assertThat(display.ingredients()).extracting(ItemStack::getItem)
                .containsExactly(ModBlocks.controllerFor(machine.registryName()).get().asItem());
    }

    @Test
    void ingredientsReturnCopiesThatCannotMutateTheDisplay() {
        MachineStructureDisplay display = MachineStructureDisplay.from(testMachine());

        ItemStack exposed = display.ingredients().getFirst();
        exposed.setCount(42);

        assertThat(display.ingredients().getFirst().getCount()).isOne();
    }

    private static Machine testMachine() {
        return new Machine() {
            @Override public Identifier registryName() { return MMCR.id("blast_furnace"); }
            @Override public BlockArray pattern() {
                return new BlockArray(Map.of());
            }
            @Override public MachineControllerSpec controller() { return MachineControllerSpec.defaultsFor(registryName()); }
        };
    }
}
