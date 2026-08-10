package cn.howxu.mmcr;

import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.machine.PortRequirementSpec;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeCraftingContext;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.internal.multiblock.SharedIoCoordinator;
import cn.howxu.mmcr.internal.multiblock.StructureClaimRegistry;
import cn.howxu.mmcr.internal.tile.EnergyInputHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemInputBusBlockEntity;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class SharedMultiblockIoGameTest {

    public void sharedEnergyPortFormsBothControllersAndSurvivesOneTeardown(GameTestHelper helper) {
        BlockPos sharedEnergy = new BlockPos(2, 2, 2);
        MachineControllerBlockEntity first = placeController(helper, new BlockPos(0, 2, 2), sharedEnergy, "shared_energy_first");
        MachineControllerBlockEntity second = placeController(helper, new BlockPos(4, 2, 2), sharedEnergy, "shared_energy_second");
        helper.setBlock(sharedEnergy, ModBlocks.BLOCKS.get("energy_input_hatch").get().defaultBlockState());

        helper.runAtTickTime(4, () -> {
            first.serverTick();
            second.serverTick();
            helper.assertTrue(first.isFormed(), "first controller should form");
            helper.assertTrue(second.isFormed(), "second controller should form through shared energy port");
            helper.destroyBlock(first.getBlockPos());
        });
        helper.runAtTickTime(8, () -> {
            IOPortBlockEntity port = helper.getBlockEntity(sharedEnergy, EnergyInputHatchBlockEntity.class);
            helper.assertTrue(second.isFormed(), "second controller remains formed after first teardown");
            helper.assertTrue(port.linkedControllerPositions().contains(second.getBlockPos()), "shared port retains second owner");
            helper.succeed();
        });
    }

    public void sharedInputPartiallyStartsBothControllers(GameTestHelper helper) {
        BlockPos sharedInput = new BlockPos(2, 2, 2);
        MachineControllerBlockEntity first = placeController(helper, new BlockPos(0, 2, 2), sharedInput, "shared_input_first");
        MachineControllerBlockEntity second = placeController(helper, new BlockPos(4, 2, 2), sharedInput, "shared_input_second");
        helper.setBlock(sharedInput, ModBlocks.BLOCKS.get("item_input_bus").get().defaultBlockState());

        helper.runAtTickTime(4, () -> {
            first.serverTick();
            second.serverTick();
            ItemInputBusBlockEntity input = helper.getBlockEntity(sharedInput, ItemInputBusBlockEntity.class);
            input.getItemHandler(null).insertItem(0, new ItemStack(Items.IRON_INGOT, 10), false);
            MachineRecipe recipe = itemRecipe("shared_input_start");
            StructureClaimRegistry.ResourceDomain domain = first.resourceDomain();
            SharedIoCoordinator coordinator = new SharedIoCoordinator();
            AtomicInteger totalParallelism = new AtomicInteger();

            enqueueStart(coordinator, domain, first, recipe, totalParallelism);
            enqueueStart(coordinator, domain, second, recipe, totalParallelism);
            coordinator.resolve(domain);

            helper.assertTrue(totalParallelism.get() == 10, "two requests for eight must receive total parallelism ten");
            helper.assertTrue(input.getItemHandler(null).getStackInSlot(0).isEmpty(), "shared input is fully committed");
            helper.succeed();
        });
    }

    public void finiteSharedEnergyRotatesTickGrantsBetweenLanes(GameTestHelper helper) {
        BlockPos sharedEnergy = new BlockPos(2, 2, 2);
        MachineControllerBlockEntity first = placeController(helper, new BlockPos(0, 2, 2), sharedEnergy, "shared_tick_first");
        MachineControllerBlockEntity second = placeController(helper, new BlockPos(4, 2, 2), sharedEnergy, "shared_tick_second");
        helper.setBlock(sharedEnergy, ModBlocks.BLOCKS.get("energy_input_hatch").get().defaultBlockState());

        SharedIoCoordinator coordinator = new SharedIoCoordinator();
        MachineRecipe recipe = energyRecipe("shared_energy_tick");
        AtomicInteger firstTicks = new AtomicInteger();
        AtomicInteger secondTicks = new AtomicInteger();
        AtomicReference<StructureClaimRegistry.ResourceDomain> domain = new AtomicReference<>();
        AtomicReference<EnergyInputHatchBlockEntity> energy = new AtomicReference<>();
        helper.runAtTickTime(4, () -> {
            first.serverTick();
            second.serverTick();
            energy.set(helper.getBlockEntity(sharedEnergy, EnergyInputHatchBlockEntity.class));
            domain.set(first.resourceDomain());
            energy.get().getMutableEnergyStorage(null).receiveEnergy(15, false);
            enqueueTick(coordinator, domain.get(), first, recipe, firstTicks);
            enqueueTick(coordinator, domain.get(), second, recipe, secondTicks);
            coordinator.resolve(domain.get());

            helper.assertTrue(firstTicks.get() == 1, "first lane receives exactly one full energy grant");
            helper.assertTrue(secondTicks.get() == 0, "second lane cannot receive a second finite energy grant in the same tick");
        });
        helper.runAtTickTime(5, () -> {
            energy.get().getMutableEnergyStorage(null).receiveEnergy(15, false);
            enqueueTick(coordinator, domain.get(), first, recipe, firstTicks);
            enqueueTick(coordinator, domain.get(), second, recipe, secondTicks);
            coordinator.resolve(domain.get());

            helper.assertTrue(firstTicks.get() == 1, "first lane advances only once across consecutive finite-energy ticks");
            helper.assertTrue(secondTicks.get() == 1, "second lane receives the next tick's full energy grant");
            helper.succeed();
        });
    }

    private static MachineControllerBlockEntity placeController(GameTestHelper helper, BlockPos controllerPos,
                                                                 BlockPos sharedPort, String path) {
        Identifier machineId = MMCR.id("test_cube");
        helper.setBlock(controllerPos, ModBlocks.controllerFor(machineId).get().defaultBlockState()
                .setValue(MachineControllerBlock.FACING, Direction.SOUTH));
        DynamicMachine machine = new DynamicMachine(MMCR.id(path), path,
                new BlockArray(Map.of(sharedPort.subtract(controllerPos), new BlockPredicate.AnyOf(List.of(
                        new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("item_input_bus").get()),
                        new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("energy_input_hatch").get()))))),
                MachineControllerSpec.defaultsFor(machineId), PortRequirementSpec.none());
        MachineControllerBlockEntity controller = helper.getBlockEntity(controllerPos, MachineControllerBlockEntity.class);
        controller.setMachine(machine);
        return controller;
    }

    private static void enqueueStart(SharedIoCoordinator coordinator, StructureClaimRegistry.ResourceDomain domain,
                                     MachineControllerBlockEntity controller, MachineRecipe recipe, AtomicInteger total) {
        RecipeCraftingContext context = new RecipeCraftingContext(controller);
        coordinator.enqueue(new SharedIoCoordinator.StartRequest(domain,
                new SharedIoCoordinator.LaneKey(controller.getBlockPos(), "base"), controller.getStructureVersion(), 8,
                requested -> context.commitStart(recipe, requested), total::addAndGet,
                () -> controller.resourceDomain() != null && controller.resourceDomain().equals(domain), controller::getStructureVersion));
    }

    private static void enqueueTick(SharedIoCoordinator coordinator, StructureClaimRegistry.ResourceDomain domain,
                                    MachineControllerBlockEntity controller, MachineRecipe recipe, AtomicInteger ticks) {
        RecipeCraftingContext context = new RecipeCraftingContext(controller);
        coordinator.enqueue(new SharedIoCoordinator.TickRequest(domain,
                new SharedIoCoordinator.LaneKey(controller.getBlockPos(), "base"), controller.getStructureVersion(),
                () -> {
                    if (!context.coordinatorIoTick(recipe, 1).getAsBoolean()) return false;
                    ticks.incrementAndGet();
                    return true;
                }, () -> controller.resourceDomain() != null && controller.resourceDomain().equals(domain), controller::getStructureVersion));
    }

    private static MachineRecipe itemRecipe(String path) {
        return new MachineRecipe(MMCR.id(path), MMCR.id("test_cube"), 20, List.of(), List.of(), List.of(), 0, 1,
                false, List.of(), List.of(new ItemRequirement(RecipeModifier.IOType.INPUT,
                Ingredient.of(Items.IRON_INGOT), 1, ItemStack.EMPTY)), true);
    }

    private static MachineRecipe energyRecipe(String path) {
        return new MachineRecipe(MMCR.id(path), MMCR.id("test_cube"), 20, List.of(), List.of(), List.of(), 0, 1,
                false, List.of(), List.of(new EnergyRequirement(15)));
    }
}
