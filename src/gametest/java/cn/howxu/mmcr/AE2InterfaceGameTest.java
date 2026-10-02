package cn.howxu.mmcr;

import appeng.api.AECapabilities;
import appeng.api.behaviors.GenericInternalInventory;
import appeng.api.config.Actionable;
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
            helper.assertTrue(InterfaceMenu.TYPE != null,
                    "AE2 InterfaceMenu.TYPE is registered");
            InterfaceMenu interfaceMenu = new InterfaceMenu(InterfaceMenu.TYPE, 0,
                    player.getInventory(), entity);
            helper.assertTrue(interfaceMenu instanceof InterfaceMenu,
                    "AE2 InterfaceMenu is constructed via the AE2 factory bound to InterfaceMenu.TYPE");

            ServerPlayer connectedPlayer = makePlayerWithConnection(helper);
            helper.assertTrue(bridge.openMenu(connectedPlayer, helper.getLevel(), portWorldPos),
                    "AE2 bridge forwards the AE2 input interface to MenuOpener.open");
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
