package cn.howxu.mmcr;

import appeng.api.AECapabilities;
import appeng.api.behaviors.GenericSlotCapacities;
import appeng.api.behaviors.GenericInternalInventory;
import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.config.LockCraftingMode;
import appeng.api.config.Settings;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.ticking.IGridTickable;
import appeng.blockentity.crafting.CraftingBlockEntity;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.blockentity.crafting.MolecularAssemblerBlockEntity;
import appeng.api.ids.AEComponents;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.GridHelper;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.MEStorage;
import appeng.blockentity.networking.CreativeEnergyCellBlockEntity;
import appeng.blockentity.storage.MEChestBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.helpers.externalstorage.GenericStackInv;
import appeng.menu.implementations.InterfaceMenu;
import appeng.menu.implementations.PriorityMenu;
import appeng.menu.implementations.SetStockAmountMenu;
import appeng.menu.MenuOpener;
import appeng.menu.SlotSemantics;
import appeng.menu.implementations.UpgradeableMenu;
import appeng.items.tools.NetworkToolItem;
import appeng.menu.locator.MenuLocators;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.menu.AE2InterfaceMenu;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.menu.AE2MenuTypes;
import cn.howxu.mmcr.compat.extendedae.loaded.menu.ExtendedInterfaceMenu;
import cn.howxu.mmcr.compat.extendedae.loaded.menu.ExtendedAEMenuTypes;
import com.glodblock.github.extendedae.client.ExSemantics;
import com.glodblock.github.extendedae.config.EAEConfig;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.config.ServerConfig;
import cn.howxu.mmcr.internal.runtime.MachineWorkMode;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.facet.TransferFacet;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.recipe.MachineIngredient;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.compat.appliedenergistics2.AE2Bridge;
import cn.howxu.mmcr.compat.appmek.AppMekBridge;
import cn.howxu.mmcr.compat.mekanism.MekanismRecipeTypes;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.InputInterfaceBlockEntity;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.LoadedAE2Bridge;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.internal.capability.NativeStackSync;
import cn.howxu.mmcr.internal.port.PortFamilyIds;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.registry.ModBlocks;
import net.minecraft.network.PacketSendListener;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * End-to-end GameTest coverage for the AE2 input interface MMCR integration.
 *
 * @author howxu <dev@howxu.cn>
 */
public class AE2InterfaceGameTest {

    private static final ResourceLocation MACHINE_ID = MMCR.id("ae2_interface_integration_test");
    private static final ResourceLocation RECIPE_ID = MMCR.id("ae2_interface_integration_recipe");
    private static final long INITIAL_ITEM_COUNT = 16L;
    private static final long INITIAL_FLUID_AMOUNT = 2_000L;
    private static final long NETWORK_ITEM_AMOUNT = 16L;
    private static final long CONFIGURED_ITEM_AMOUNT = 8L;
    private static final long MANUAL_ITEM_AMOUNT = 4L;

    public void interfaceFeedsMmcrInputs(GameTestHelper helper) {
        ServerConfig.MACHINE_WORK_MODE.clearCache();
        ServerConfig.MACHINE_WORK_MODE.set(MachineWorkMode.SYNC);
        helper.assertTrue(AE2Bridge.get().available(),
                "AE2 must be loaded for this integration test");

        BlockPos portPos = new BlockPos(1, 2, 0);
        BlockPos outputPos = new BlockPos(1, 0, 0);
        helper.setBlock(portPos, ModBlocks.BLOCKS.get("ae2_me_input_interface").get().defaultBlockState());
        helper.setBlock(outputPos, ModBlocks.BLOCKS.get("item_output_bus").get().defaultBlockState());
        BlockPos portWorldPos = helper.absolutePos(portPos);

        BlockPos controllerPos = new BlockPos(1, 1, 0);
        helper.setBlock(controllerPos,
                ModBlocks.controllerFor(MMCR.id("test_cube")).get().defaultBlockState()
                        .setValue(MachineControllerBlock.FACING, Direction.SOUTH));

        Map<BlockPos, BlockPredicate> pattern = Map.of(
                new BlockPos(0, 1, 0), new BlockPredicate.OfBlock(
                        ModBlocks.BLOCKS.get("ae2_me_input_interface").get()),
                new BlockPos(0, -1, 0), new BlockPredicate.OfBlock(
                        ModBlocks.BLOCKS.get("item_output_bus").get()));
        DynamicMachine machine = new DynamicMachine(MACHINE_ID,
                "AE2 Interface Integration Test",
                new BlockArray(pattern));
        if (!MachineRegistry.containsStatic(MACHINE_ID)) {
            MachineRegistry.register(machine);
        }
        RecipeRegistry.registerStatic(MachineRecipe.fromCanonical(
                RECIPE_ID, MACHINE_ID, 20,
                List.of(
                        MachineRequirement.fromInput(new MachineIngredient.ItemIngredient(
                                Ingredient.of(Items.IRON_INGOT), 1)),
                        MachineRequirement.fromInput(new MachineIngredient.FluidIngredient(
                                FluidIngredient.of(Fluids.WATER), 1_000)),
                        MachineRequirement.itemOutput(new ItemStack(Items.IRON_NUGGET))),
                List.of(new MachineOutput.ItemOutput(new ItemStack(Items.IRON_NUGGET), 1F)),
                List.of(), 0, 1,
                false, false, false, Set.of()));

        MachineControllerBlockEntity controller = helper.getBlockEntity(controllerPos);
        controller.setMachine(machine);
        controller.setStructureCheckIntervalForTesting(1);
        helper.runAtTickTime(1, () -> {
            controller.setMachine(machine);
            controller.requestImmediateStructureCheck();
        });

        helper.runAtTickTime(2, () -> {
            InputInterfaceBlockEntity entity = helper.getBlockEntity(portPos);
            helper.assertTrue(entity != null,
                    "AE2 input interface resolves to AE2InputInterfaceBlockEntity");

            BlockState portState = helper.getLevel().getBlockState(portWorldPos);

            IInWorldGridNodeHost gridHost = helper.getLevel().getCapability(
                    AECapabilities.IN_WORLD_GRID_NODE_HOST, portWorldPos, null);
            helper.assertTrue(gridHost != null,
                    "AE2 IN_WORLD_GRID_NODE_HOST capability is exposed");

            GenericInternalInventory genericInv = helper.getLevel().getCapability(
                    AECapabilities.GENERIC_INTERNAL_INV, portWorldPos, portState, entity, null);
            helper.assertTrue(genericInv != null,
                    "AE2 GENERIC_INTERNAL_INV capability is exposed");
            MEStorage meStorage = helper.getLevel().getCapability(
                    AECapabilities.ME_STORAGE, portWorldPos, portState, entity, null);
            helper.assertTrue(meStorage != null,
                    "AE2 ME_STORAGE capability is exposed");

            CapabilitySnapshot snapshot = entity.capabilitySnapshot();
            List<MachineCapability> capabilities = snapshot.capabilities();
            helper.assertTrue(capabilities.size() == (AppMekBridge.get().available() ? 3 : 2),
                    "MMCR AE2 input interface exposes its supported resource capabilities");
            helper.assertTrue(capabilities.stream()
                            .map(MachineCapability::type)
                            .allMatch(type -> type.id().equals(PortFamilyIds.ITEM)
                                    || type.id().equals(PortFamilyIds.FLUID)
                                    || AppMekBridge.get().available() && type.id().equals(MekanismRecipeTypes.CHEMICAL)),
                    "MMCR AE2 input interface capabilities cover supported resource families");
            for (MachineCapability capability : capabilities) {
                helper.assertTrue(capability.facet(TransferFacet.class).isEmpty(),
                        "MMCR AE2 input interface capability does not expose TransferFacet");
            }

            GenericStackInv storageInv = entity.getInterfaceLogic().getStorage();
            entity.getInterfaceLogic().getConfig().setStack(0,
                    new GenericStack(AEItemKey.of(Items.IRON_INGOT), 8L));
            entity.getInterfaceLogic().getConfig().setStack(1,
                    new GenericStack(AEFluidKey.of(Fluids.WATER), 1_000L));
            helper.assertTrue(entity.getInterfaceLogic().getConfig().size() == 9,
                    "AE2 input interface config inventory has nine slots");
            helper.assertTrue(entity.getInterfaceLogic().getConfig().getStack(0) != null
                            && Objects.requireNonNull(entity.getInterfaceLogic().getConfig().getStack(0)).what()
                                    .equals(AEItemKey.of(Items.IRON_INGOT)),
                    "AE2 config slot 0 holds the iron filter");
            helper.assertTrue(entity.getInterfaceLogic().getConfig().getStack(1) != null
                            && Objects.requireNonNull(entity.getInterfaceLogic().getConfig().getStack(1)).what()
                                    .equals(AEFluidKey.of(Fluids.WATER)),
                    "AE2 config slot 1 holds the water filter");
            helper.assertTrue(entity.nativeItemHandler() instanceof NativeStackSync.Item,
                    "MMCR item capability uses the native AE2 item adapter");
            helper.assertTrue(entity.nativeFluidHandler() instanceof NativeStackSync.Fluid,
                    "MMCR fluid capability uses the native AE2 fluid adapter");

            AE2Bridge bridge = AE2Bridge.get();
            helper.assertTrue(bridge instanceof LoadedAE2Bridge,
                    "AE2 bridge resolves to LoadedAE2Bridge when AE2 is loaded");
            helper.assertTrue(bridge.available(),
                    "LoadedAE2Bridge reports available()");

            ServerPlayer player = makePlayer(helper);
            helper.assertFalse(bridge.openMenu(player, helper.getLevel(), new BlockPos(99, 99, 99)),
                    "AE2 bridge returns false for a non-AE2 input interface block");
            InterfaceMenu interfaceMenu = new AE2InterfaceMenu(AE2MenuTypes.INTERFACE, 0,
                    player.getInventory(), entity);
            helper.assertTrue(interfaceMenu.getType() == AE2MenuTypes.INTERFACE,
                    "Input interface uses the registered MMCR menu type");

            ServerPlayer connectedPlayer = makePlayerWithConnection(helper);
            helper.assertTrue(bridge.openMenu(connectedPlayer, helper.getLevel(), portWorldPos),
                    "AE2 bridge forwards the AE2 input interface to MenuOpener.open");
            helper.assertTrue(connectedPlayer.containerMenu instanceof AE2InterfaceMenu
                            && connectedPlayer.containerMenu.getType() == AE2MenuTypes.INTERFACE,
                    "Bridge opens the MMCR input menu through its actual factory");
            helper.assertTrue(entity.getInterfaceLogic().getUpgrades() != null,
                    "AE2 upgrade inventory is visible through host.getInterfaceLogic()");
            helper.assertTrue(entity.getInterfaceLogic().getUpgrades().isEmpty(),
                    "AE2 upgrade inventory slots are empty before any upgrade is installed");

            if (genericInv != null) {
                genericInv.insert(0, AEItemKey.of(Items.IRON_INGOT), INITIAL_ITEM_COUNT,
                        Actionable.MODULATE);
            }
            if (genericInv != null) {
                genericInv.insert(1, AEFluidKey.of(Fluids.WATER), INITIAL_FLUID_AMOUNT,
                        Actionable.MODULATE);
            }
            helper.assertTrue(entity.nativeItemHandler().getStackInSlot(0).getCount() == INITIAL_ITEM_COUNT,
                    "GENERIC_INTERNAL_INV item insert flows into the MMCR item storage view");
            helper.assertTrue(entity.nativeFluidHandler().getFluidInTank(1).getAmount() == INITIAL_FLUID_AMOUNT,
                    "GENERIC_INTERNAL_INV fluid insert flows into the MMCR fluid storage view");

        });

        helper.runAtTickTime(60, () -> {
            helper.runAfterDelay(40, () -> {
                int polls = 0;
                while (!controller.structureSnapshot().formed() && polls++ < 100) {
                    controller.serverTick();
                }
                InputInterfaceBlockEntity entity = helper.getBlockEntity(portPos);
                helper.assertTrue(controller.structureSnapshot().formed(),
                        "MMCR multiblock containing AE2 input interface forms");
                helper.assertTrue(entity.nativeItemHandler().getStackInSlot(0).getCount() <= INITIAL_ITEM_COUNT - 1L,
                        "MMCR recipe consumes an iron ingot from the AE2 local inventory amount="
                                + entity.nativeItemHandler().getStackInSlot(0).getCount());
                helper.assertTrue(entity.nativeFluidHandler().getFluidInTank(1).getAmount() <= INITIAL_FLUID_AMOUNT - 1_000L,
                        "MMCR recipe consumes 1000 mB of water from the AE2 local inventory fluidAmount="
                                + entity.nativeFluidHandler().getFluidInTank(1).getAmount());
                helper.assertTrue(controller.runtimeSnapshot().crafting().recipeId() == null,
                        "MMCR controller reports the recipe has completed");
                helper.assertTrue(controller.runtimeSnapshot().linkedPortPositions().contains(portWorldPos),
                        "MMCR controller links the AE2 input interface as an input port");

                long itemBeforeRollback = entity.nativeItemHandler().getStackInSlot(0).getCount();
                long fluidBeforeRollback = entity.nativeFluidHandler().getFluidInTank(1).getAmount();
                entity.nativeItemHandler().extractItem(0, 1, true);
                entity.nativeFluidHandler().drain(new FluidStack(Fluids.WATER, 1_000),
                        IFluidHandler.FluidAction.SIMULATE);
                helper.assertTrue(entity.nativeItemHandler().getStackInSlot(0).getCount() == itemBeforeRollback,
                        "AE2 local inventory item amount is unchanged after a simulated extraction");
                helper.assertTrue(entity.nativeFluidHandler().getFluidInTank(1).getAmount() == fluidBeforeRollback,
                        "AE2 local inventory fluid amount is unchanged after a simulated extraction");

                long itemBeforeCommit = entity.nativeItemHandler().getStackInSlot(0).getCount();
                entity.nativeItemHandler().extractItem(0, 1, false);
                helper.assertTrue(entity.nativeItemHandler().getStackInSlot(0).getCount() == itemBeforeCommit - 1L,
                        "AE2 local inventory item amount is reduced by 1 after a committed extraction amount="
                                + entity.nativeItemHandler().getStackInSlot(0).getCount());

                entity.getInterfaceLogic().getConfig().setStack(2,
                        new GenericStack(AEItemKey.of(Items.COAL), 4L));
                entity.getInterfaceLogic().setPriority(42);
                entity.getInterfaceLogic().getUpgrades().addItems(AEItems.FUZZY_CARD.stack());
                long itemBeforeReload = entity.nativeItemHandler().getStackInSlot(0).getCount();
                long fluidBeforeReload = entity.nativeFluidHandler().getFluidInTank(1).getAmount();
                long bucketDropsBefore = fluidBeforeReload / 1000L;
                reloadBlockEntity(entity, helper);
                helper.assertTrue(entity.nativeItemHandler().getStackInSlot(0).getCount() == itemBeforeReload,
                        "Item storage amount survives a save/load cycle");
                helper.assertTrue(entity.nativeFluidHandler().getFluidInTank(1).getAmount() == fluidBeforeReload,
                        "Fluid storage amount survives a save/load cycle");
                helper.assertTrue(entity.getInterfaceLogic().getConfig().getStack(0) != null
                                && Objects.requireNonNull(entity.getInterfaceLogic().getConfig().getStack(0)).what()
                                        .equals(AEItemKey.of(Items.IRON_INGOT)),
                        "Config slot 0 (iron) survives a save/load cycle");
                helper.assertTrue(entity.getInterfaceLogic().getConfig().getStack(2) != null
                                && Objects.requireNonNull(entity.getInterfaceLogic().getConfig().getStack(2)).what()
                                        .equals(AEItemKey.of(Items.COAL)),
                        "Config slot 2 (coal) added after reload setup survives the cycle");
                helper.assertTrue(entity.getInterfaceLogic().getPriority() == 42,
                        "AE2 interface priority survives a save/load cycle");
                helper.assertTrue(!entity.getInterfaceLogic().getUpgrades().isEmpty(),
                        "AE2 upgrade inventory survives a save/load cycle");

                helper.getLevel().destroyBlock(portWorldPos, true);
                helper.runAfterDelay(2, () -> {
                    long ironDrops = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                                    new AABB(portWorldPos).inflate(1))
                            .stream()
                            .filter(itemEntity -> itemEntity.getItem().is(Items.IRON_INGOT))
                            .mapToLong(itemEntity -> itemEntity.getItem().getCount())
                            .sum();
                    long upgradeDrops = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                                    new AABB(portWorldPos).inflate(1))
                            .stream()
                            .filter(itemEntity -> itemEntity.getItem().is(AEItems.FUZZY_CARD.asItem()))
                            .mapToLong(itemEntity -> itemEntity.getItem().getCount())
                            .sum();
                    helper.assertTrue(ironDrops == itemBeforeReload,
                            "AE2 logic.addDrops drops exactly the stored iron ingot amount");
                    helper.assertTrue(upgradeDrops == 1L,
                            "AE2 logic.addDrops drops the installed FUZZY_CARD upgrade");
                    if (bucketDropsBefore > 0) {
                        long bucketDrops = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                                        new AABB(portWorldPos).inflate(1))
                                .stream()
                                .filter(itemEntity -> itemEntity.getItem().is(Items.WATER_BUCKET))
                                .mapToLong(itemEntity -> itemEntity.getItem().getCount())
                                .sum();
                        helper.assertTrue(bucketDrops == bucketDropsBefore,
                                "AE2 logic.addDrops drops the stored water as water buckets");
                    }
                    helper.succeed();
                });
            });
        });
    }

    public void inputInterfaceDoesNotReturnManualCacheItems(GameTestHelper helper) {
        helper.assertTrue(AE2Bridge.get().available(),
                "AE2 must be loaded for this integration test");

        BlockPos inputPos = new BlockPos(0, 0, 0);
        BlockPos chestPos = new BlockPos(3, 0, 0);
        BlockPos energyPos = new BlockPos(3, 0, 2);
        helper.setBlock(inputPos, ModBlocks.BLOCKS.get("ae2_me_input_interface").get().defaultBlockState());
        helper.setBlock(chestPos, AEBlocks.ME_CHEST.block().defaultBlockState());
        helper.setBlock(energyPos, AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());

        helper.runAtTickTime(2, () -> {
            InputInterfaceBlockEntity input = helper.getBlockEntity(inputPos);
            MEChestBlockEntity chest = helper.getBlockEntity(chestPos);
            CreativeEnergyCellBlockEntity energy = helper.getBlockEntity(energyPos);
            helper.assertTrue(input != null && chest != null,
                    "Input interface and ME chest are available");
            chest.setCell(AEItems.ITEM_CELL_1K.stack());
            helper.assertTrue(energy != null && input.getMainNode().getNode() != null
                            && chest.getMainNode().getNode() != null && energy.getMainNode().getNode() != null,
                    "Input interface, ME chest, and energy cell have initialized grid nodes");
            GridHelper.createConnection(input.getMainNode().getNode(), chest.getMainNode().getNode());
            GridHelper.createConnection(input.getMainNode().getNode(), energy.getMainNode().getNode());
        });

        helper.runAtTickTime(4, () -> {
            InputInterfaceBlockEntity input = helper.getBlockEntity(inputPos);
            MEChestBlockEntity chest = helper.getBlockEntity(chestPos);
            MEStorage network = chest.getInventory();
            helper.assertTrue(network.insert(AEItemKey.of(Items.IRON_INGOT), NETWORK_ITEM_AMOUNT,
                            Actionable.MODULATE, IActionSource.empty()) == NETWORK_ITEM_AMOUNT,
                    "ME chest accepts the source items");
            input.getInterfaceLogic().getConfig().setStack(0,
                    new GenericStack(AEItemKey.of(Items.IRON_INGOT), CONFIGURED_ITEM_AMOUNT));
        });

        helper.runAtTickTime(20, () -> {
            InputInterfaceBlockEntity input = helper.getBlockEntity(inputPos);
            GenericStackInv storage = input.getInterfaceLogic().getStorage();
            helper.assertTrue(storage.getAmount(0) == CONFIGURED_ITEM_AMOUNT,
                    "Configured items are pulled into the input cache");

            storage.insert(0, AEItemKey.of(Items.IRON_INGOT), MANUAL_ITEM_AMOUNT, Actionable.MODULATE);
            input.getInterfaceLogic().getConfig().setStack(0, null);
        });

        helper.runAtTickTime(30, () -> {
            InputInterfaceBlockEntity input = helper.getBlockEntity(inputPos);
            helper.assertTrue(input.getInterfaceLogic().getStorage().getAmount(0) == MANUAL_ITEM_AMOUNT,
                    "Manually inserted items remain in the input cache after config cancellation, actual="
                            + input.getInterfaceLogic().getStorage().getAmount(0));
            helper.succeed();
        });
    }

    public void extendedInputReturnsOnlyAeOwnedResourcesAfterCommittedExtraction(GameTestHelper helper) {
        helper.assertTrue(AE2Bridge.get().available(), "AE2 must be loaded for this integration test");

        BlockPos inputPos = new BlockPos(0, 0, 0);
        BlockPos chestPos = new BlockPos(3, 0, 0);
        BlockPos energyPos = new BlockPos(3, 0, 2);
        helper.setBlock(inputPos, ModBlocks.BLOCKS.get("eae_me_extended_input_interface").get().defaultBlockState());
        helper.setBlock(chestPos, AEBlocks.ME_CHEST.block().defaultBlockState());
        helper.setBlock(energyPos, AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());

        helper.runAtTickTime(2, () -> {
            InputInterfaceBlockEntity input = helper.getBlockEntity(inputPos);
            MEChestBlockEntity chest = helper.getBlockEntity(chestPos);
            CreativeEnergyCellBlockEntity energy = helper.getBlockEntity(energyPos);
            chest.setCell(AEItems.ITEM_CELL_1K.stack());
            helper.assertTrue(input != null && chest != null && energy != null
                            && input.getMainNode().getNode() != null && chest.getMainNode().getNode() != null
                            && energy.getMainNode().getNode() != null,
                    "ExtendedAE input interface and its ME network initialize");
            GridHelper.createConnection(input.getMainNode().getNode(), chest.getMainNode().getNode());
            GridHelper.createConnection(input.getMainNode().getNode(), energy.getMainNode().getNode());
        });

        helper.runAtTickTime(4, () -> {
            InputInterfaceBlockEntity input = helper.getBlockEntity(inputPos);
            MEChestBlockEntity chest = helper.getBlockEntity(chestPos);
            helper.assertTrue(chest.getInventory().insert(AEItemKey.of(Items.IRON_INGOT), NETWORK_ITEM_AMOUNT,
                            Actionable.MODULATE, IActionSource.empty()) == NETWORK_ITEM_AMOUNT,
                    "ME network accepts the ExtendedAE input source items");
            input.getInterfaceLogic().getConfig().setStack(35,
                    new GenericStack(AEItemKey.of(Items.IRON_INGOT), CONFIGURED_ITEM_AMOUNT));
        });

        helper.runAtTickTime(20, () -> {
            InputInterfaceBlockEntity input = helper.getBlockEntity(inputPos);
            helper.assertTrue(input.getInterfaceLogic().getStorage().getAmount(35) == CONFIGURED_ITEM_AMOUNT,
                    "ExtendedAE slot 35 stocks the configured AE-owned resources");
            helper.assertTrue(input.nativeItemHandler().extractItem(35, 1, false).getCount() == 1,
                    "Committed machine extraction consumes an AE-owned resource");
            input.getInterfaceLogic().getStorage().insert(35, AEItemKey.of(Items.IRON_INGOT), MANUAL_ITEM_AMOUNT,
                    Actionable.MODULATE);
            input.getInterfaceLogic().getConfig().setStack(35, null);
        });

        helper.runAtTickTime(30, () -> {
            InputInterfaceBlockEntity input = helper.getBlockEntity(inputPos);
            MEChestBlockEntity chest = helper.getBlockEntity(chestPos);
            helper.assertTrue(input.getInterfaceLogic().getStorage().getAmount(35) == MANUAL_ITEM_AMOUNT,
                    "Config cancellation returns only the remaining AE-owned resources, preserving manual cache items");
            helper.assertTrue(chest.getInventory().extract(AEItemKey.of(Items.IRON_INGOT), NETWORK_ITEM_AMOUNT - 1L,
                            Actionable.SIMULATE, IActionSource.empty()) == NETWORK_ITEM_AMOUNT - 1L,
                    "The network receives all returnable resources except the committed machine extraction");
            helper.succeed();
        });
    }

    public void inputInterfaceMemoryCardRoundTrip(GameTestHelper helper) {
        BlockPos sourcePos = new BlockPos(0, 0, 0);
        BlockPos targetPos = new BlockPos(2, 0, 0);
        BlockPos outputPos = new BlockPos(4, 0, 0);
        helper.setBlock(sourcePos, ModBlocks.BLOCKS.get("ae2_me_input_interface").get().defaultBlockState());
        helper.setBlock(targetPos, ModBlocks.BLOCKS.get("ae2_me_input_interface").get().defaultBlockState());
        helper.setBlock(outputPos, ModBlocks.BLOCKS.get("ae2_me_output_interface").get().defaultBlockState());

        helper.runAtTickTime(2, () -> {
            InputInterfaceBlockEntity source = helper.getBlockEntity(sourcePos);
            InputInterfaceBlockEntity target = helper.getBlockEntity(targetPos);
            ServerPlayer player = makePlayerWithConnection(helper);
            player.getAbilities().instabuild = true;
            ItemStack card = AEItems.MEMORY_CARD.stack();

            helper.assertTrue(source.getInterfaceLogic().getUpgrades().addItems(AEItems.FUZZY_CARD.stack()).isEmpty(),
                    "Input interface accepts AE2 fuzzy cards");
            source.getInterfaceLogic().getUpgrades().setItemDirect(0, ItemStack.EMPTY);
            helper.assertTrue(source.getInterfaceLogic().getUpgrades().addItems(AEItems.CRAFTING_CARD.stack()).isEmpty(),
                    "Input interface accepts AE2 crafting cards");
            source.getInterfaceLogic().getConfig().setStack(0,
                    new GenericStack(AEItemKey.of(Items.IRON_INGOT), CONFIGURED_ITEM_AMOUNT));
            source.getInterfaceLogic().setPriority(42);

            player.setPose(Pose.CROUCHING);
            helper.assertTrue(useMemoryCard(helper, sourcePos, player, card).consumesAction(),
                    "Sneak-use saves the input interface to the memory card");
            player.setPose(Pose.STANDING);
            helper.assertTrue(useMemoryCard(helper, targetPos, player, card).consumesAction(),
                    "Normal use restores the input interface from the memory card");
            helper.assertTrue(target.getInterfaceLogic().getConfig().getStack(0).what()
                            .equals(AEItemKey.of(Items.IRON_INGOT)),
                    "Memory-card restore copies the input config inventory");
            helper.assertTrue(target.getInterfaceLogic().getPriority() == 42,
                    "Memory-card restore copies interface priority");
            helper.assertTrue(target.getInterfaceLogic().getUpgrades().isInstalled(AEItems.CRAFTING_CARD),
                    "Memory-card restore copies installed input upgrades");

            ItemStack outputCard = AEItems.MEMORY_CARD.stack();
            helper.assertTrue(useMemoryCard(helper, outputPos, player, outputCard) == InteractionResult.PASS,
                    "Output interfaces do not claim memory-card interactions");
            helper.assertTrue(outputCard.get(AEComponents.EXPORTED_SETTINGS_SOURCE) == null,
                    "Output interfaces do not write memory-card data");
            helper.succeed();
        });
    }

    public void fuzzyCardRestocksDamagedItem(GameTestHelper helper) {
        fuzzyCardRestocksDamagedItem(helper, "ae2_me_input_interface");
    }

    public void extendedFuzzyCardRestocksDamagedItem(GameTestHelper helper) {
        fuzzyCardRestocksDamagedItem(helper, "eae_me_extended_input_interface");
    }

    private static void fuzzyCardRestocksDamagedItem(GameTestHelper helper, String id) {
        BlockPos chestPos = new BlockPos(3, 0, 0);
        BlockPos energyPos = new BlockPos(3, 0, 2);
        helper.setBlock(BlockPos.ZERO, ModBlocks.BLOCKS.get(id).get().defaultBlockState());
        helper.setBlock(chestPos, AEBlocks.ME_CHEST.block().defaultBlockState());
        helper.setBlock(energyPos, AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        InputInterfaceBlockEntity host = helper.getBlockEntity(BlockPos.ZERO);
        MEChestBlockEntity chest = helper.getBlockEntity(chestPos);
        CreativeEnergyCellBlockEntity energy = helper.getBlockEntity(energyPos);
        chest.setCell(AEItems.ITEM_CELL_1K.stack());
        ServerPlayer player = makePlayerWithConnection(helper);
        host.openMenu(player, MenuLocators.forBlockEntity(host));
        var menu = (UpgradeableMenu<?>) player.containerMenu;
        boolean extended = menu instanceof ExtendedInterfaceMenu;
        if (extended) ((ExtendedInterfaceMenu) menu).setPage(1);
        int slot = extended ? 35 : 0;
        ItemStack requested = Items.IRON_SWORD.getDefaultInstance();
        requested.setDamageValue(5);
        ItemStack available = Items.IRON_SWORD.getDefaultInstance();
        available.setDamageValue(20);
        AEItemKey requestedKey = AEItemKey.of(requested);
        AEItemKey availableKey = AEItemKey.of(available);
        menu.setCarried(AEItems.FUZZY_CARD.stack());
        Slot upgrade = menu.getSlots(SlotSemantics.UPGRADE).getFirst();
        menu.clicked(upgrade.index, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().isEmpty() && host.getUpgrades().isInstalled(AEItems.FUZZY_CARD),
                "Actual menu click installs the fuzzy card through native upgrade callbacks");
        host.getConfigManager().putSetting(Settings.FUZZY_MODE, FuzzyMode.IGNORE_ALL);
        menu.broadcastChanges();
        helper.assertTrue(menu.getFuzzyMode() == FuzzyMode.IGNORE_ALL, "Own menu retains fuzzy setting synchronization");
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(host.getMainNode().getNode() != null
                        && chest.getMainNode().getNode() != null && energy.getMainNode().getNode() != null,
                "Fuzzy stocking network nodes initialize"))
                .thenExecute(() -> {
                    GridHelper.createConnection(host.getMainNode().getNode(), chest.getMainNode().getNode());
                    GridHelper.createConnection(host.getMainNode().getNode(), energy.getMainNode().getNode());
                }).thenWaitUntil(() -> helper.assertTrue(host.getMainNode().getNode().isActive(),
                        "Input joins the powered network"))
                .thenExecute(() -> {
                    helper.assertTrue(chest.getInventory().insert(availableKey, 1L, Actionable.MODULATE, IActionSource.empty()) == 1L,
                            "Network contains only the differently damaged sword");
                    host.getConfig().setStack(slot, new GenericStack(requestedKey, 1L));
                }).thenWaitUntil(() -> helper.assertTrue(new GenericStack(availableKey, 1L)
                                .equals(host.getInterfaceLogic().getStorage().getStack(slot)),
                        "Fuzzy card performs actual restocking with a non-exact key"))
                .thenExecute(() -> {
                    helper.assertTrue(chest.getInventory().extract(availableKey, Long.MAX_VALUE,
                                    Actionable.SIMULATE, IActionSource.empty()) == 0L,
                            "Fuzzy stocking consumes the real network resource exactly once");
                    menu.clicked(upgrade.index, 0, ClickType.PICKUP, player);
                    helper.assertTrue(menu.getCarried().is(AEItems.FUZZY_CARD.asItem())
                                    && !host.getUpgrades().isInstalled(AEItems.FUZZY_CARD),
                            "Removing the card uses the real upgrade slot callback");
                    player.getInventory().add(menu.getCarried());
                    menu.setCarried(ItemStack.EMPTY);
                    Slot stored = extended ? menu.getSlots(ExSemantics.EX_8).getLast()
                            : menu.getSlots(SlotSemantics.STORAGE).getFirst();
                    menu.quickMoveStack(player, stored.index);
                    ItemStack recovered = player.getInventory().items.stream()
                            .filter(stack -> availableKey.equals(AEItemKey.of(stack))).findFirst().orElseThrow();
                    helper.assertTrue(recovered.getCount() == 1 && host.getInterfaceLogic().getStorage().getStack(slot) == null,
                            "Actual extraction clears the fuzzy cache without losing the sword");
                    helper.assertTrue(chest.getInventory().insert(AEItemKey.of(recovered), 1L,
                                    Actionable.MODULATE, IActionSource.empty()) == 1L,
                            "Recovered sword is returned to the network for the no-card control");
                    recovered.shrink(1);
                    host.getConfig().setStack(slot, null);
                    host.getConfig().setStack(slot, new GenericStack(requestedKey, 1L));
                    exerciseNativeStocking(host);
                    helper.assertTrue(host.getInterfaceLogic().getStorage().getStack(slot) == null
                                    && chest.getInventory().extract(availableKey, Long.MAX_VALUE,
                                    Actionable.SIMULATE, IActionSource.empty()) == 1L,
                            "Native exact-match stocking without a fuzzy card does not consume the mismatched sword");
                }).thenSucceed();
    }

    public void craftingCardActuallyRestocksAndCancels(GameTestHelper helper) {
        craftingCardActuallyRestocksAndCancels(helper, "ae2_me_input_interface");
    }

    public void extendedCraftingCardActuallyRestocksAndCancels(GameTestHelper helper) {
        craftingCardActuallyRestocksAndCancels(helper, "eae_me_extended_input_interface");
    }

    private static void craftingCardActuallyRestocksAndCancels(GameTestHelper helper, String id) {
        // Stay in the test origin's forced chunk and outside neighbouring tests' horizontal fixtures.
        BlockPos providerPos = new BlockPos(0, 2, 0);
        BlockPos assemblerPos = new BlockPos(0, 3, 0);
        BlockPos chestPos = new BlockPos(0, 5, 0);
        BlockPos energyPos = new BlockPos(0, 7, 0);
        BlockPos cpuPos = new BlockPos(0, 9, 0);
        helper.setBlock(BlockPos.ZERO, ModBlocks.BLOCKS.get(id).get().defaultBlockState());
        helper.setBlock(providerPos, AEBlocks.PATTERN_PROVIDER.block().defaultBlockState());
        helper.setBlock(assemblerPos, AEBlocks.MOLECULAR_ASSEMBLER.block().defaultBlockState());
        helper.setBlock(chestPos, AEBlocks.ME_CHEST.block().defaultBlockState());
        helper.setBlock(energyPos, AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        helper.setBlock(cpuPos, AEBlocks.CRAFTING_STORAGE_64K.block().defaultBlockState());
        InputInterfaceBlockEntity host = helper.getBlockEntity(BlockPos.ZERO);
        PatternProviderBlockEntity provider = helper.getBlockEntity(providerPos);
        MolecularAssemblerBlockEntity assembler = helper.getBlockEntity(assemblerPos);
        MEChestBlockEntity chest = helper.getBlockEntity(chestPos);
        CreativeEnergyCellBlockEntity energy = helper.getBlockEntity(energyPos);
        CraftingBlockEntity cpu = helper.getBlockEntity(cpuPos);
        chest.setCell(AEItems.ITEM_CELL_1K.stack());
        var ingredients = NonNullList.withSize(9, ItemStack.EMPTY);
        ingredients.set(0, Items.IRON_INGOT.getDefaultInstance());
        var input = CraftingInput.of(3, 3, ingredients);
        var recipe = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel()).orElseThrow();
        provider.getLogic().getPatternInv().setItemDirect(0, PatternDetailsHelper.encodeCraftingPattern(recipe,
                ingredients.toArray(ItemStack[]::new), recipe.value().assemble(input, helper.getLevel().registryAccess()), false, false));
        ServerPlayer player = makePlayerWithConnection(helper);
        host.openMenu(player, MenuLocators.forBlockEntity(host));
        var menu = (UpgradeableMenu<?>) player.containerMenu;
        int slot = menu instanceof ExtendedInterfaceMenu ? 35 : 0;
        if (menu instanceof ExtendedInterfaceMenu extended) extended.setPage(1);
        Slot upgrade = menu.getSlots(SlotSemantics.UPGRADE).getFirst();
        player.getInventory().setItem(0, AEItems.CRAFTING_CARD.stack());
        menu.quickMoveStack(player, menu.getSlots(SlotSemantics.PLAYER_HOTBAR).getFirst().index);
        helper.assertTrue(host.getUpgrades().isInstalled(AEItems.CRAFTING_CARD) && player.getInventory().getItem(0).isEmpty(),
                "Shift installation moves the actual crafting card into native upgrade inventory");
        helper.startSequence().thenWaitUntil(() -> {
                    for (BlockEntity entity : List.of(host, provider, assembler, chest, energy, cpu)) {
                        helper.assertTrue(!entity.isRemoved()
                                        && helper.getLevel().getBlockEntity(entity.getBlockPos()) == entity,
                                "Crafting fixture retains its original block entity: " + entity.getBlockPos());
                    }
                    helper.assertTrue(host.getMainNode().getNode() != null, "Crafting input node initializes");
                    helper.assertTrue(provider.getMainNode().getNode() != null, "Native crafting provider node initializes");
                    helper.assertTrue(assembler.getMainNode().getNode() != null, "Native molecular assembler node initializes");
                    helper.assertTrue(chest.getMainNode().getNode() != null, "Native crafting storage chest node initializes");
                    helper.assertTrue(energy.getMainNode().getNode() != null, "Native crafting energy node initializes");
                    helper.assertTrue(cpu.getMainNode().getNode() != null, "Native crafting CPU node initializes");
                })
                .thenExecute(() -> {
                    var node = host.getMainNode().getNode();
                    GridHelper.createConnection(node, provider.getMainNode().getNode());
                    GridHelper.createConnection(node, assembler.getMainNode().getNode());
                    GridHelper.createConnection(node, chest.getMainNode().getNode());
                    GridHelper.createConnection(node, energy.getMainNode().getNode());
                    GridHelper.createConnection(node, cpu.getMainNode().getNode());
                    provider.getLogic().updatePatterns();
                }).thenWaitUntil(() -> helper.assertTrue(host.getMainNode().getNode().isActive()
                                && cpu.getCluster() != null && cpu.getCluster().isActive()
                                && host.getMainNode().getGrid().getCraftingService().isCraftable(AEItemKey.of(Items.IRON_NUGGET)),
                        "Powered native CPU and assembler advertise the real nugget crafting recipe"))
                .thenExecute(() -> {
                    helper.assertTrue(chest.getInventory().extract(AEItemKey.of(Items.IRON_NUGGET), Long.MAX_VALUE,
                                    Actionable.SIMULATE, IActionSource.empty()) == 0L,
                            "Target material is absent before the card request");
                    helper.assertTrue(chest.getInventory().insert(AEItemKey.of(Items.IRON_INGOT), 2L,
                                    Actionable.MODULATE, IActionSource.empty()) == 2L, "Network accepts real crafting inputs");
                    host.getConfig().setStack(slot, new GenericStack(AEItemKey.of(Items.IRON_NUGGET), 9L));
                }).thenWaitUntil(() -> helper.assertTrue(new GenericStack(AEItemKey.of(Items.IRON_NUGGET), 9L)
                                .equals(host.getInterfaceLogic().getStorage().getStack(slot)) && !cpu.getCluster().craftingLogic.hasJob(),
                        "Missing stock triggers an actual CPU craft and its result fills the own interface cache"))
                .thenExecute(() -> {
                    helper.assertTrue(chest.getInventory().extract(AEItemKey.of(Items.IRON_INGOT), Long.MAX_VALUE,
                                    Actionable.SIMULATE, IActionSource.empty()) == 1L,
                            "One ingot was consumed to produce exactly nine cached nuggets");
                    provider.getConfigManager().putSetting(Settings.LOCK_CRAFTING_MODE, LockCraftingMode.LOCK_WHILE_LOW);
                    provider.getLogic().updateRedstoneState();
                    Slot stored = slot == 35 ? menu.getSlots(ExSemantics.EX_8).getLast()
                            : menu.getSlots(SlotSemantics.STORAGE).getFirst();
                    menu.quickMoveStack(player, stored.index);
                    helper.assertTrue(player.getInventory().items.stream()
                                    .filter(stack -> stack.is(Items.IRON_NUGGET)).mapToInt(ItemStack::getCount).sum() == 9,
                            "The completed crafted stock is actually extracted into the player inventory");
                }).thenWaitUntil(() -> helper.assertTrue(host.getInterfaceLogic().getRequestedJobs().stream()
                                .anyMatch(link -> !link.isCanceled() && !link.isDone()),
                        "A second shortage creates a real tracked job while native provider redstone-lock blocks execution"))
                .thenExecute(() -> {
                    var links = List.copyOf(host.getInterfaceLogic().getRequestedJobs());
                    menu.clicked(upgrade.index, 0, ClickType.PICKUP, player);
                    helper.assertTrue(menu.getCarried().is(AEItems.CRAFTING_CARD.asItem())
                                    && !host.getUpgrades().isInstalled(AEItems.CRAFTING_CARD)
                                    && links.stream().allMatch(link -> link.isCanceled() || link.isDone())
                                    && host.getInterfaceLogic().getRequestedJobs().isEmpty(),
                            "Actual card removal cancels the native tracker's pending crafting links");
                    exerciseNativeStocking(host);
                    helper.assertTrue(host.getInterfaceLogic().getStorage().getStack(slot) == null
                                    && host.getInterfaceLogic().getRequestedJobs().isEmpty(),
                            "Without the crafting card the missing stock cannot start another crafting job");
                }).thenWaitUntil(() -> helper.assertTrue(!cpu.getCluster().craftingLogic.hasJob()
                                && chest.getInventory().extract(AEItemKey.of(Items.IRON_INGOT), Long.MAX_VALUE,
                                Actionable.SIMULATE, IActionSource.empty()) == 1L,
                        "Cancelled native CPU releases its input back to the network without consuming another ingot"))
                .thenSucceed();
    }

    private static void exerciseNativeStocking(InputInterfaceBlockEntity host) {
        var node = host.getMainNode().getNode();
        var ticker = node.getService(IGridTickable.class);
        for (int i = 0; i < 16; i++) ticker.tickingRequest(node, 1);
    }

    public void inputUpgradeToolboxAndOversizeAmount(GameTestHelper helper) {
        for (String id : List.of("ae2_me_input_interface", "eae_me_extended_input_interface", "eae_me_oversize_input_interface")) {
            BlockPos pos = new BlockPos(id.contains("oversize") ? 4 : id.startsWith("eae") ? 2 : 0, 0, 0);
            helper.setBlock(pos, ModBlocks.BLOCKS.get(id).get().defaultBlockState());
            InputInterfaceBlockEntity host = helper.getBlockEntity(pos);
            var upgrades = host.getUpgrades();
            var configManager = host.getConfigManager();
            ServerPlayer player = makePlayerWithConnection(helper);
            player.getInventory().setItem(8, AEItems.NETWORK_TOOL.stack());
            host.openMenu(player, MenuLocators.forBlockEntity(host));
            var menu = (UpgradeableMenu<?>) player.containerMenu;
            helper.assertTrue(menu.getToolbox().isPresent() && menu.getUpgrades() == upgrades,
                    "Every input profile retains its native upgrade inventory and equipped toolbox");
            Slot toolbox = menu.getSlots(SlotSemantics.TOOLBOX).getFirst();
            menu.setCarried(AEItems.FUZZY_CARD.stack());
            menu.clicked(toolbox.index, 0, ClickType.PICKUP, player);
            helper.assertTrue(menu.getCarried().isEmpty() && toolbox.getItem().is(AEItems.FUZZY_CARD.asItem()),
                    "Actual toolbox insertion stores the upgrade in the network tool");
            menu.quickMoveStack(player, toolbox.index);
            helper.assertTrue(upgrades.isInstalled(AEItems.FUZZY_CARD) && toolbox.getItem().isEmpty(),
                    "Shift toolbox action installs the card through native callbacks");
            Slot upgrade = menu.getSlots(SlotSemantics.UPGRADE).getFirst();
            menu.quickMoveStack(player, upgrade.index);
            helper.assertTrue(!upgrades.isInstalled(AEItems.FUZZY_CARD) && toolbox.getItem().is(AEItems.FUZZY_CARD.asItem()),
                    "Shift removal returns the same card to toolbox storage");
            menu.clicked(toolbox.index, 0, ClickType.PICKUP, player);
            menu.clicked(upgrade.index, 0, ClickType.PICKUP, player);
            menu.clicked(upgrade.index, 0, ClickType.PICKUP, player);
            helper.assertTrue(menu.getCarried().is(AEItems.FUZZY_CARD.asItem()) && menu.getCarried().getCount() == 1
                            && upgrades.isEmpty() && toolbox.getItem().isEmpty(),
                    "Manual install/removal conserves the card and invokes the same native inventory");
            helper.assertTrue(NetworkToolItem.getInventory(player.getInventory().getItem(8)).isEmpty(),
                    "Toolbox state is persisted to the real tool stack");
            int slot = menu instanceof ExtendedInterfaceMenu ? 35 : 0;
            if (menu instanceof ExtendedInterfaceMenu extended) extended.setPage(1);
            host.getConfig().setStack(slot, new GenericStack(AEItemKey.of(Items.IRON_INGOT), 1L));
            if (menu instanceof ExtendedInterfaceMenu extended) extended.openSetAmountMenu(slot);
            else ((AE2InterfaceMenu) menu).openSetAmountMenu(slot);
            var amount = (SetStockAmountMenu) player.containerMenu;
            int requested = id.contains("oversize") ? 20_000 : 7;
            AEItemKey key = AEItemKey.of(Items.IRON_INGOT);
            long nativeLimit = Math.min(key.getMaxStackSize(),
                    GenericSlotCapacities.getMap().getOrDefault(key.getType(), Long.MAX_VALUE));
            if (id.contains("oversize")) nativeLimit *= EAEConfig.getOversizeMultiplier(key.getType());
            long expected = Math.min(requested, nativeLimit);
            helper.assertTrue(amount.getMaxAmount() == nativeLimit && expected > amount.getInitialAmount(),
                    "Quantity page exposes the native profile limit and will change its initial request for " + id);
            amount.confirm(requested);
            helper.assertTrue(host.getConfig().getAmount(slot) == expected && host.getConfigManager() == configManager
                            && host.getUpgrades() == upgrades && player.containerMenu.getType() == AE2MenuTypes.typeFor(host.kind()),
                    "Quantity confirmation applies native clamping while preserving config manager, upgrades and own route for " + id);
        }
        helper.succeed();
    }

    public void amountAndPriorityReturnToOwnMenu(GameTestHelper helper) {
        helper.setBlock(BlockPos.ZERO, ModBlocks.BLOCKS.get("ae2_me_input_interface").get().defaultBlockState());
        InputInterfaceBlockEntity host = helper.getBlockEntity(BlockPos.ZERO);
        ServerPlayer player = makePlayerWithConnection(helper);
        host.getConfig().setStack(0, new GenericStack(AEItemKey.of(Items.IRON_INGOT), 1L));
        var locator = MenuLocators.forBlockEntity(host);
        host.openMenu(player, locator);
        helper.assertTrue(player.containerMenu instanceof AE2InterfaceMenu, "Host opens its own main menu");
        ((AE2InterfaceMenu) player.containerMenu).openSetAmountMenu(0);
        helper.assertTrue(player.containerMenu instanceof SetStockAmountMenu, "Real stock amount sub-menu opens");
        ((SetStockAmountMenu) player.containerMenu).confirm(7);
        helper.assertTrue(host.getConfig().getAmount(0) == 7L, "Confirm updates the host stock request");
        helper.assertTrue(player.containerMenu instanceof AE2InterfaceMenu
                        && player.containerMenu.getType() == AE2MenuTypes.INTERFACE,
                "Confirm returns through the MMCR menu factory");
        MenuOpener.open(PriorityMenu.TYPE, player, locator);
        helper.assertTrue(player.containerMenu instanceof PriorityMenu, "Real priority sub-menu opens");
        PriorityMenu priority = (PriorityMenu) player.containerMenu;
        priority.setPriority(43);
        host.returnToMainMenu(player, priority);
        helper.assertTrue(host.getPriority() == 43 && player.containerMenu.getType() == AE2MenuTypes.INTERFACE,
                "Priority update and locator-based return preserve the own menu route");
        helper.succeed();
    }

    public void extendedAmountReturnRetainsTransientPage(GameTestHelper helper) {
        helper.setBlock(BlockPos.ZERO, ModBlocks.BLOCKS.get("eae_me_extended_input_interface").get().defaultBlockState());
        InputInterfaceBlockEntity host = helper.getBlockEntity(BlockPos.ZERO);
        ServerPlayer player = makePlayerWithConnection(helper);
        host.getConfig().setStack(35, new GenericStack(AEItemKey.of(Items.IRON_INGOT), 1L));
        host.openMenu(player, MenuLocators.forBlockEntity(host));
        ExtendedInterfaceMenu menu = (ExtendedInterfaceMenu) player.containerMenu;
        menu.setPage(1);
        menu.openSetAmountMenu(35);
        helper.assertTrue(player.containerMenu instanceof SetStockAmountMenu, "Slot 35 amount sub-menu opens");
        var amountMenu = (SetStockAmountMenu) player.containerMenu;
        AEItemKey key = AEItemKey.of(Items.IRON_INGOT);
        long nativeLimit = Math.min(key.getMaxStackSize(),
                GenericSlotCapacities.getMap().getOrDefault(key.getType(), Long.MAX_VALUE));
        long expected = Math.min(123L, nativeLimit);
        helper.assertTrue(amountMenu.getMaxAmount() == nativeLimit && expected > amountMenu.getInitialAmount(),
                "Extended quantity page retains native item limits and will change slot 35's request");
        amountMenu.confirm(123);
        menu = (ExtendedInterfaceMenu) player.containerMenu;
        helper.assertTrue(menu.getType() == ExtendedAEMenuTypes.INTERFACE && menu.page == 1
                        && menu.getConfigSlots().get(35).isActive()
                        && new GenericStack(key, expected).equals(host.getConfig().getStack(35)),
                "Amount confirmation clamps the request natively and returns to page 1 with slot 35 active");
        player.closeContainer();
        host.openMenu(player, MenuLocators.forBlockEntity(host));
        helper.assertTrue(((ExtendedInterfaceMenu) player.containerMenu).page == 1,
                "Reopening the same host retains the selected page");
        player.closeContainer();
        helper.setBlock(BlockPos.ZERO, Blocks.AIR.defaultBlockState());
        helper.setBlock(BlockPos.ZERO, ModBlocks.BLOCKS.get("eae_me_extended_input_interface").get().defaultBlockState());
        InputInterfaceBlockEntity replacement = helper.getBlockEntity(BlockPos.ZERO);
        replacement.openMenu(player, MenuLocators.forBlockEntity(replacement));
        helper.assertTrue(replacement != host && ((ExtendedInterfaceMenu) player.containerMenu).page == 0,
                "Replacing the host resets transient page state");
        helper.succeed();
    }

    private static InteractionResult useMemoryCard(GameTestHelper helper, BlockPos pos,
                                                   ServerPlayer player, ItemStack card) {
        BlockPos worldPos = helper.absolutePos(pos);
        player.setItemInHand(InteractionHand.MAIN_HAND, card);
        return helper.getLevel().getBlockState(worldPos).useItemOn(card, helper.getLevel(), player,
                InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(worldPos), Direction.UP, worldPos, false)).result();
    }

    private static ServerPlayer makePlayer(GameTestHelper helper) {
        return new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new GameProfile(UUID.nameUUIDFromBytes(
                        "mmcr-ae2-interface-test".getBytes(StandardCharsets.UTF_8)),
                        "mmcr-ae2-interface"),
                ClientInformation.createDefault());
    }

    private static ServerPlayer makePlayerWithConnection(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = new ServerPlayer(server, helper.getLevel(),
                new GameProfile(UUID.nameUUIDFromBytes(
                        "mmcr-ae2-interface-test-connected".getBytes(StandardCharsets.UTF_8)),
                        "mmcr-ae2-interface-connected"),
                ClientInformation.createDefault());
        player.connection = new RecordingConnection(server, player);
        return player;
    }

    private static void reloadBlockEntity(BlockEntity entity, GameTestHelper helper) {
        try {
            Method save = BlockEntity.class.getDeclaredMethod("saveAdditional", CompoundTag.class,
                    HolderLookup.Provider.class);
            save.setAccessible(true);
            Method load = BlockEntity.class.getDeclaredMethod("loadAdditional", CompoundTag.class,
                    HolderLookup.Provider.class);
            load.setAccessible(true);
            CompoundTag tag = new CompoundTag();
            save.invoke(entity, tag, helper.getLevel().registryAccess());
            load.invoke(entity, tag, helper.getLevel().registryAccess());
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unable to reload block entity " + entity, exception);
        }
    }

    /**
     * Minimal packet-recording connection that lets {@code MenuOpener.open} dispatch
     * its {@code ClientboundOpenScreenPacket} without driving a real network client.
     *
     * @author howxu <dev@howxu.cn>
     */
    private static final class RecordingConnection extends ServerGamePacketListenerImpl {
        private final List<Packet<?>> packets = new ArrayList<>();

        private RecordingConnection(MinecraftServer server, ServerPlayer player) {
            super(server, new Connection(PacketFlow.CLIENTBOUND), player,
                    CommonListenerCookie.createInitial(new GameProfile(UUID.nameUUIDFromBytes(
                            "mmcr-ae2-interface-recording".getBytes(StandardCharsets.UTF_8)),
                            "mmcr-ae2-interface-recording"), false));
        }

        @Override
        public void send(Packet<?> packet) {
            packets.add(packet);
        }

        @Override
        public void send(Packet<?> packet, PacketSendListener listener) {
            packets.add(packet);
        }
    }
}
