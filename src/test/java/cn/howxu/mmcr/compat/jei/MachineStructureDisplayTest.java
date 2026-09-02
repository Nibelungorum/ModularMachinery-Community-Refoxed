package cn.howxu.mmcr.compat.jei;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.registry.ModItems;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
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
    void displayRetainsDefaultStageMaterials() {
        Machine machine = testMachineWithBlocks();
        MachineStructureDisplay display = MachineStructureDisplay.from(machine);

        assertThat(display.machine()).isSameAs(machine);
        assertThat(display.defaultSchema().states()).hasSize(2);
        assertThat(display.materials().entries()).extracting(entry -> entry.stack().getItem())
                .containsExactly(Blocks.STONE.asItem(), Blocks.COBBLESTONE.asItem());
        assertThat(display.ingredients()).allSatisfy(stack -> assertThat(stack.getCount()).isGreaterThan(0));
    }

    @Test
    void ingredientsReturnCopiesThatCannotMutateTheDisplay() {
        MachineStructureDisplay display = MachineStructureDisplay.from(testMachineWithBlocks());

        ItemStack exposed = display.ingredients().getFirst();
        exposed.setCount(42);

        assertThat(display.ingredients().getFirst().getCount()).isOne();
    }

    @Test
    void displayMaterialEntriesAreDefensiveCopies() {
        MachineStructureDisplay display = MachineStructureDisplay.from(testMachineWithBlocks());

        ItemStack exposed = display.materials().entries().getFirst().stack();
        exposed.setCount(42);

        assertThat(display.materials().entries().getFirst().stack().getCount()).isOne();
    }

    @Test
    void structureOutputUsesBlueprintAndMachineName() {
        Machine machine = testMachineWithBlocks();
        ItemStack output = MachineStructureCategory.structureOutput(MachineStructureDisplay.from(machine));

        assertThat(output.getItem()).isSameAs(ModItems.BLUEPRINT.get());
        assertThat(output.get(DataComponents.CUSTOM_NAME)).isEqualTo(machine.displayName());
        assertThat(output.getItem()).isNotEqualTo(Items.ENCHANTED_BOOK);
    }

    @Test
    void materialsDoNotBuildTheFullDefaultPreviewSchema() {
        Machine machine = new Machine() {
            @Override public Identifier registryName() { return MMCR.id("test_materials_only"); }
            @Override public BlockArray pattern() {
                Map<BlockPos, BlockPredicate> pattern = new LinkedHashMap<>();
                pattern.put(BlockPos.ZERO, new BlockPredicate.OfBlock(Blocks.STONE));
                pattern.put(new BlockPos(1, 0, 0), new BlockPredicate.OfBlock(Blocks.COBBLESTONE));
                return new BlockArray(pattern);
            }
            @Override public MachineControllerSpec controller() {
                throw new AssertionError("material extraction must not build the preview schema");
            }
        };

        assertThat(MachineStructureDisplay.from(machine).materials().entries())
                .extracting(entry -> entry.stack().getItem())
                .containsExactly(Blocks.STONE.asItem(), Blocks.COBBLESTONE.asItem());
    }

    private static Machine testMachineWithBlocks() {
        return new Machine() {
            @Override public Identifier registryName() { return MMCR.id("test_cube"); }
            @Override public BlockArray pattern() {
                Map<BlockPos, BlockPredicate> pattern = new LinkedHashMap<>();
                pattern.put(BlockPos.ZERO, new BlockPredicate.OfBlock(Blocks.STONE));
                pattern.put(new BlockPos(1, 0, 0), new BlockPredicate.OfBlock(Blocks.COBBLESTONE));
                return new BlockArray(pattern);
            }
            @Override public MachineControllerSpec controller() { return MachineControllerSpec.defaultsFor(registryName()); }
        };
    }
}
