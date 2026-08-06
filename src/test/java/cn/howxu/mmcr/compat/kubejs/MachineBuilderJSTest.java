package cn.howxu.mmcr.compat.kubejs;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
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

class MachineBuilderJSTest {

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void controller_textures_sets_front_and_all_other_faces() {
        var machine = new MachineBuilderJS(MMCR.id("arc_furnace"))
                .controllerTextures(MMCR.id("block/arc_front"), MMCR.id("block/arc_side"))
                .createObject();

        assertThat(machine.controller()).isEqualTo(new MachineControllerSpec(
                MMCR.id("arc_furnace_controller"),
                MMCR.id("block/arc_front"),
                MMCR.id("block/arc_side"),
                MMCR.id("block/arc_side"),
                MMCR.id("block/arc_side"),
                false));
    }

    @Test
    void individual_texture_setters_override_only_that_face() {
        var machine = new MachineBuilderJS(MMCR.id("arc_furnace"))
                .controllerTextures("mmcr:block/arc_front", "mmcr:block/arc_side")
                .controllerTopTexture(MMCR.id("block/arc_top"))
                .controllerBottomTexture(MMCR.id("block/arc_bottom"))
                .createObject();

        assertThat(machine.controller().frontTexture()).isEqualTo(MMCR.id("block/arc_front"));
        assertThat(machine.controller().sideTexture()).isEqualTo(MMCR.id("block/arc_side"));
        assertThat(machine.controller().topTexture()).isEqualTo(MMCR.id("block/arc_top"));
        assertThat(machine.controller().bottomTexture()).isEqualTo(MMCR.id("block/arc_bottom"));
    }

    @Test
    void allow_vertical_facing_sets_controller_spec_flag() {
        var machine = new MachineBuilderJS(MMCR.id("arc_furnace"))
                .allowVerticalFacing()
                .createObject();

        assertThat(machine.controller().allowVerticalFacing()).isTrue();
    }

    @Test
    void full_rotational_symmetry_sets_controller_spec_flag() {
        var machine = new MachineBuilderJS(MMCR.id("arc_furnace"))
                .fullyRotationallySymmetric()
                .createObject();

        assertThat(machine.controller().fullyRotationallySymmetric()).isTrue();
    }

    @Test
    void require_vertical_facing_sets_controller_spec_flags() {
        var machine = new MachineBuilderJS(MMCR.id("arc_furnace"))
                .requireVerticalFacing()
                .createObject();

        assertThat(machine.controller().allowVerticalFacing()).isTrue();
        assertThat(machine.controller().requireVerticalFacing()).isTrue();
    }

    @Test
    void builder_passes_single_block_replacements_to_machine() {
        var replacement = new SingleBlockModifierReplacement(
                "speed", new BlockPos(1, 0, 0), new BlockPredicate.OfBlock(Blocks.GOLD_BLOCK),
                List.of(), "", ItemStack.EMPTY);
        var builder = new MachineBuilderJS("mmcr:builder_replacement")
                .pattern("_I", Map.of("I", Blocks.IRON_BLOCK))
                .addModifier(replacement);

        DynamicMachine machine = builder.createObject();

        assertThat(machine.modifierReplacementsAt(new BlockPos(1, 0, 0))).singleElement()
                .extracting(SingleBlockModifierReplacement::getModifierName)
                .isEqualTo("speed");
    }
}
