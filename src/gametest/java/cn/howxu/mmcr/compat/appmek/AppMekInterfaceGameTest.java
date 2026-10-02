package cn.howxu.mmcr.compat.appmek;

import appeng.api.AECapabilities;
import appeng.api.behaviors.GenericInternalInventory;
import appeng.api.config.Actionable;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.AEItemKey;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.helpers.InterfaceLogicHost;
import appeng.api.storage.MEStorage;
import appeng.me.helpers.BaseActionSource;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.compat.mekanism.ChemicalViewFacet;
import cn.howxu.mmcr.publicapi.structure.BlockConditions;
import cn.howxu.mmcr.compat.appmek.loaded.MEChemicalCapability;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.InputInterfaceBlockEntity;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.StockingInterfaceBlockEntity;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.OutputInterfaceBlockEntity;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.AsyncOutputInterfaceBlockEntity;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.PatternInterfaceBlockEntity;
import cn.howxu.mmcr.compat.mekanism.loaded.ChemicalHandlerPort;
import cn.howxu.mmcr.compat.mekanism.MekanismRecipeTypes;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.util.IOType;
import me.ramidzkh.mekae2.ae2.MekanismKey;
import mekanism.api.Action;
import mekanism.common.capabilities.Capabilities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.world.item.Items;
import me.ramidzkh.mekae2.ae2.MekanismKeyType;
import net.minecraft.gametest.framework.GameTestHelper;

import java.util.List;

/**
 * Runtime registration and validation of all existing ME interface variants.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class AppMekInterfaceGameTest {
    public void configuredInputFeedsRecipeFromRealChemicalCell(GameTestHelper helper) {
        var machine = AppMekGameTestFixtures.createChemicalMachine(helper, "appmek_network_recipe", "ae2_me_input_interface", 500L);
        var input = (InputInterfaceBlockEntity) machine.input();
        var network = AppMekGameTestFixtures.createChemicalNetwork(helper, new BlockPos(1, 2, 1));
        MekanismKey oxygen = MekanismKey.of(AppMekGameTestFixtures.chemical("oxygen", 1L));
        helper.startSequence().thenWaitUntil(() -> network.connectWhenReady(helper))
                .thenExecute(() -> {
                    helper.assertTrue(network.storage().insert(oxygen, 1_000L, Actionable.MODULATE, new BaseActionSource()) == 1_000L,
                            "Real AppMek cell accepts recipe chemical supply");
                    input.getInterfaceLogic().getConfig().setStack(0, new GenericStack(oxygen, 500L));
                }).thenWaitUntil(() -> helper.assertTrue(machine.output().nativeItemHandler().getStackInSlot(0).getCount() == 2,
                        "Configured ME input pulls chemicals and processes two recipe batches"))
                .thenExecute(() -> helper.assertTrue(network.storage().extract(oxygen, Long.MAX_VALUE, Actionable.SIMULATE,
                                new BaseActionSource()) == 0L && input.getStorage().getAmount(0) == 0L,
                        "Two completed recipes consume exactly the network chemical supply"))
                .thenSucceed();
    }

    public void stockingTracksChemicalDisconnectAndReconnect(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ModBlocks.BLOCKS.get("ae2_me_stocking_input_interface").get().defaultBlockState());
        StockingInterfaceBlockEntity host = helper.getBlockEntity(pos);
        MekanismKey oxygen = MekanismKey.of(AppMekGameTestFixtures.chemical("oxygen", 1L));
        MekanismKey hydrogen = MekanismKey.of(AppMekGameTestFixtures.chemical("hydrogen", 1L));
        host.getInterfaceLogic().getConfig().setStack(0, new GenericStack(oxygen, 1L));
        var handler = ((ChemicalHandlerPort) host.capability(new CapabilityType(MekanismRecipeTypes.CHEMICAL))).chemicalHandler();
        var network = AppMekGameTestFixtures.createChemicalNetwork(helper, pos);
        helper.startSequence().thenWaitUntil(() -> network.connectWhenReady(helper))
                .thenExecute(() -> {
                    network.storage().insert(oxygen, 1_000L, Actionable.MODULATE, new BaseActionSource());
                    network.storage().insert(hydrogen, 500L, Actionable.MODULATE, new BaseActionSource());
                    helper.assertTrue(handler.extractChemical(0, 400L, Action.EXECUTE).getAmount() == 400L,
                            "Configured Stocking input extracts chemical directly from the cell");
                    network.disconnect();
                }).thenWaitUntil(() -> helper.assertTrue(handler.getChemicalInTank(0).isEmpty()
                                && handler.extractChemical(0, 100L, Action.EXECUTE).isEmpty(),
                        "Disconnected Stocking input cannot consume its display mirror"))
                .thenWaitUntil(() -> network.connectWhenReady(helper))
                .thenExecute(() -> {
                    helper.assertTrue(handler.extractChemical(0, 600L, Action.EXECUTE).getAmount() == 600L,
                            "Reconnected Stocking input sees the remaining chemical supply");
                    helper.assertTrue(network.storage().extract(hydrogen, Long.MAX_VALUE, Actionable.SIMULATE, new BaseActionSource()) == 500L,
                            "Unconfigured hydrogen survives disconnect and reconnect");
                }).thenSucceed();
    }

    public void normalOutputFlushesDisconnectedChemicalCache(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ModBlocks.BLOCKS.get("ae2_me_output_interface").get().defaultBlockState());
        OutputInterfaceBlockEntity host = helper.getBlockEntity(pos);
        MekanismKey oxygen = MekanismKey.of(AppMekGameTestFixtures.chemical("oxygen", 1L));
        var handler = ((ChemicalHandlerPort) host.capability(new CapabilityType(MekanismRecipeTypes.CHEMICAL))).chemicalHandler();
        helper.assertTrue(handler.insertChemical(oxygen.withAmount(2_000L), Action.EXECUTE).isEmpty(),
                "Disconnected normal output retains chemical output in its cache");
        var network = AppMekGameTestFixtures.createChemicalNetwork(helper, pos);
        helper.startSequence().thenWaitUntil(() -> network.connectWhenReady(helper))
                .thenWaitUntil(() -> helper.assertTrue(network.storage().extract(oxygen, Long.MAX_VALUE, Actionable.SIMULATE,
                                new BaseActionSource()) == 2_000L && host.getStorage().isEmpty(),
                        "Restored real network receives all cached output without duplication"))
                .thenSucceed();
    }

    public void asyncOutputConservesRealCellPartialAcceptance(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ModBlocks.BLOCKS.get("ae2_me_async_output_interface").get().defaultBlockState());
        AsyncOutputInterfaceBlockEntity host = helper.getBlockEntity(pos);
        var network = AppMekGameTestFixtures.createChemicalNetwork(helper, pos);
        MekanismKey oxygen = MekanismKey.of(AppMekGameTestFixtures.chemical("oxygen", 1L));
        helper.startSequence().thenWaitUntil(() -> network.connectWhenReady(helper))
                .thenExecute(() -> {
                    long capacity = network.storage().insert(oxygen, Long.MAX_VALUE, Actionable.SIMULATE, new BaseActionSource());
                    helper.assertTrue(capacity > 600L, "Chemical cell has room for a partial acceptance scenario");
                    long before = network.storage().insert(oxygen, capacity - 600L, Actionable.MODULATE, new BaseActionSource());
                    host.getStorage().setCapacity(MekanismKeyType.TYPE, 300L);
                    for (int slot = 1; slot < host.getStorage().size(); slot++) {
                        host.getStorage().setStack(slot, new GenericStack(AEItemKey.of(Items.IRON_INGOT), 64L));
                    }
                    var handler = ((ChemicalHandlerPort) host.capability(new CapabilityType(MekanismRecipeTypes.CHEMICAL))).chemicalHandler();
                    var remainder = handler.insertChemical(oxygen.withAmount(1_300L), Action.EXECUTE);
                    long accepted = network.storage().extract(oxygen, Long.MAX_VALUE, Actionable.SIMULATE, new BaseActionSource()) - before;
                    helper.assertTrue(accepted == 600L && host.getStorage().getAmount(0) == 300L && remainder.getAmount() == 400L,
                            "Real cell, async output cache and returned remainder conserve partial output acceptance");
                }).thenSucceed();
    }

    public void oversizeLastSlotConsumesLongRecipeAmount(GameTestHelper helper) {
        long amount = (long) Integer.MAX_VALUE + 5_000L;
        var machine = AppMekGameTestFixtures.createChemicalMachine(helper, "appmek_oversize_long_recipe", "eae_me_oversize_input_interface", amount - 100L);
        InputInterfaceBlockEntity input = (InputInterfaceBlockEntity) machine.input();
        MekanismKey oxygen = MekanismKey.of(AppMekGameTestFixtures.chemical("oxygen", 1L));
        input.getStorage().setCapacity(MekanismKeyType.TYPE, amount);
        var handler = ((ChemicalHandlerPort) input.capability(new CapabilityType(MekanismRecipeTypes.CHEMICAL))).chemicalHandler();
        helper.assertTrue(handler.insertChemical(35, oxygen.withAmount(amount), Action.EXECUTE).isEmpty(),
                "Test-sized oversize storage accepts the complete long input through the actual chemical handler");
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(machine.output().nativeItemHandler().getStackInSlot(0).is(Items.DIAMOND),
                        "Oversize chemical slot 35 supplies a long-quantity recipe"))
                .thenExecute(() -> helper.assertTrue(input.getStorage().getAmount(35) == 100L,
                        "Long recipe consumption preserves the exact remaining chemical amount"))
                .thenSucceed();
    }

    public void chemicalStorageAndConfigurationSurviveNativeSaveLoad(GameTestHelper helper) {
        MekanismKey oxygen = MekanismKey.of(AppMekGameTestFixtures.chemical("oxygen", 1L));
        List<String> ids = List.of("ae2_me_input_interface", "ae2_me_stocking_input_interface", "ae2_me_output_interface", "ae2_me_pattern_interface");
        for (int index = 0; index < ids.size(); index++) {
            BlockPos sourcePos = new BlockPos(index, 1, 0);
            BlockPos targetPos = new BlockPos(index, 1, 2);
            helper.setBlock(sourcePos, ModBlocks.BLOCKS.get(ids.get(index)).get().defaultBlockState());
            helper.setBlock(targetPos, ModBlocks.BLOCKS.get(ids.get(index)).get().defaultBlockState());
            IOPortBlockEntity source = helper.getBlockEntity(sourcePos);
            IOPortBlockEntity target = helper.getBlockEntity(targetPos);
            if (source instanceof PatternInterfaceBlockEntity pattern) {
                pattern.getLogic().getReturnInv().setStack(0, new GenericStack(oxygen, 1_500L));
                pattern.getLogic().getPatternInv().addItems(PatternDetailsHelper.encodeProcessingPattern(
                        List.of(new GenericStack(oxygen, 500L)), List.of(new GenericStack(oxygen, 250L))));
            } else {
                var logic = ((InterfaceLogicHost) source).getInterfaceLogic();
                logic.getStorage().setStack(0, new GenericStack(oxygen, 1_500L));
                if (source instanceof InputInterfaceBlockEntity || source instanceof StockingInterfaceBlockEntity) {
                    logic.getConfig().setStack(0, new GenericStack(oxygen, 500L));
                }
                if (source instanceof InputInterfaceBlockEntity input) input.recordNetworkPull(0, oxygen, 1_000L);
            }
            target.loadWithComponents(source.saveWithoutMetadata(helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
            var handler = ((ChemicalHandlerPort) target.capability(new CapabilityType(MekanismRecipeTypes.CHEMICAL))).chemicalHandler();
            if (target instanceof StockingInterfaceBlockEntity stocking) {
                helper.assertTrue(stocking.configuredKeys().contains(oxygen) && handler.extractChemical(0, 100L, Action.EXECUTE).isEmpty(),
                        "Restored Stocking configuration does not turn network display amounts into stored chemicals");
            } else {
                helper.assertTrue(handler.getChemicalInTank(0).getAmount() == 1_500L, ids.get(index) + " preserves chemical key and amount in native NBT");
            }
            if (target instanceof InputInterfaceBlockEntity input) helper.assertTrue(input.networkOwnedAmount(0, oxygen) == 1_000L,
                    "Chemical network-owned attribution survives save/load");
            if (target instanceof PatternInterfaceBlockEntity pattern) helper.assertTrue(!pattern.getLogic().getPatternInv().getStackInSlot(0).isEmpty(),
                    "Chemical processing pattern survives native save/load");
        }
        helper.succeed();
    }

    public void chemicalMemoryCardConfigurationPullsFromNetwork(GameTestHelper helper) {
        BlockPos sourcePos = new BlockPos(0, 1, 1);
        BlockPos targetPos = new BlockPos(2, 1, 1);
        helper.setBlock(sourcePos, ModBlocks.BLOCKS.get("ae2_me_input_interface").get().defaultBlockState());
        helper.setBlock(targetPos, ModBlocks.BLOCKS.get("ae2_me_input_interface").get().defaultBlockState());
        InputInterfaceBlockEntity source = helper.getBlockEntity(sourcePos);
        InputInterfaceBlockEntity target = helper.getBlockEntity(targetPos);
        MekanismKey oxygen = MekanismKey.of(AppMekGameTestFixtures.chemical("oxygen", 1L));
        source.getInterfaceLogic().getConfig().setStack(0, new GenericStack(oxygen, 500L));
        DataComponentMap.Builder settings = DataComponentMap.builder();
        source.exportMemoryCardSettings(settings, null);
        target.importMemoryCardSettings(settings.build(), null);
        helper.assertTrue(oxygen.equals(target.getInterfaceLogic().getConfig().getKey(0)), "Memory card preserves chemical configuration key");
        var network = AppMekGameTestFixtures.createChemicalNetwork(helper, targetPos);
        helper.startSequence().thenWaitUntil(() -> network.connectWhenReady(helper))
                .thenExecute(() -> network.storage().insert(oxygen, 1_000L, Actionable.MODULATE, new BaseActionSource()))
                .thenWaitUntil(() -> helper.assertTrue(target.getStorage().getAmount(0) == 500L,
                        "Restored memory card chemical configuration pulls actual network supply"))
                .thenExecute(() -> helper.assertTrue(source.getStorage().isEmpty(), "Memory card transfers configuration without copying materials"))
                .thenSucceed();
    }

    public void chemicalKindChangesWakeBlockedController(GameTestHelper helper) {
        var machine = AppMekGameTestFixtures.createChemicalMachine(helper, "appmek_chemical_wakeup", "ae2_me_input_interface", 500L);
        InputInterfaceBlockEntity input = (InputInterfaceBlockEntity) machine.input();
        MekanismKey oxygen = MekanismKey.of(AppMekGameTestFixtures.chemical("oxygen", 1L));
        MekanismKey hydrogen = MekanismKey.of(AppMekGameTestFixtures.chemical("hydrogen", 1L));
        input.getStorage().setStack(0, new GenericStack(oxygen, 100L));
        input.getStorage().setStack(1, new GenericStack(hydrogen, 1_000L));
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(machine.controller().structureSnapshot().formed()
                        && machine.controller().runtimeSnapshot().crafting().failure() != null,
                "Controller waits for missing oxygen"))
                .thenExecute(() -> {
                    input.getStorage().beginBatch();
                    try {
                        input.getStorage().setStack(0, new GenericStack(oxygen, 700L));
                        input.getStorage().setStack(1, new GenericStack(hydrogen, 400L));
                    } finally {
                        input.getStorage().endBatch();
                    }
                }).thenWaitUntil(() -> helper.assertTrue(machine.output().nativeItemHandler().getStackInSlot(0).is(Items.DIAMOND),
                        "Increasing one chemical kind wakes the recipe even when total chemical amount is unchanged"))
                .thenExecute(() -> helper.assertTrue(input.getStorage().getAmount(0) == 200L && input.getStorage().getAmount(1) == 400L,
                        "Woken recipe consumes only its oxygen input"))
                .thenSucceed();
    }

    public void chemicalPredicatesIncludeMePortsWithoutRadioactivity(GameTestHelper helper) {
        var inputs = BlockConditions.chemicalInput().alternatives();
        var outputs = BlockConditions.chemicalOutput().alternatives();
        var radioactive = BlockConditions.radioactiveChemicalPorts().alternatives();
        for (String id : PORTS) {
            var block = ModBlocks.BLOCKS.get(id).get();
            if (!id.contains("output")) helper.assertTrue(inputs.stream()
                    .anyMatch(condition -> condition.blockSupplier().map(supplier -> supplier.get() == block).orElse(false)),
                    id + " matches the public ordinary chemical input predicate");
            if (id.contains("output") || id.contains("pattern")) helper.assertTrue(outputs.stream()
                    .anyMatch(condition -> condition.blockSupplier().map(supplier -> supplier.get() == block).orElse(false)),
                    id + " matches the public ordinary chemical output predicate");
            helper.assertTrue(radioactive.stream().noneMatch(condition -> condition.blockSupplier()
                    .map(supplier -> supplier.get() == block).orElse(false)), id + " does not match radioactive chemical ports");
        }
        helper.succeed();
    }

    public static final List<String> PORTS = List.of(
            "ae2_me_input_interface", "ae2_me_stocking_input_interface", "ae2_me_output_interface",
            "ae2_me_async_output_interface", "ae2_me_pattern_interface", "eae_me_extended_input_interface",
            "eae_me_extended_output_interface", "eae_me_extended_stocking_input_interface", "eae_me_extended_pattern_interface",
            "eae_me_oversize_input_interface", "eae_me_oversize_output_interface", "eae_me_oversize_stocking_input_interface");

    public void allMeKindsExposeChemicalCapabilities(GameTestHelper helper) {
        CapabilityType type = new CapabilityType(MekanismRecipeTypes.CHEMICAL);
        for (int index = 0; index < PORTS.size(); index++) {
            String id = PORTS.get(index);
            BlockPos pos = new BlockPos(index % 4, 1, index / 4);
            helper.setBlock(pos, ModBlocks.BLOCKS.get(id).get().defaultBlockState());
            IOPortBlockEntity host = helper.getBlockEntity(pos);
            helper.assertTrue(host.capability(type) instanceof MEChemicalCapability, id + " exposes ME chemical capability");
            helper.assertTrue(host.capability(type).facet(ChemicalViewFacet.class).isPresent(), id + " provides chemical queries");
            IOType direction = id.contains("output") || id.contains("pattern") ? IOType.OUTPUT : IOType.INPUT;
            helper.assertTrue(host.capability(type).directions().supports(direction), id + " has correct chemical direction");
            helper.assertTrue(helper.getLevel().getCapability(AECapabilities.IN_WORLD_GRID_NODE_HOST,
                    helper.absolutePos(pos), null) != null, id + " exposes its AE2 grid host");
        }
        helper.succeed();
    }

    public void genericCapabilitiesRejectRadioactiveChemicals(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ModBlocks.BLOCKS.get("ae2_me_input_interface").get().defaultBlockState());
        BlockPos world = helper.absolutePos(pos);
        var chemicals = helper.getLevel().getCapability(Capabilities.CHEMICAL.block(), world, null);
        helper.assertTrue(chemicals != null, "AppMek adapts the MMCR generic inventory capability");
        var waste = AppMekGameTestFixtures.chemical("nuclear_waste", 100L);
        helper.assertTrue(chemicals.insertChemical(0, waste, Action.SIMULATE).getAmount() == 100L,
                "External chemical simulation rejects radioactive material");
        helper.assertTrue(chemicals.insertChemical(0, waste, Action.EXECUTE).getAmount() == 100L,
                "External chemical execution rejects radioactive material");
        MEStorage storage = helper.getLevel().getCapability(AECapabilities.ME_STORAGE, world, null);
        helper.assertTrue(storage.insert(MekanismKey.of(waste), 100L, Actionable.MODULATE, new BaseActionSource()) == 0L,
                "ME storage access cannot bypass chemical attribute validation");
        GenericInternalInventory generic = helper.getLevel().getCapability(AECapabilities.GENERIC_INTERNAL_INV, world, null);
        helper.assertTrue(generic.insert(0, MekanismKey.of(waste), 100L, Actionable.MODULATE) == 0L,
                "Generic inventory access cannot bypass chemical attribute validation");
        InputInterfaceBlockEntity host = helper.getBlockEntity(pos);
        helper.assertTrue(host.getStorage().isEmpty(), "Rejected external chemicals do not enter the input cache");
        helper.assertTrue(chemicals.insertChemical(0, AppMekGameTestFixtures.chemical("oxygen", 500L), Action.EXECUTE).isEmpty(),
                "Ordinary chemical input remains usable");
        helper.succeed();
    }
}
