package cn.howxu.mmcr.compat.jei;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the JEI structure usage ingredients match the resolved preview.
 *
 * @author howxu <dev@howxu.cn>
 */
class MachineStructureDisplayTest {

    @BeforeAll
    static void bootstrap() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void displayIncludesControllerAndEachResolvedStructureBlockOnce() {
        MachineStructureDisplay display = MachineStructureDisplay.from(testMachine(
                Blocks.IRON_BLOCK.defaultBlockState(),
                Blocks.IRON_BLOCK.defaultBlockState(),
                Blocks.GOLD_BLOCK.defaultBlockState()));

        assertThat(display.ingredients()).extracting(ItemStack::getItem)
                .containsExactly(ModBlocks.controllerFor(display.machine().registryName()).get().asItem(),
                        Blocks.IRON_BLOCK.asItem(), Blocks.GOLD_BLOCK.asItem());
    }

    @Test
    void displaySchemaIsThePreviewSchemaForItsMachine() {
        Machine machine = testMachine(Blocks.IRON_BLOCK.defaultBlockState());

        MachineStructureDisplay display = MachineStructureDisplay.from(machine);

        assertThat(display.schema().machineId()).isEqualTo(machine.registryName());
        assertThat(display.schema().states()).containsValue(Blocks.IRON_BLOCK.defaultBlockState());
    }

    @Test
    void displayExcludesAirAndPredicatesWithoutResolvedStates() {
        Machine machine = new Machine() {
            @Override public Identifier registryName() { return MMCR.id("blast_furnace"); }
            @Override public BlockArray pattern() {
                Map<BlockPos, BlockPredicate> pattern = new LinkedHashMap<>();
                pattern.put(BlockPos.ZERO, new BlockPredicate.OfBlockState(Blocks.AIR.defaultBlockState()));
                pattern.put(new BlockPos(1, 0, 0), new BlockPredicate.Any());
                pattern.put(new BlockPos(2, 0, 0), new BlockPredicate.OfBlock(Blocks.IRON_BLOCK));
                return new BlockArray(pattern);
            }
            @Override public MachineControllerSpec controller() { return MachineControllerSpec.defaultsFor(registryName()); }
        };

        assertThat(MachineStructureDisplay.from(machine).ingredients()).extracting(ItemStack::getItem)
                .containsExactly(ModBlocks.controllerFor(machine.registryName()).get().asItem(), Blocks.IRON_BLOCK.asItem());
    }

    @Test
    void ingredientsReturnCopiesThatCannotMutateTheDisplay() {
        MachineStructureDisplay display = MachineStructureDisplay.from(testMachine(Blocks.IRON_BLOCK.defaultBlockState()));

        ItemStack exposed = display.ingredients().get(1);
        exposed.setCount(42);

        assertThat(display.ingredients().get(1).getCount()).isOne();
    }

    private static Machine testMachine(BlockState... states) {
        return new Machine() {
            @Override public Identifier registryName() { return MMCR.id("blast_furnace"); }
            @Override public BlockArray pattern() {
                Map<BlockPos, BlockPredicate> pattern = new LinkedHashMap<>();
                for (int index = 0; index < states.length; index++) {
                    pattern.put(new BlockPos(index, 0, 0), new BlockPredicate.OfBlockState(states[index]));
                }
                return new BlockArray(pattern);
            }
            @Override public MachineControllerSpec controller() { return MachineControllerSpec.defaultsFor(registryName()); }
        };
    }
}
