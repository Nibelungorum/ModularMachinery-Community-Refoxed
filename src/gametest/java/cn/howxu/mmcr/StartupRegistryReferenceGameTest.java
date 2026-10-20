package cn.howxu.mmcr;

import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.api.machine.MachineStructureRegistry;
import cn.howxu.mmcr.api.machine.level.MachineLevelRegistry;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.controller.ControllerScreenTextScope;
import cn.howxu.mmcr.api.machine.definition.MachineBehaviorContext;
import cn.howxu.mmcr.api.machine.definition.MachineIoView;
import cn.howxu.mmcr.api.machine.definition.RecipeBehavior;
import cn.howxu.mmcr.api.machine.definition.RecipeStartContext;
import cn.howxu.mmcr.api.machine.definition.RecipeFinishContext;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.internal.runtime.ControllerScreenTextState;
import cn.howxu.mmcr.internal.runtime.JadeTextState;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.api.recipe.modifier.ModifierRegistry;
import cn.howxu.mmcr.api.recipe.requirement.LevelRequirement;
import cn.howxu.mmcr.publicapi.event.RegisterMachineStructuresEvent;
import cn.howxu.mmcr.publicapi.recipe.modifier.Modifiers;
import cn.howxu.mmcr.publicapi.structure.BlockConditions;
import cn.howxu.mmcr.publicapi.structure.level.Levels;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;

import java.util.List;
import java.util.Set;

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
        assertIdentifierOverloads(helper);
        helper.succeed();
    }

    private static void assertIdentifierOverloads(GameTestHelper helper) {
        ResourceLocation machineId = ResourceLocation.parse("mmcr_test:kubejs_identifier_overloads");
        var machine = MachineDefinitions.getRegistration(machineId);
        helper.assertTrue(machine != null, "ResourceLocation overload creates a registered script machine");
        helper.assertTrue(machine.recipePoolIds().equals(List.of(ResourceLocation.parse("mmcr_test:identifier_pool"),
                        ResourceLocation.parse("mmcr_test:identifier_other_pool"))),
                "String varargs preserve recipe pool order");
        var controller = machine.controllerSpec();
        helper.assertTrue(controller.frontTexture().equals(ResourceLocation.parse("minecraft:block/stone"))
                        && controller.sideTexture().equals(ResourceLocation.parse("minecraft:block/dirt"))
                        && controller.topTexture().equals(ResourceLocation.parse("minecraft:block/gravel"))
                        && controller.bottomTexture().equals(ResourceLocation.parse("minecraft:block/sand")),
                "Four String texture arguments retain face order through Rhino");
        helper.assertTrue(machine.appearance().controllerBaseTexture().equals(ResourceLocation.parse("minecraft:block/stone")),
                "ResourceLocation arguments remain callable alongside String overloads through Rhino");
        var screen = new ControllerScreenTextState();
        var jade = new JadeTextState();
        var context = new MachineBehaviorContext(null, helper.getLevel(), BlockPos.ZERO, machineId, 0L, screen, null,
                new MachineIoView(new CapabilitySnapshot(List.of())), List.of(), jade);
        var recipe = MachineRecipe.fromCanonical(ResourceLocation.parse("mmcr_test:identifier_recipe"),
                machine.recipePoolIds().getFirst(), 1, List.of(), List.of(), List.of(), 0, 1,
                false, false, false, Set.of());
        var behavior = (RecipeBehavior) machine.behavior();
        var start = new RecipeStartContext(context, recipe, 1, 1, 1, List.of(), List.of());
        behavior.beforeStart().accept(start);
        screen.clear(ControllerScreenTextScope.OPERATION);
        screen.flushReplacements();
        helper.assertTrue(start.cancelled() && screen.snapshot().lines().stream().map(line -> line.lineId()).toList()
                        .equals(List.of(ResourceLocation.parse("mmcr_test:warning"), ResourceLocation.parse("mmcr_test:details"))),
                "Cancelled JavaScript start retains ordered controller text added with String IDs");
        helper.assertTrue(screen.snapshot().lines().getFirst().text().equals(Component.translatable("message.mmcr_test.updated")),
                "ResourceLocation replacement updates the line created with a String ID");
        behavior.beforeFinish().accept(new RecipeFinishContext(context, recipe, 1, 1, List.of()));
        helper.assertTrue(screen.snapshot().lines().size() == 1
                        && screen.snapshot().lines().getFirst().lineId().equals(ResourceLocation.parse("mmcr_test:details"))
                        && jade.snapshot().lines().isEmpty(),
                "JavaScript removes the same controller and Jade lines using String IDs");
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
