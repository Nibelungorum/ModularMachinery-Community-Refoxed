package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.LevelStub;
import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.MachineAppearanceSpec;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.PortRequirementSpec;
import cn.howxu.mmcr.api.machine.PortTierRequirementSpec;
import cn.howxu.mmcr.api.publicapi.machine.ModifierDefinition;
import cn.howxu.mmcr.api.recipe.modifier.ModifierRegistry;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.modifier.SingleBlockModifierReplacement;
import cn.howxu.mmcr.internal.port.UpgradeBusSize;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.test.RuntimeTestFixtures;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies runtime resolution of ID-only structure modifier replacements.
 *
 * @author howxu <dev@howxu.cn>
 */
class MachineControllerModifierTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void id_only_replacement_uses_the_registered_modifier_definition() throws Exception {
        var modifierId = MMCR.id("registered_structure_modifier");
        var modifier = new RecipeModifier("item", RecipeModifier.IOType.INPUT, 2F,
                RecipeModifier.Operation.MULTIPLY, false);
        ModifierRegistry.installSnapshot(Map.of(modifierId, new ModifierDefinition(List.of(modifier))));

        var controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), BlockPos.ZERO);
        controller.setLevel(LevelStub.create(Map.of(BlockPos.ZERO, Blocks.DIAMOND_BLOCK)));
        var replacement = new SingleBlockModifierReplacement(modifierId,
                new BlockPredicate.OfBlock(Blocks.DIAMOND_BLOCK));

        Map<String, List<RecipeModifier>> found = collectFoundModifiers(controller,
                Map.of(BlockPos.ZERO, List.of(replacement)));

        assertThat(replacement.getModifiers()).isEmpty();
        assertThat(found).containsEntry(modifierId.toString(), List.of(modifier));
    }

    @Test
    void formed_upgrade_bus_uses_the_machine_appearance_base_texture() {
        var machineId = MMCR.id("upgrade_bus_appearance_machine");
        var busPos = new BlockPos(1, 0, 0);
        Block busBlock = ModBlocks.BLOCKS.get("upgrade_bus_normal").get();
        var appearance = new MachineAppearanceSpec(
                MMCR.id("basic_casing"), MMCR.id("block/controller"), MMCR.id("block/formed_port"));
        var machine = new DynamicMachine(machineId, "Upgrade Bus Appearance", new BlockArray(
                Map.of(busPos, new BlockPredicate.OfBlock(busBlock))),
                MachineControllerSpec.defaultsFor(machineId), appearance,
                PortRequirementSpec.none(), PortTierRequirementSpec.none(), List.of(), Map.of(),
                1, false, false, 1);
        var controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), BlockPos.ZERO);
        var bus = new UpgradeBusBlockEntity(UpgradeBusSize.NORMAL, busPos, busBlock.defaultBlockState());

        RuntimeTestFixtures.formStructureWithComponents(controller, machine, bus);

        assertThat(bus.appearanceBaseTexture()).isEqualTo(MMCR.id("block/formed_port"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, List<RecipeModifier>> collectFoundModifiers(
            MachineControllerBlockEntity controller,
            Map<BlockPos, List<SingleBlockModifierReplacement>> replacements) throws Exception {
        Method method = MachineControllerBlockEntity.class
                .getDeclaredMethod("collectFoundModifiers", Map.class);
        method.setAccessible(true);
        return (Map<String, List<RecipeModifier>>) method.invoke(controller, replacements);
    }
}
