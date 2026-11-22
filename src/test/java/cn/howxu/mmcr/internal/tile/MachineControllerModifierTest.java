package cn.howxu.mmcr.internal.tile;

import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import cn.howxu.mmcr.LevelStub;
import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.MachineAppearanceSpec;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.modifier.MachineModifier;
import cn.howxu.mmcr.api.machine.modifier.ModifierStacking;
import cn.howxu.mmcr.api.recipe.IntegrationTypeHelper;
import cn.howxu.mmcr.api.machine.PortRequirementSpec;
import cn.howxu.mmcr.api.machine.PortTierRequirementSpec;
import cn.howxu.mmcr.api.machine.definition.ModifierDefinition;
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
        var modifier = MachineModifier.numeric("duration", "input", 2D, "multiply", false);
        ModifierRegistry.installSnapshot(Map.of(modifierId, new ModifierDefinition(List.of(modifier))));

        var controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), BlockPos.ZERO);
        controller.setLevel(LevelStub.create(Map.of(BlockPos.ZERO, Blocks.DIAMOND_BLOCK)));
        var replacement = new SingleBlockModifierReplacement(modifierId,
                new BlockPredicate.OfBlock(Blocks.DIAMOND_BLOCK));

        Map<String, List<MachineModifier>> found = collectFoundModifiers(controller,
                Map.of(BlockPos.ZERO, List.of(replacement)), Set.of(modifierId));

        assertThat(collectFoundModifiers(controller, Map.of(BlockPos.ZERO, List.of(replacement)), Set.of())).isEmpty();
        assertThat(replacement.getModifiers()).isEmpty();
        assertThat(found).containsEntry(modifierId.toString(), List.of(modifier));
    }

    @Test
    void block_folding_counts_distinct_matching_positions_only_for_opted_in_ids() throws Exception {
        var id = MMCR.id("stacked_speed");
        var effect = ModifierDefinition.of("duration", "input", 0.9, "multiply", false);
        var pos = BlockPos.ZERO.east();
        var controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), BlockPos.ZERO);
        controller.setLevel(LevelStub.create(Map.of(BlockPos.ZERO, Blocks.DIAMOND_BLOCK, pos, Blocks.DIAMOND_BLOCK)));
        var replacement = new SingleBlockModifierReplacement(id, new BlockPredicate.OfBlock(Blocks.DIAMOND_BLOCK));
        var positions = Map.of(BlockPos.ZERO, List.of(replacement, replacement), pos, List.of(replacement));
        try {
            for (var policy : List.of(ModifierStacking.LEGACY, ModifierStacking.ADD, ModifierStacking.MULTIPLY)) {
                ModifierRegistry.installSnapshot(Map.of(id, effect), Map.of(), Map.of(id, policy));
                var found = collectFoundModifiers(controller, positions, Set.of(id)).get(id.toString());
                float ticks = IntegrationTypeHelper.applyDuration(MachineModifier.recipeModifiers(found), 100);
                assertThat(Math.round(ticks)).isEqualTo(policy == ModifierStacking.LEGACY ? 90
                        : policy == ModifierStacking.ADD ? 80 : 81);
            }
        } finally {
            ModifierRegistry.installSnapshot(Map.of());
        }
    }

    @Test
    void formed_upgrade_bus_uses_the_machine_appearance_base_texture() {
        var machineId = MMCR.id("upgrade_bus_appearance_machine");
        var busPatternPos = new BlockPos(1, 0, 0);
        var busPos = new BlockPos(-1, 0, 0);
        Block busBlock = ModBlocks.BLOCKS.get("upgrade_bus_normal").get();
        var appearance = new MachineAppearanceSpec(
                MMCR.id("basic_casing"), MMCR.id("block/controller"), MMCR.id("block/formed_port"));
        var machine = new DynamicMachine(machineId, "Upgrade Bus Appearance", new BlockArray(
                Map.of(busPatternPos, new BlockPredicate.OfBlock(busBlock))),
                MachineControllerSpec.defaultsFor(machineId), appearance,
                PortRequirementSpec.none(), PortTierRequirementSpec.none(), List.of(), Map.of(),
                1, false, false, 1);
        var controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), BlockPos.ZERO);
        var bus = new UpgradeBusBlockEntity(UpgradeBusSize.NORMAL, busPos, busBlock.defaultBlockState());

        RuntimeTestFixtures.formStructureWithComponents(controller, machine, bus);

        assertThat(bus.appearanceBaseTexture()).isEqualTo(MMCR.id("block/formed_port"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, List<MachineModifier>> collectFoundModifiers(
            MachineControllerBlockEntity controller,
            Map<BlockPos, List<SingleBlockModifierReplacement>> replacements, Set<ResourceLocation> allowed) throws Exception {
        Method method = MachineControllerBlockEntity.class
                .getDeclaredMethod("collectFoundModifiers", Map.class, Set.class);
        method.setAccessible(true);
        return (Map<String, List<MachineModifier>>) method.invoke(controller, replacements, allowed);
    }
}
