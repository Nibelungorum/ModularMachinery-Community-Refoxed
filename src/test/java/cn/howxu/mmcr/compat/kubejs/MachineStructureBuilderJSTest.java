package cn.howxu.mmcr.compat.kubejs;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.recipe.modifier.SingleBlockModifierReplacement;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MachineStructureBuilderJSTest {

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void server_structure_builder_creates_structure_definition() {
        var replacement = new SingleBlockModifierReplacement(
                "speed", new BlockPos(1, 0, 0), new BlockPredicate.OfBlock(Blocks.GOLD_BLOCK),
                List.of(), "", ItemStack.EMPTY);

        var structure = new MachineStructureBuilderJS("mmcr:arc_furnace")
                .pattern("_I", Map.of("I", Blocks.IRON_BLOCK))
                .addModifier(replacement)
                .createObject();

        assertThat(structure.machineId()).isEqualTo(MMCR.id("arc_furnace"));
        assertThat(structure.pattern().pattern()).containsKey(new BlockPos(1, 0, 0));
        assertThat(structure.modifierReplacements().get(new BlockPos(1, 0, 0))).singleElement()
                .extracting(SingleBlockModifierReplacement::getModifierName)
                .isEqualTo("speed");
    }
}
