package cn.howxu.mmcr;

import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.api.machine.MachineStructureRegistry;
import cn.howxu.mmcr.api.machine.level.MachineLevelRegistry;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.api.recipe.modifier.ModifierRegistry;
import cn.howxu.mmcr.api.recipe.requirement.LevelRequirement;
import cn.howxu.mmcr.publicapi.event.RegisterMachineStructuresEvent;
import cn.howxu.mmcr.publicapi.recipe.modifier.Modifiers;
import cn.howxu.mmcr.publicapi.structure.BlockConditions;
import cn.howxu.mmcr.publicapi.structure.level.Levels;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;

/** Real startup-script coverage for non-vanilla registry references.
 * @author howxu <dev@howxu.cn>
 */
public final class StartupRegistryReferenceGameTest {
    public static void registerPublicReferences(RegisterMachineStructuresEvent event) {
        ResourceLocation typeId = ResourceLocation.parse("mmcr_test:public_induction_cell");
        ResourceLocation modifierId = ResourceLocation.parse("mmcr_test:public_mek_upgrade");
        event.registerLevelType(Levels.type(typeId, Component.translatable("level.mmcr_test.public_induction_cell")));
        event.registerLevel(Levels.level(ResourceLocation.parse("mmcr_test:public_basic_induction_cell"), typeId, 0,
                BlockConditions.state("mekanism:basic_induction_cell"), Modifiers.bundle()));
        event.registerModifier(modifierId, Modifiers.bundle());
        event.registerModifierItem(ResourceLocation.parse("mekanism:basic_control_circuit"), modifierId);
    }

    public static void mekanismExampleResolvesRegisteredContent(GameTestHelper helper) {
        ResourceLocation machineId = ResourceLocation.parse("mmcr_kubejs:mekanism_registry_demo");
        helper.assertTrue(MachineDefinitions.getRegistration(machineId) != null,
                "The example machine was declared early enough to register its controller");
        assertLevel(helper, "mmcr_kubejs:basic_induction_cell", "mekanism:basic_induction_cell");
        assertLevel(helper, "mmcr_kubejs:advanced_induction_cell", "mekanism:advanced_induction_cell");
        assertBinding(helper, "mekanism:upgrade_speed", "mmcr_kubejs:mek_speed");
        assertBinding(helper, "mekanism:upgrade_energy", "mmcr_kubejs:mek_energy");
        assertBinding(helper, "mekanism:steel_casing", "mmcr_kubejs:mek_energy");
        assertLevel(helper, "mmcr_test:public_basic_induction_cell", "mekanism:basic_induction_cell");
        assertBinding(helper, "mekanism:basic_control_circuit", "mmcr_test:public_mek_upgrade");

        var structure = MachineStructureRegistry.dynamicSnapshot().get(machineId);
        helper.assertTrue(structure != null, "The shipped server script compiled the example structure");
        var replacement = structure.requirements().modifierReplacements().get('M').getFirst();
        var glass = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse("mekanism:structural_glass")).orElseThrow();
        helper.assertTrue(replacement.getReplacement().matches(glass.defaultBlockState()),
                "The modifier replacement uses the actual Mekanism block");
        helper.assertTrue(!replacement.getReplacement().matches(Blocks.AIR.defaultBlockState()),
                "The replacement did not fall back to air");
        var recipe = RecipeRegistry.getRecipe(ResourceLocation.parse("mmcr_kubejs:mekanism_registry_demo/steel"));
        helper.assertTrue(recipe != null && recipe.requirements().stream().anyMatch(requirement ->
                        requirement instanceof LevelRequirement level
                                && level.levelId().equals(ResourceLocation.parse("mmcr_kubejs:basic_induction_cell"))),
                "The example recipe resolved the startup level before recipe parsing");
        helper.succeed();
    }

    public static void kubejsReferencesResolveAfterRegistryCallbacks(GameTestHelper helper) {
        assertLevel(helper, "mmcr_test:kubejs_reference_coil", "kubejs:mmcr_reference_coil");
        assertBinding(helper, "kubejs:mmcr_reference_upgrade", "mmcr_test:kubejs_reference_speed");
        assertBinding(helper, "kubejs:mmcr_reference_coil", "mmcr_test:kubejs_reference_speed");
        var horizontalLevel = MachineLevelRegistry.getLevel(ResourceLocation.parse("mmcr_test:kubejs_reference_horizontal_log"));
        var horizontalLog = Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X);
        helper.assertTrue(horizontalLevel != null && horizontalLevel.statePredicate().matches(horizontalLog)
                        && !horizontalLevel.statePredicate().matches(Blocks.OAK_LOG.defaultBlockState()),
                "Script level declarations preserve explicit state properties through deferred compilation");
        helper.succeed();
    }

    private static void assertLevel(GameTestHelper helper, String levelId, String blockId) {
        var block = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse(blockId)).orElseThrow();
        var level = MachineLevelRegistry.getLevel(ResourceLocation.parse(levelId));
        helper.assertTrue(level != null && level.statePredicate() instanceof BlockPredicate.OfBlockState predicate
                        && predicate.state().getBlock() == block,
                "Level " + levelId + " resolves to " + blockId + ", not air");
    }

    private static void assertBinding(GameTestHelper helper, String itemId, String modifierId) {
        var item = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(itemId)).orElseThrow();
        ResourceLocation modifier = ResourceLocation.parse(modifierId);
        helper.assertTrue(modifier.equals(ModifierRegistry.modifierFor(new ItemStack(item))),
                "Registered item " + itemId + " provides modifier " + modifierId);
        var stacks = ModifierRegistry.modifierItems().get(modifier);
        helper.assertTrue(stacks != null && stacks.stream().anyMatch(stack -> stack.getItem() == item
                        && ItemStack.isSameItemSameComponents(stack, new ItemStack(item))),
                "The saved binding has the real item's bound default components");
    }
}
