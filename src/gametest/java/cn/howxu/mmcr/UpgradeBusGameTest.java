package cn.howxu.mmcr;

import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.api.publicapi.machine.RecipeBehavior;
import cn.howxu.mmcr.api.recipe.MachineIngredient;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.internal.tile.ItemInputBusBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemOutputBusBlockEntity;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.internal.tile.UpgradeBusBlockEntity;
import cn.howxu.mmcr.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * End-to-end Upgrade Bus structure and active recipe invalidation coverage.
 *
 * @author howxu <dev@howxu.cn>
 */
public class UpgradeBusGameTest {
    public void optionalBusesInvalidateActiveRecipe(GameTestHelper helper) {
        Identifier machineId = MMCR.id("upgrade_bus_test");
        Identifier modifierId = MMCR.id("upgrade_bus_test_modifier");
        BlockPos controllerPos = new BlockPos(2, 1, 2);
        BlockPos firstBusPos = controllerPos.west(2);
        BlockPos inputPos = controllerPos.west();
        BlockPos outputPos = controllerPos.east();
        BlockPos secondBusPos = controllerPos.east(2);
        Identifier structureModifierId = MMCR.id("upgrade_bus_test_block_modifier");

        helper.setBlock(controllerPos, ModBlocks.controllerFor(machineId).get().defaultBlockState()
                .setValue(MachineControllerBlock.FACING, Direction.SOUTH));
        helper.setBlock(firstBusPos, ModBlocks.CASING.get().defaultBlockState());
        helper.setBlock(inputPos, ModBlocks.BLOCKS.get("item_input_bus").get().defaultBlockState());
        helper.setBlock(outputPos, ModBlocks.BLOCKS.get("item_output_bus").get().defaultBlockState());
        helper.setBlock(secondBusPos, ModBlocks.CASING.get().defaultBlockState());

        DynamicMachine registeredMachine = (DynamicMachine) MachineRegistry.getMachine(machineId);
        helper.assertTrue(registeredMachine != null, "Upgrade Bus test machine is registered");
        AtomicReference<List<ItemStack>> observedUpgradeItems = new AtomicReference<>();
        AtomicReference<List<MachineRequirement>> observedRequirements = new AtomicReference<>();
        Machine machine = new DynamicMachine(registeredMachine.registryName(), registeredMachine.displayNameKey(),
                registeredMachine.pattern(), registeredMachine.controller(), registeredMachine.appearance(),
                registeredMachine.portRequirements(), registeredMachine.portTierRequirements(),
                registeredMachine.dynamicPatterns(), registeredMachine.modifierReplacements(),
                registeredMachine.maxParallelism(), registeredMachine.parallelizable(), registeredMachine.hasFactory(),
                registeredMachine.factoryThreadLimit(), registeredMachine.factoryThreads(), registeredMachine.role(),
                registeredMachine.acceptedModuleIds(), registeredMachine.structureStages(), registeredMachine.failureAction(),
                RecipeBehavior.builder().beforeStart(context ->
                        {
                            observedUpgradeItems.set(context.machineContext().upgradeItems());
                            observedRequirements.set(context.requirements());
                        }).build());
        MachineControllerBlockEntity controller = helper.getBlockEntity(controllerPos, MachineControllerBlockEntity.class);
        controller.setMachine(machine);
        controller.serverTick();

        helper.runAtTickTime(10, () -> {
            helper.assertTrue(controller.structureSnapshot().formed(),
                    "Base blocks form without optional modifiers: " + controller.structureSnapshot());
            helper.assertTrue(controller.runtimeSnapshot().foundModifiers().isEmpty(),
                    "Base blocks do not report an optional modifier");

            helper.setBlock(firstBusPos, ModBlocks.BLOCKS.get("upgrade_bus_normal").get().defaultBlockState());
            helper.setBlock(secondBusPos, ModBlocks.BLOCKS.get("upgrade_bus_normal").get().defaultBlockState());
            UpgradeBusBlockEntity firstBus = helper.getBlockEntity(firstBusPos, UpgradeBusBlockEntity.class);
            UpgradeBusBlockEntity secondBus = helper.getBlockEntity(secondBusPos, UpgradeBusBlockEntity.class);
            try (Transaction transaction = Transaction.openRoot()) {
                firstBus.itemStorage().insert(0, ItemResource.of(Items.NETHER_STAR), 1L, transaction);
                secondBus.itemStorage().insert(0, ItemResource.of(Items.NETHER_STAR), 1L, transaction);
                transaction.commit();
            }
            controller.onStructureBlockChanged(helper.absolutePos(firstBusPos));
            controller.onStructureBlockChanged(helper.absolutePos(secondBusPos));
            controller.serverTick();

            helper.assertTrue(controller.structureSnapshot().formed(), "Replacement Upgrade Bus blocks form the machine");
            helper.assertTrue(controller.runtimeSnapshot().foundModifiers().containsKey(structureModifierId.toString()),
                    "Replacement block exposes its registered modifier");
            helper.assertTrue(controller.runtimeSnapshot().upgradeItems().size() == 2,
                    "Both Upgrade Bus contents are published to the controller");
            helper.assertTrue(controller.componentRuntime().upgradeModifierUnits().get(modifierId) == 2L,
                    "Both Upgrade Bus items resolve to two modifier units");
            helper.assertTrue(controller.componentRuntime().modifierList().stream()
                            .anyMatch(modifier -> modifier.getModifier() == 2.0F),
                    "Upgrade Bus items rebuild the aggregated input modifier");

            ItemInputBusBlockEntity input = helper.getBlockEntity(inputPos, ItemInputBusBlockEntity.class);
            ItemOutputBusBlockEntity output = helper.getBlockEntity(outputPos, ItemOutputBusBlockEntity.class);
            try (Transaction transaction = Transaction.openRoot()) {
                input.itemStorage().insert(0, ItemResource.of(Items.IRON_INGOT), 3L, transaction);
                transaction.commit();
            }
            Identifier recipeId = MMCR.id("upgrade_bus_invalidation_recipe");
            ItemStack goldNugget = new ItemStack(Items.GOLD_NUGGET);
            RecipeRegistry.registerStatic(MachineRecipe.fromCanonical(recipeId, machineId, 20,
                    List.of(MachineRequirement.fromInput(new MachineIngredient.ItemIngredient(
                                    Ingredient.of(Items.IRON_INGOT), 1)),
                            MachineRequirement.itemOutput(goldNugget)),
                    List.of(new MachineOutput.ItemOutput(goldNugget, 1F)), List.of(), 0, 1, false, false, List.of(),
                    false, Set.of()));

            controller.serverTick();
            helper.assertTrue(controller.runtimeSnapshot().crafting().recipeId() != null,
                    "Recipe starts with the formed Upgrade Bus machine");
            helper.assertTrue(observedUpgradeItems.get() != null && observedUpgradeItems.get().size() == 2
                            && observedUpgradeItems.get().stream().allMatch(stack -> stack.is(Items.NETHER_STAR)),
                    "Recipe start callback receives both Upgrade Bus items");
            helper.assertTrue(observedRequirements.get() != null
                            && observedRequirements.get().stream()
                            .anyMatch(requirement -> requirement instanceof ItemRequirement item && item.count() == 3),
                    "Recipe start callback receives the input quantity after Upgrade Bus modifiers: "
                            + observedRequirements.get());

            try (Transaction transaction = Transaction.openRoot()) {
                ItemResource current = firstBus.itemStorage().resource(0);
                if (current != null && !current.isEmpty()) {
                    firstBus.itemStorage().extract(0, current, firstBus.itemStorage().amount(0), transaction);
                }
                firstBus.itemStorage().insert(0, ItemResource.of(Items.DIAMOND), 1L, transaction);
                transaction.commit();
            }
            controller.serverTick();

            helper.assertTrue(controller.runtimeSnapshot().crafting().recipeId() == null,
                    "Bus content mutation invalidates the active recipe on the next tick");
            helper.assertTrue(controller.runtimeSnapshot().crafting().failure() != null
                            && "version_invalidated".equals(controller.runtimeSnapshot().crafting().failure()
                            .details().get("reason")),
                    "Bus mutation uses the version invalidation failure path");
            helper.assertTrue(input.itemStorage().amount(0) == 0L,
                    "Invalidation does not restore consumed inputs");
            helper.assertTrue(output.itemStorage().amount(0) == 0L,
                    "Invalidation does not emit recipe output");
            helper.succeed();
        });
    }
}
