package cn.howxu.mmcr;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.compat.mekanism.ChemicalIngredient;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.machine.PortRequirementSpec;
import cn.howxu.mmcr.api.recipe.CraftingContext;
import cn.howxu.mmcr.api.recipe.InputScope;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.api.recipe.component.ComponentPredicate;
import cn.howxu.mmcr.api.recipe.component.DataComponentPredicateSet;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.IOType;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.api.recipe.requirement.FluidRequirement;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.adapter.AE2NativeAdapters;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.InputInterfaceBlockEntity;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.PatternInterfaceBlockEntity;
import cn.howxu.mmcr.compat.mekanism.loaded.ChemicalPortBlockEntity;
import cn.howxu.mmcr.compat.mekanism.loaded.HeatPortBlockEntity;
import cn.howxu.mmcr.compat.mekanism.loaded.LoadedChemicalRequirement;
import cn.howxu.mmcr.compat.mekanism.loaded.LoadedHeatRequirement;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.internal.capability.FluidHatchCapability;
import cn.howxu.mmcr.internal.capability.ItemBusCapability;
import cn.howxu.mmcr.internal.menu.AbstractMachineMenu;
import cn.howxu.mmcr.internal.menu.InputIsolationMenuState;
import cn.howxu.mmcr.internal.menu.ItemBusMenu;
import cn.howxu.mmcr.internal.menu.MachineControllerMenu;
import cn.howxu.mmcr.internal.multiblock.SharedIoCoordinator;
import cn.howxu.mmcr.internal.runtime.CraftingRuntime;
import cn.howxu.mmcr.internal.runtime.MachineWorkMode;
import cn.howxu.mmcr.internal.runtime.PatternStartReservation;
import cn.howxu.mmcr.internal.sync.RuntimeContentVersion;
import cn.howxu.mmcr.internal.tile.EnergyInputHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemInputBusBlockEntity;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.internal.tile.MachineControllerRuntime;
import cn.howxu.mmcr.registry.ModBlocks;
import com.google.gson.JsonPrimitive;
import mekanism.api.chemical.ChemicalStack;
import mekanism.common.registries.MekanismChemicals;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerSynchronizer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ServerLevelData;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static cn.howxu.mmcr.util.IOType.INPUT;

/** Registered native storage, real menu synchronization and explicitly admitted runtime operations.
 * @author howxu <dev@howxu.cn>
 */
public final class InputIsolationGameTest {
    private InputIsolationGameTest() { }

    public static void itemSourcesCannotPoolAcrossIsolation(GameTestHelper helper) {
        try (var fixture = fixture(helper, "items", "item_input_bus", "combined_input_basic")) {
            var first = fixture.ports.get(0);
            var second = fixture.ports.get(1);
            first.setInputIsolated(true);
            second.setInputIsolated(true);
            insertItem(helper, first, new ItemStack(Items.IRON_INGOT));
            insertItem(helper, second, new ItemStack(Items.IRON_INGOT));
            helper.assertFalse(fixture.context().planRequirements(List.of(item(2)), 1, Map.of()).successful(),
                    "Isolated inventories cannot pool one ingredient each");
            helper.assertTrue(items(first) == 1 && items(second) == 1, "Rejected planning preserves both inventories");
            first.setInputIsolated(false);
            second.setInputIsolated(false);
            var ordinary = fixture.context().planRequirements(List.of(item(2)), 1, Map.of());
            helper.assertTrue(ordinary.successful() && ordinary.plan().commit(), "Ordinary inventories jointly pay");
            helper.assertTrue(items(first) == 0 && items(second) == 0, "Ordinary commit consumes both ingredients once");
        }
        helper.succeed();
    }

    public static void fluidSourcesCannotPoolAcrossIsolation(GameTestHelper helper) {
        try (var fixture = fixture(helper, "fluids", "fluid_input_hatch", "combined_input_basic")) {
            var first = fixture.ports.get(0);
            var second = fixture.ports.get(1);
            fixture.controller.setFullyInputIsolated(true);
            insertWater(helper, first, 125);
            insertWater(helper, second, 125);
            helper.assertFalse(fixture.context().planRequirements(List.of(water(250)), 1, Map.of()).successful(),
                    "Full isolation cannot pool water across registered physical inputs");
            helper.assertTrue(fluids(first) == 125 && fluids(second) == 125, "Rejected planning cannot partially drain inputs");
            fixture.controller.setFullyInputIsolated(false);
            var ordinary = fixture.context().planRequirements(List.of(water(250)), 1, Map.of());
            helper.assertTrue(ordinary.successful() && ordinary.plan().commit(), "Ordinary fluid payment pools both sources");
            helper.assertTrue(fluids(first) == 0 && fluids(second) == 0, "Both native fluid inventories are drained once");
        }
        helper.succeed();
    }

    public static void combinedInputSharesEnergyAndOutputAtomically(GameTestHelper helper) {
        try (var fixture = fixture(helper, "combined", "combined_input_basic", "fluid_input_hatch", "energy_input_hatch", "item_output_bus")) {
            var combined = fixture.ports.get(0);
            var outside = fixture.ports.get(1);
            var energy = (EnergyInputHatchBlockEntity) fixture.ports.get(2);
            var output = fixture.ports.get(3);
            fixture.controller.setFullyInputIsolated(true);
            insertItem(helper, combined, new ItemStack(Items.IRON_INGOT, 2));
            insertWater(helper, outside, 250);
            energy.energyStorage().setAmount(20);
            List<MachineRequirement> requirements = List.of(item(2), water(250), new EnergyRequirement(7),
                    new ItemRequirement(IOType.OUTPUT, null, 0, new ItemStack(Items.GOLD_NUGGET, 2)));
            helper.assertFalse(fixture.context().planRequirements(requirements, 1, Map.of()).successful(),
                    "An isolated combined port cannot borrow another hatch's water");
            helper.assertTrue(items(combined) == 2 && fluids(outside) == 250 && energy.energyStorage().getAmountAsLong() == 20,
                    "Cross-family planning rejection spends no resources");
            insertWater(helper, combined, 250);
            var prepared = fixture.context().planRequirements(requirements, 1, Map.of());
            helper.assertTrue(prepared.successful() && prepared.plan().inputScope().sources().equals(List.of(combined.getBlockPos())),
                    "All facets of one physical combined port form one isolated source");
            energy.energyStorage().setAmount(0);
            helper.assertFalse(prepared.plan().commit(), "Live FE loss rejects native payment");
            helper.assertTrue(items(combined) == 0 && fluids(combined) == 0 && fluids(outside) == 250 && items(output) == 0,
                    "Late native failure retains its material prefix and never borrows isolated resources or emits output");
            insertItem(helper, combined, new ItemStack(Items.IRON_INGOT, 2));
            insertWater(helper, combined, 250);
            energy.energyStorage().setAmount(20);
            var retry = fixture.context().planRequirements(requirements, 1, Map.of());
            helper.assertTrue(retry.successful() && retry.plan().commit(), "A complete isolated source shares FE and outputs");
            helper.assertTrue(items(combined) == 0 && fluids(combined) == 0 && fluids(outside) == 250
                            && energy.energyStorage().getAmountAsLong() == 13 && items(output) == 2
                            && output.nativeItemHandler().getStackInSlot(0).is(Items.GOLD_NUGGET),
                    "Success consumes the selected source and shared FE, then emits output once");
        }
        helper.succeed();
    }

    public static void fullIsolationPreservesLocalAndIncludesReplacement(GameTestHelper helper) {
        try (var fixture = fixture(helper, "full_or_local", "combined_input_basic", "combined_input_basic")) {
            var local = fixture.ports.get(0);
            var ordinary = fixture.ports.get(1);
            local.setInputIsolated(true);
            fixture.controller.setFullyInputIsolated(true);
            helper.assertTrue(local.isInputIsolated() && !ordinary.isInputIsolated()
                    && fixture.context().inputRouting().inputPorts().stream().allMatch(port -> port.isolated()),
                    "Full policy is ORed with local settings without overwriting them");
            BlockPos replacementPos = new BlockPos(2, 1, 0);
            helper.setBlock(replacementPos, ModBlocks.CASING.get().defaultBlockState());
            helper.setBlock(replacementPos, ModBlocks.BLOCKS.get("combined_input_basic").get().defaultBlockState());
            IOPortBlockEntity replacement = helper.getBlockEntity(replacementPos);
            fixture.controller.invalidateFormedStructure();
            fixture.controller.requestImmediateStructureCheck();
            fixture.controller.serverTick();
            helper.assertTrue(fixture.controller.currentStructureSnapshot().formed() && replacement != ordinary
                    && !replacement.isInputIsolated()
                    && fixture.context().inputRouting().inputPorts().stream().allMatch(port -> port.isolated()),
                    "A replacement joins full isolation with its local setting still false");
            fixture.controller.setFullyInputIsolated(false);
            insertItem(helper, local, new ItemStack(Items.IRON_INGOT, 2));
            insertWater(helper, replacement, 250);
            helper.assertFalse(fixture.context().planRequirements(List.of(item(2), water(250)), 1, Map.of()).successful(),
                    "Retained local isolation still rejects cross-source mixing after disabling full");
            local.setInputIsolated(false);
            var joined = fixture.context().planRequirements(List.of(item(2), water(250)), 1, Map.of());
            helper.assertTrue(joined.successful() && joined.plan().commit(), "Disabling local isolation restores ordinary mixing");
        }
        helper.succeed();
    }

    public static void menusPersistAndSynchronizeTwoViewers(GameTestHelper helper) {
        try (var fixture = fixture(helper, "menus", "item_input_bus")) {
            Player first = helper.makeMockPlayer(GameType.SURVIVAL);
            Player second = helper.makeMockPlayer(GameType.SURVIVAL);
            var input = (ItemInputBusBlockEntity) fixture.ports.getFirst();
            first.setPos(input.getBlockPos().getCenter());
            second.setPos(input.getBlockPos().getCenter());
            checkMenus(helper, input, first, second,
                    new ItemBusMenu(11, first.getInventory(), input), new ItemBusMenu(12, second.getInventory(), input),
                    new ItemBusMenu(11, new Inventory(null)), new ItemBusMenu(12, new Inventory(null)));
            first.setPos(fixture.controller.getBlockPos().getCenter());
            second.setPos(fixture.controller.getBlockPos().getCenter());
            checkMenus(helper, fixture.controller, first, second,
                    new MachineControllerMenu(21, first.getInventory(), fixture.controller),
                    new MachineControllerMenu(22, second.getInventory(), fixture.controller),
                    new MachineControllerMenu(21, new Inventory(null)), new MachineControllerMenu(22, new Inventory(null)));
            helper.assertTrue(fixture.controller.isFullyInputIsolated() && input.isInputIsolated(),
                    "Controller and local input settings persist independently");
        }
        helper.succeed();
    }

    private static void checkMenus(GameTestHelper helper, BlockEntity owner, Player first, Player second,
                                   AbstractMachineMenu firstMenu, AbstractMachineMenu secondMenu,
                                   AbstractMachineMenu firstClient, AbstractMachineMenu secondClient) {
        first.containerMenu = firstMenu;
        second.containerMenu = secondMenu;
        var firstWire = new MenuWire(firstClient);
        var secondWire = new MenuWire(secondClient);
        firstMenu.setSynchronizer(firstWire);
        secondMenu.setSynchronizer(secondWire);
        helper.assertTrue(firstClient.inputIsolation().supported() && secondClient.inputIsolation().supported()
                && !firstClient.inputIsolation().enabled() && !secondClient.inputIsolation().enabled(), "Initial DataSlots reach both clients");
        helper.assertTrue(firstMenu.clickMenuButton(first, InputIsolationMenuState.ENABLE), "First viewer enables isolation");
        firstMenu.broadcastChanges();
        secondMenu.broadcastChanges();
        helper.assertTrue(firstWire.changes > 0 && secondWire.changes > 0 && firstClient.inputIsolation().enabled()
                && secondClient.inputIsolation().enabled(), "Both independent synchronizers deliver the enable change");
        int firstChanges = firstWire.changes;
        int secondChanges = secondWire.changes;
        helper.assertTrue(firstMenu.clickMenuButton(first, InputIsolationMenuState.ENABLE), "Repeated enable is idempotent");
        firstMenu.broadcastChanges();
        secondMenu.broadcastChanges();
        helper.assertTrue(firstWire.changes == firstChanges && secondWire.changes == secondChanges, "No redundant DataSlot delta");
        CompoundTag saved = owner.saveWithoutMetadata(helper.getLevel().registryAccess());
        helper.assertTrue(secondMenu.clickMenuButton(second, InputIsolationMenuState.DISABLE), "Second viewer disables isolation");
        firstMenu.broadcastChanges();
        secondMenu.broadcastChanges();
        helper.assertFalse(firstClient.inputIsolation().enabled() || secondClient.inputIsolation().enabled(), "Disable reaches both clients");
        owner.loadWithComponents(saved, helper.getLevel().registryAccess());
        firstMenu.broadcastChanges();
        secondMenu.broadcastChanges();
        helper.assertTrue(firstClient.inputIsolation().enabled() && secondClient.inputIsolation().enabled(), "NBT reload republishes persisted state");
    }

    public static void paidOrdinaryScopeSurvivesReload(GameTestHelper helper) { paidScopeSurvivesReload(helper, false); }
    public static void paidIsolatedScopeSurvivesReload(GameTestHelper helper) { paidScopeSurvivesReload(helper, true); }

    private static void paidScopeSurvivesReload(GameTestHelper helper, boolean isolated) {
        try (var fixture = fixture(helper, "paid_" + isolated, "item_input_bus", "item_input_bus", "energy_input_hatch")) {
            var first = fixture.ports.get(0);
            var other = fixture.ports.get(1);
            var energy = (EnergyInputHatchBlockEntity) fixture.ports.get(2);
            first.setInputIsolated(isolated);
            other.setInputIsolated(true);
            var catalyst = new ItemStack(Items.DIAMOND_PICKAXE);
            catalyst.set(DataComponents.DAMAGE, 7);
            insertItem(helper, first, catalyst);
            energy.energyStorage().setAmount(100);
            var components = new DataComponentPredicateSet(Map.of(DataComponents.DAMAGE, ComponentPredicate.exact(new JsonPrimitive(7))));
            var retained = new ItemRequirement(IOType.INPUT, Ingredient.of(Items.DIAMOND_PICKAXE), 1, ItemStack.EMPTY,
                    1F, List.of(), components, 0F);
            var recipe = recipe(fixture, "retained", List.of(retained, new EnergyRequirement(5)), List.of());
            fixture.registerRecipe(recipe);
            var runtime = fixture.runtime;
            helper.assertTrue(runtime.start(recipe, 1).isCrafting(), "Real controller starts a component-bearing catalyst recipe");
            InputScope scope = runtime.activeInputScope();
            helper.assertTrue(scope.kind() == (isolated ? InputScope.Kind.ISOLATED : InputScope.Kind.ORDINARY)
                    && scope.sources().equals(List.of(first.getBlockPos())), "Paid start pins the physical source");
            var installed = runtime.ensureInstalledProgram();
            first.setInputIsolated(!isolated);
            other.setInputIsolated(false);
            fixture.controller.setFullyInputIsolated(true);
            fixture.controller.setFullyInputIsolated(false);
            helper.assertTrue(installed != null && installed.current() && runtime.activeInputScope().equals(scope),
                    "Configuration changes do not replace paid execution scope");
            var ledger = runtime.ledger().state();
            CompoundTag saved = new CompoundTag();
            runtime.save(saved, helper.getLevel().registryAccess());
            var portSave = first.saveWithoutMetadata(helper.getLevel().registryAccess());
            runtime.invalidate();
            first.loadWithComponents(portSave, helper.getLevel().registryAccess());
            runtime.load(saved, fixture.controller.resourceDomain(), helper.getLevel().registryAccess());
            runtime.rebindCurrentVersions();
            helper.assertTrue(runtime.active() && runtime.activeInputScope().equals(scope) && runtime.ledger().state().equals(ledger)
                    && ItemStack.isSameItemSameComponents(first.nativeItemHandler().getStackInSlot(0), catalyst),
                    "NBT preserves scope, ledger and catalyst components");
            installed = runtime.ensureInstalledProgram();
            long before = energy.energyStorage().getAmountAsLong();
            helper.assertTrue(installed != null && installed.tryAttempt(fixture.nextRound(), helper.getLevel().getGameTime()) && installed.advance(),
                    "Restored execution pays against its original source");
            helper.assertTrue(runtime.tickCount() == 1 && energy.energyStorage().getAmountAsLong() == before - 5, "Restored attempt pays FE once");
            helper.assertTrue(first.nativeItemHandler().extractItem(0, 1, false).getCount() == 1, "Remove original catalyst");
            insertItem(helper, other, catalyst);
            before = energy.energyStorage().getAmountAsLong();
            ledger = runtime.ledger().state();
            helper.assertTrue(installed.tryAttempt(fixture.nextRound(), helper.getLevel().getGameTime()), "Missing pinned catalyst receives an attempt");
            helper.assertFalse(installed.advance(), "Paid execution cannot migrate to another port's identical catalyst");
            helper.assertTrue(runtime.tickCount() == 1 && energy.energyStorage().getAmountAsLong() == before
                    && runtime.ledger().state().equals(ledger) && items(other) == 1 && runtime.activeInputScope().equals(scope),
                    "Rejected migration preserves progress, payment and catalyst");
        }
        helper.succeed();
    }

    public static void nativeAeInputUsesOnePhysicalSource(GameTestHelper helper) {
        try (var fixture = fixture(helper, "ae_input", "ae2_me_input_interface", "fluid_input_hatch", "energy_input_hatch")) {
            var input = (InputInterfaceBlockEntity) fixture.ports.get(0);
            var outside = fixture.ports.get(1);
            var energy = (EnergyInputHatchBlockEntity) fixture.ports.get(2);
            fixture.controller.setFullyInputIsolated(true);
            var storage = input.getInterfaceLogic().getStorage();
            storage.setStack(0, new GenericStack(AEItemKey.of(Items.IRON_INGOT), 2));
            insertWater(helper, outside, 250);
            energy.energyStorage().setAmount(20);
            List<MachineRequirement> requirements = List.of(item(2), water(250), new EnergyRequirement(7));
            helper.assertFalse(fixture.context().planRequirements(requirements, 1, Map.of()).successful(), "AE cache cannot borrow outside isolated water");
            storage.setStack(1, new GenericStack(AEFluidKey.of(Fluids.WATER), 250));
            var plan = fixture.context().planRequirements(requirements, 1, Map.of());
            helper.assertTrue(plan.successful() && plan.plan().inputScope().sources().equals(List.of(input.getBlockPos()))
                    && plan.plan().commit(), "AE item and fluid views pay as one physical isolated source");
            helper.assertTrue(storage.getStack(0) == null && storage.getStack(1) == null && fluids(outside) == 250
                    && energy.energyStorage().getAmountAsLong() == 13, "Only the selected AE source and shared FE are spent");
        }
        helper.succeed();
    }

    public static void ordinaryPatternRequestIsAtomic(GameTestHelper helper) { patternRequest(helper, false, false); }
    public static void isolatedPatternRequestIsAtomic(GameTestHelper helper) { patternRequest(helper, true, false); }
    public static void ordinaryPatternRequestCanTopUpAndRollback(GameTestHelper helper) { patternRequest(helper, false, true); }

    private static void patternRequest(GameTestHelper helper, boolean isolated, boolean topUp) {
        try (var fixture = fixture(helper, "pattern_" + isolated + "_" + topUp,
                "ae2_me_pattern_interface", "combined_input_basic", "energy_input_hatch")) {
            var host = (PatternInterfaceBlockEntity) fixture.ports.get(0);
            var outside = fixture.ports.get(1);
            var energy = (EnergyInputHatchBlockEntity) fixture.ports.get(2);
            host.setInputIsolated(isolated);
            outside.setInputIsolated(!isolated && !topUp);
            insertItem(helper, outside, new ItemStack(Items.IRON_INGOT, topUp ? 1 : 10));
            if (!topUp) insertWater(helper, outside, 1_000);
            energy.energyStorage().setAmount(20);
            var outputs = List.<MachineOutput>of(new MachineOutput.ItemOutput(new ItemStack(Items.GOLD_NUGGET), 1F));
            fixture.registerRecipe(recipe(fixture, "request", List.of(item(2), water(250), new EnergyRequirement(7),
                    new ItemRequirement(IOType.OUTPUT, null, 0, new ItemStack(Items.GOLD_NUGGET))), outputs));
            var iron = AEItemKey.of(Items.IRON_INGOT);
            var water = AEFluidKey.of(Fluids.WATER);
            host.getLogic().getReturnInv().setStack(0, new GenericStack(iron, 9));
            host.getLogic().getReturnInv().setStack(1, new GenericStack(water, 1_000));
            var holder = new KeyCounter();
            holder.add(iron, 1);
            holder.add(water, topUp ? 125 : 250);
            var request = AE2NativeAdapters.requestInventory(new KeyCounter[]{holder});
            List<MachineCapability> capabilities = List.of(new ItemBusCapability(host, AE2NativeAdapters.items(request), INPUT),
                    new FluidHatchCapability(host, AE2NativeAdapters.fluids(request), INPUT));
            helper.assertTrue(host.reservePatternStart(outputs, capabilities).status() == PatternStartReservation.Status.UNAVAILABLE,
                    "Incomplete request cannot borrow return inventory or a different isolated group");
            helper.assertTrue(holder.get(iron) == 1 && holder.get(water) == (topUp ? 125 : 250)
                    && energy.energyStorage().getAmountAsLong() == 20 && !fixture.runtime.active(), "Rejected admission leaves holders and FE untouched");
            if (topUp) {
                insertWater(helper, outside, 125);
                var stale = host.reservePatternStart(outputs, capabilities);
                helper.assertTrue(stale.status() == PatternStartReservation.Status.RESERVED, "Same-group physical inputs complete the request");
                outside.nativeFluidHandler().drain(125, FluidAction.EXECUTE);
                stale.rollback();
                helper.assertTrue(host.reservePatternStart(outputs, capabilities).status() == PatternStartReservation.Status.UNAVAILABLE,
                        "Lost top-up rejects fresh admission before irreversible native payment");
                helper.assertTrue(items(outside) == 1 && holder.get(iron) == 1 && !fixture.runtime.active(), "Unpaid rejection preserves items");
                insertWater(helper, outside, 125);
            } else {
                holder.add(iron, 1);
                request = AE2NativeAdapters.requestInventory(new KeyCounter[]{holder});
                capabilities = List.of(new ItemBusCapability(host, AE2NativeAdapters.items(request), INPUT),
                        new FluidHatchCapability(host, AE2NativeAdapters.fluids(request), INPUT));
            }
            var complete = host.reservePatternStart(outputs, capabilities);
            helper.assertTrue(complete.status() == PatternStartReservation.Status.RESERVED && complete.commit(), "Complete native pattern admission pays once");
            holder.clear();
            helper.assertTrue(energy.energyStorage().getAmountAsLong() == 20 && items(outside) == (topUp ? 0 : 10)
                    && fluids(outside) == (topUp ? 0 : 1_000)
                    && host.getLogic().getReturnInv().getAvailableStacks().get(iron) == 9
                    && host.getLogic().getReturnInv().getAvailableStacks().get(water) == 1_000,
                    "Pattern admission pays only its request/group, checks shared FE, and never consumes returned resources");
            helper.assertTrue(fixture.runtime.activeInputScope().kind() == (isolated ? InputScope.Kind.ISOLATED : InputScope.Kind.ORDINARY)
                    && fixture.runtime.activeInputScope().sources().equals(topUp ? List.of(host.getBlockPos(), outside.getBlockPos())
                    : List.of(host.getBlockPos())), "Pattern execution pins actual host/group coordinates");
        }
        helper.succeed();
    }

    public static void chemicalIsolationStillSharesHeatAndEnergy(GameTestHelper helper) {
        try (var fixture = fixture(helper, "chemical", "chemical_input_hatch_basic", "chemical_input_hatch_basic", "heat_input_hatch", "energy_input_hatch")) {
            var first = (ChemicalPortBlockEntity) fixture.ports.get(0);
            var second = (ChemicalPortBlockEntity) fixture.ports.get(1);
            var heat = (HeatPortBlockEntity) fixture.ports.get(2);
            var energy = (EnergyInputHatchBlockEntity) fixture.ports.get(3);
            fixture.controller.setFullyInputIsolated(true);
            first.chemicalTank().setStack(new ChemicalStack(MekanismChemicals.OXYGEN, 500));
            second.chemicalTank().setStack(new ChemicalStack(MekanismChemicals.OXYGEN, 500));
            heat.heatCapacitor().setHeat(800 * heat.heatCapacitor().getHeatCapacity());
            double before = heat.heatCapacitor().getHeat();
            energy.energyStorage().setAmount(20);
            List<MachineRequirement> requirements = List.of(LoadedChemicalRequirement.input(ChemicalIngredient.chemical(
                    ResourceLocation.parse("mekanism:oxygen"), 1_000)), new EnergyRequirement(7), LoadedHeatRequirement.minimumTemperature(600));
            helper.assertFalse(fixture.context().planRequirements(requirements, 1, Map.of()).successful(), "Isolated chemical tanks cannot pool");
            helper.assertTrue(first.chemicalTank().getStored() == 500 && second.chemicalTank().getStored() == 500
                    && energy.energyStorage().getAmountAsLong() == 20, "Rejected chemical planning spends no resources");
            second.chemicalTank().setStack(new ChemicalStack(MekanismChemicals.OXYGEN, 1_000));
            var complete = fixture.context().planRequirements(requirements, 1, Map.of());
            helper.assertTrue(complete.successful() && complete.plan().inputScope().sources().equals(List.of(second.getBlockPos()))
                    && complete.plan().commit(), "One complete chemical source shares separate FE and heat ports");
            helper.assertTrue(first.chemicalTank().getStored() == 500 && second.chemicalTank().getStored() == 0
                    && energy.energyStorage().getAmountAsLong() == 13 && heat.heatCapacitor().getHeat() == before,
                    "Only selected chemicals and FE are spent; minimum temperature remains read-only");
        }
        helper.succeed();
    }

    private static long items(IOPortBlockEntity port) { return port.nativeItemHandler().getStackInSlot(0).getCount(); }
    private static long fluids(IOPortBlockEntity port) { return port.nativeFluidHandler().getFluidInTank(0).getAmount(); }
    private static void insertItem(GameTestHelper helper, IOPortBlockEntity port, ItemStack stack) {
        helper.assertTrue(port.nativeItemHandler().insertItem(0, stack.copy(), false).isEmpty(), "Native input accepts fixture items");
    }
    private static void insertWater(GameTestHelper helper, IOPortBlockEntity port, int amount) {
        helper.assertTrue(port.nativeFluidHandler().fill(new FluidStack(Fluids.WATER, amount), FluidAction.EXECUTE) == amount,
                "Native input accepts fixture water");
    }
    private static ItemRequirement item(int count) { return new ItemRequirement(IOType.INPUT, Ingredient.of(Items.IRON_INGOT), count, ItemStack.EMPTY); }
    private static FluidRequirement water(int amount) { return new FluidRequirement(IOType.INPUT, FluidIngredient.of(Fluids.WATER), amount, FluidStack.EMPTY); }
    private static MachineRecipe recipe(Fixture fixture, String name, List<MachineRequirement> requirements, List<MachineOutput> outputs) {
        return MachineRecipe.fromCanonical(fixture.machineId.withSuffix("_" + name), fixture.machineId, 4,
                requirements, outputs, List.of(), 0, 1, false, false, false, Set.of());
    }

    private static Fixture fixture(GameTestHelper helper, String name, String... portIds) {
        BlockPos controllerPos = new BlockPos(0, 1, 0);
        helper.setBlock(controllerPos, ModBlocks.controllerFor(MMCR.id("test_cube")).get().defaultBlockState()
                .setValue(MachineControllerBlock.FACING, Direction.SOUTH).setValue(MachineControllerBlock.ROLL_FACING, Direction.NORTH));
        Map<BlockPos, BlockPredicate> pattern = new LinkedHashMap<>();
        List<IOPortBlockEntity> ports = new ArrayList<>();
        for (int index = 0; index < portIds.length; index++) {
            var pos = new BlockPos(index + 1, 1, 0);
            var block = ModBlocks.BLOCKS.get(portIds[index]).get();
            helper.setBlock(pos, block.defaultBlockState());
            ports.add(helper.getBlockEntity(pos));
            pattern.put(pos.subtract(controllerPos), new BlockPredicate.OfBlock(block));
        }
        var machineId = MMCR.id("input_isolation_" + name);
        var machine = new DynamicMachine(machineId, name, new BlockArray(pattern), MachineControllerSpec.defaultsFor(machineId), PortRequirementSpec.none());
        MachineControllerBlockEntity controller = helper.getBlockEntity(controllerPos);
        try {
            var mode = MachineControllerBlockEntity.class.getDeclaredField("activeWorkMode");
            mode.setAccessible(true);
            var runtime = MachineControllerBlockEntity.class.getDeclaredField("runtime");
            runtime.setAccessible(true);
            var fixture = new Fixture(helper, controller, ports, ((MachineControllerRuntime) runtime.get(controller)).craftingRuntime(), machineId);
            try {
                helper.assertFalse(MachineRegistry.containsStatic(machineId), "Fixture registration starts clean");
                fixture.machineRegistered = true;
                MachineRegistry.register(machine);
                controller.setMachine(machine);
                var coordinator = SharedIoCoordinator.get(helper.getLevel());
                coordinator.beginExecutionRound(helper.getLevel().getGameTime(), true);
                controller.serverTick();
                helper.assertTrue(controller.currentStructureSnapshot().formed() && controller.currentStructureSnapshot().structureAreaLoaded()
                        && controller.resourceDomain() != null, "Real fixture forms and links ports at an explicit controller tick");
                helper.assertTrue(coordinator.controllerPermit(controller.getBlockPos()).eligible(coordinator.executionRoundId(),
                        helper.getLevel().getGameTime(), true), "Controller tick grants execution admission");
                mode.set(controller, MachineWorkMode.SYNC);
                return fixture;
            } catch (ReflectiveOperationException | RuntimeException | Error failure) {
                fixture.close();
                throw failure;
            }
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Cannot access attached fixture runtime", exception);
        }
    }

    /** Explicit execution rounds avoid scheduler timing assertions and restore shared registries/clock.
     * @author howxu <dev@howxu.cn>
     */
    private static final class Fixture implements AutoCloseable {
        private final GameTestHelper helper;
        private final MachineControllerBlockEntity controller;
        private final List<IOPortBlockEntity> ports;
        private final CraftingRuntime runtime;
        private final long originalTime;
        private final ResourceLocation machineId;
        private final Map<ResourceLocation, Machine> previousMachines;
        private final Map<ResourceLocation, MachineRecipe> previousDynamicRecipes;
        private boolean machineRegistered;
        private boolean recipesChanged;

        private Fixture(GameTestHelper helper, MachineControllerBlockEntity controller, List<IOPortBlockEntity> ports,
                        CraftingRuntime runtime, ResourceLocation machineId) {
            this.helper = helper;
            this.controller = controller;
            this.ports = List.copyOf(ports);
            this.runtime = runtime;
            this.machineId = machineId;
            previousMachines = MachineRegistry.effectiveSnapshot();
            previousDynamicRecipes = RecipeRegistry.dynamicSnapshot();
            originalTime = helper.getLevel().getGameTime();
        }
        private CraftingContext context() { return new CraftingContext(controller.componentRuntime().capabilitySnapshot()); }
        private void registerRecipe(MachineRecipe recipe) {
            var recipes = new LinkedHashMap<>(RecipeRegistry.dynamicSnapshot());
            recipes.put(recipe.id(), recipe);
            recipesChanged = true;
            RecipeRegistry.replaceDynamic(recipes);
        }
        private long nextRound() {
            long next = helper.getLevel().getGameTime() + 1;
            ((ServerLevelData) helper.getLevel().getLevelData()).setGameTime(next);
            return SharedIoCoordinator.get(helper.getLevel()).beginExecutionRound(next, true);
        }
        @Override public void close() {
            try {
                runtime.invalidate();
                SharedIoCoordinator.get(helper.getLevel()).cancel(controller.getBlockPos());
                controller.releaseStructureClaims();
            } finally {
                ((ServerLevelData) helper.getLevel().getLevelData()).setGameTime(originalTime);
                try {
                    if (recipesChanged) RecipeRegistry.replaceDynamic(previousDynamicRecipes);
                } finally {
                    if (machineRegistered) removeFixtureMachine();
                }
                helper.assertTrue(MachineRegistry.effectiveSnapshot().equals(previousMachines)
                        && RecipeRegistry.dynamicSnapshot().equals(previousDynamicRecipes), "Fixture cleanup restores existing registry snapshots");
            }
        }
        @SuppressWarnings("unchecked")
        private void removeFixtureMachine() {
            synchronized (RuntimeContentVersion.lock()) {
                try {
                    for (String name : List.of("STATIC_MACHINES", "COMPILED", "EFFECTIVE_MACHINES")) {
                        var field = MachineRegistry.class.getDeclaredField(name);
                        field.setAccessible(true);
                        var current = (Map<ResourceLocation, ?>) field.get(null);
                        if (name.equals("STATIC_MACHINES")) current.remove(machineId);
                        else {
                            var remaining = new LinkedHashMap<>(current);
                            remaining.remove(machineId);
                            field.set(null, Map.copyOf(remaining));
                        }
                    }
                } catch (ReflectiveOperationException exception) { throw new AssertionError("Cannot clean up fixture machine", exception); }
            }
        }
    }

    /** One independent vanilla DataSlot receiver per viewer.
     * @author howxu <dev@howxu.cn>
     */
    private static final class MenuWire implements ContainerSynchronizer {
        private final AbstractContainerMenu client;
        private int changes;
        private MenuWire(AbstractContainerMenu client) { this.client = client; }
        @Override public void sendInitialData(AbstractContainerMenu menu, NonNullList<ItemStack> items, ItemStack carried, int[] data) {
            for (int index = 0; index < data.length; index++) client.setData(index, data[index]);
        }
        @Override public void sendDataChange(AbstractContainerMenu menu, int id, int value) { changes++; client.setData(id, value); }
        @Override public void sendSlotChange(AbstractContainerMenu menu, int slot, ItemStack stack) { }
        @Override public void sendCarriedChange(AbstractContainerMenu menu, ItemStack stack) { }
    }
}
