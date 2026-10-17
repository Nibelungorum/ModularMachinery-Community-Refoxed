package cn.howxu.mmcr;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.helpers.externalstorage.GenericStackInv;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.machine.PortRequirementSpec;
import cn.howxu.mmcr.api.recipe.MachineIngredient;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.OutputRegistry;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.compat.ae2lt.LightningOutput;
import cn.howxu.mmcr.compat.ae2lt.LightningRequirement;
import cn.howxu.mmcr.compat.ae2lt.loaded.LoadedLightningBridge;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.PatternInterfaceBlockEntity;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.kind.PatternInterfaceKind;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.adapter.AE2NativeAdapters;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static cn.howxu.mmcr.api.compat.ae2lt.LightningTier.HIGH_VOLTAGE;
import static cn.howxu.mmcr.api.compat.ae2lt.LightningTier.EXTREME_HIGH_VOLTAGE;

/** Mixed native patterns exercise deferred admission and shared material ownership.
 * @author howxu <dev@howxu.cn> */
public final class LightningPatternGameTest {
    public void mixedPatternAdmissionAndPersistence(GameTestHelper helper) { mixedPattern(helper, false); }
    public void mixedBatchPreservesOneMaterialOwner(GameTestHelper helper) { mixedPattern(helper, true); }

    private void mixedPattern(GameTestHelper helper, boolean factory) {
        var machineId = MMCR.id(factory ? "lightning_factory_pattern_test" : "lightning_pattern_test");
        var recipeId = MMCR.id(machineId.getPath() + "_recipe");
        BlockPos controllerPos = new BlockPos(1, 1, 1);
        BlockPos portPos = controllerPos.above();
        var portBlock = ModBlocks.BLOCKS.get("ae2_me_pattern_interface").get();
        helper.setBlock(portPos, portBlock.defaultBlockState());
        helper.setBlock(controllerPos, ModBlocks.controllerFor(MMCR.id("test_cube")).get().defaultBlockState()
                .setValue(MachineControllerBlock.FACING, Direction.SOUTH));
        Map<BlockPos, BlockPredicate> blocks = new LinkedHashMap<>();
        blocks.put(new BlockPos(0, 1, 0), new BlockPredicate.OfBlock(portBlock));
        if (factory) for (int index = 1; index <= 2; index++) {
            var factoryBlock = ModBlocks.BLOCKS.get("factory_controller").get();
            helper.setBlock(controllerPos.offset(index, 0, 0), factoryBlock.defaultBlockState());
            blocks.put(new BlockPos(index, 0, 0), new BlockPredicate.OfBlock(factoryBlock));
        }
        var machine = factory ? new DynamicMachine(machineId, "Lightning factory pattern test", new BlockArray(blocks),
                MachineControllerSpec.defaultsFor(machineId), PortRequirementSpec.none(), List.of(), Map.of(), 1, false, true, 2)
                : new DynamicMachine(machineId, "Lightning pattern test", new BlockArray(blocks));
        if (!MachineRegistry.containsStatic(machineId)) MachineRegistry.register(machine);
        List<MachineOutput> outputs = List.of(new MachineOutput.ItemOutput(new ItemStack(Items.GOLD_INGOT), 1F),
                new LightningOutput(HIGH_VOLTAGE, 4L, 1F), new LightningOutput(EXTREME_HIGH_VOLTAGE, 5L, 1F));
        List<MachineRequirement> requirements = new ArrayList<>(List.of(
                MachineRequirement.fromInput(new MachineIngredient.ItemIngredient(Ingredient.of(Items.IRON_INGOT), 1)),
                MachineRequirement.fromInput(new MachineIngredient.FluidIngredient(FluidIngredient.of(Fluids.WATER), 250)),
                LightningRequirement.input(HIGH_VOLTAGE, 2L), LightningRequirement.input(EXTREME_HIGH_VOLTAGE, 3L)));
        outputs.forEach(output -> requirements.add(OutputRegistry.toRequirement(output, List.of())));
        RecipeRegistry.registerStatic(MachineRecipe.fromCanonical(recipeId, machineId, 2, requirements, outputs,
                List.of(), 0, factory ? 2 : 1, false, false, false, Set.of()));
        PatternInterfaceBlockEntity host = helper.getBlockEntity(portPos);
        MachineControllerBlockEntity controller = helper.getBlockEntity(controllerPos);
        controller.setMachine(machine);
        controller.setStructureCheckIntervalForTesting(1);
        controller.requestImmediateStructureCheck();
        var iron = AEItemKey.of(Items.IRON_INGOT);
        var water = AEFluidKey.of(Fluids.WATER);
        var high = LoadedLightningBridge.key(HIGH_VOLTAGE);
        var extreme = LoadedLightningBridge.key(EXTREME_HIGH_VOLTAGE);
        long batches = factory ? 2L : 1L;
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(controller.structureSnapshot().formed(), "Mixed lightning pattern controller forms"))
                .thenExecute(() -> {
                    host.getLogic().getPatternInv().addItems(PatternDetailsHelper.encodeProcessingPattern(
                            List.of(new GenericStack(iron, 3L), new GenericStack(water, 1_000L),
                                    new GenericStack(high, 5L), new GenericStack(extreme, 7L)),
                            List.of(new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 1L),
                                    new GenericStack(high, 4L), new GenericStack(extreme, 5L))));
                    var pattern = host.getLogic().getAvailablePatterns().getFirst();
                    for (int copy = 0; copy < batches; copy++) {
                        KeyCounter[] holders = {new KeyCounter(), new KeyCounter(), new KeyCounter(), new KeyCounter()};
                        holders[0].add(iron, 3L);
                        holders[1].add(water, 1_000L);
                        holders[2].add(high, 5L);
                        holders[3].add(extreme, 7L);
                        helper.assertTrue(host.craftingMachine().pushPattern(pattern, holders, Direction.NORTH),
                                "Standard mixed pattern accepts one complete request with both lightning tiers");
                        for (KeyCounter holder : holders) helper.assertTrue(holder.isEmpty(),
                                "Successful admission claims each original holder exactly once");
                    }
                    helper.assertTrue(host.pendingPatterns().totalCopies() == batches
                                    && controller.runtimeSnapshot().crafting().recipeId() == null
                                    && controller.runtimeSnapshot().factory().activeLaneCount() == 0
                                    && host.getLogic().getReturnInv().isEmpty(),
                            "Admission privately owns every family without starting or returning materials: "
                                    + mixedState(host, controller));
                    long now = helper.getLevel().getGameTime();
                    // Each dispatch starts at most one lane for this nonparallel recipe's compressed group.
                    for (long started = 1L; started <= batches; started++) {
                        host.pendingScheduler().tick(now + started);
                        helper.assertTrue(host.pendingPatterns().totalCopies() == batches - started
                                        && (factory ? controller.runtimeSnapshot().factory().activeLaneCount() == started
                                        : recipeId.equals(controller.runtimeSnapshot().crafting().recipeId()))
                                        && amount(host, iron) == 2L * started && amount(host, water) == 750L * started
                                        && amount(host, high) == 3L * started && amount(host, extreme) == 4L * started,
                                "Each explicit dispatch starts one request and preserves the unstarted copies; started="
                                        + started + ", " + mixedState(host, controller));
                    }
                    helper.assertTrue(host.pendingPatterns().isEmpty()
                                    && amount(host, iron) == 2L * batches && amount(host, water) == 750L * batches
                                    && amount(host, high) == 3L * batches && amount(host, extreme) == 4L * batches,
                            "Explicit scheduling consumes each request and returns only its exact excess once: "
                                    + mixedState(host, controller));
                    if (!factory) {
                        CompoundTag paid = controller.saveWithoutMetadata(helper.getLevel().registryAccess());
                        controller.loadWithComponents(paid, helper.getLevel().registryAccess());
                        controller.requestImmediateStructureCheck();
                    }
                }).thenWaitUntil(() -> {
                    helper.assertTrue(amount(host, AEItemKey.of(Items.GOLD_INGOT)) == batches && amount(host, iron) == 2L * batches
                                    && amount(host, water) == 750L * batches && amount(host, high) == 7L * batches && amount(host, extreme) == 9L * batches,
                            "Products plus exact leftovers settle once for every lane and family, including a paid controller reload: "
                                    + mixedState(host, controller));
                }).thenExecute(() -> {
                    CompoundTag saved = host.saveWithoutMetadata(helper.getLevel().registryAccess());
                    host.getLogic().getReturnInv().clear();
                    host.loadWithComponents(saved, helper.getLevel().registryAccess());
                    helper.assertTrue(amount(host, high) == 7L * batches && amount(host, extreme) == 9L * batches,
                            "Native return inventory NBT preserves both tier balances");
                    host.craftingMachine().flushPendingReturns();
                    helper.assertTrue(amount(host, high) == 7L * batches, "Repeating return settlement cannot duplicate paid lightning");
                }).thenSucceed();
    }

    public void mixedLaneLongPersistenceAndNativeReturn(GameTestHelper helper) {
        helper.setBlock(BlockPos.ZERO, ModBlocks.BLOCKS.get("ae2_me_pattern_interface").get().defaultBlockState());
        PatternInterfaceBlockEntity host = helper.getBlockEntity(BlockPos.ZERO);
        var iron = AEItemKey.of(Items.IRON_INGOT);
        var water = AEFluidKey.of(Fluids.WATER);
        var high = LoadedLightningBridge.key(HIGH_VOLTAGE);
        var extreme = LoadedLightningBridge.key(EXTREME_HIGH_VOLTAGE);
        KeyCounter holder = new KeyCounter();
        holder.add(iron, 12L);
        holder.add(water, 4_000L);
        holder.add(high, 8_589_934_588L);
        holder.add(extreme, 22L);
        GenericStackInv owner = AE2NativeAdapters.requestInventory(new KeyCounter[]{holder});
        GenericStackInv[] slices = {AE2NativeAdapters.requestInventoryView(owner, 1L, 2L),
                AE2NativeAdapters.requestInventoryView(owner, 1L, 2L)};
        for (var slice : slices) {
            consume(helper, slice, iron, 1L);
            consume(helper, slice, water, 500L);
            consume(helper, slice, high, 2_147_483_647L);
            consume(helper, slice, extreme, 3L);
        }
        helper.assertTrue(holder.get(high) == 8_589_934_588L && holder.get(extreme) == 22L,
                "Native scratch never mutates the provider counters before ownership handoff");
        holder.clear();
        CompoundTag pending = new CompoundTag();
        owner.writeToChildTag(pending, "pattern_pending_returns", helper.getLevel().registryAccess());
        host.craftingMachine().loadPendingReturns(pending, helper.getLevel().registryAccess());
        host.craftingMachine().flushPendingReturns();
        CompoundTag saved = host.saveWithoutMetadata(helper.getLevel().registryAccess());
        var restored = PatternInterfaceKind.INSTANCE.entityFactory().create(helper.absolutePos(new BlockPos(4, 0, 0)),
                host.getBlockState());
        restored.setLevel(helper.getLevel());
        restored.loadWithComponents(saved, helper.getLevel().registryAccess());
        KeyCounter received = new KeyCounter();
        MEStorage receiver = new MEStorage() {
            @Override
            public long insert(AEKey key, long count, Actionable mode, IActionSource source) {
                if (mode == Actionable.MODULATE) received.add(key, count);
                return count;
            }

            @Override
            public Component getDescription() { return Component.empty(); }
        };
        restored.getLogic().getReturnInv().injectIntoNetwork(receiver, IActionSource.empty(), stack -> {});
        helper.assertTrue(restored.getLogic().getReturnInv().isEmpty()
                        && received.get(iron) == 10L && received.get(water) == 3_000L
                        && received.get(high) == 4_294_967_294L && received.get(extreme) == 16L,
                "Reloaded native returns preserve every exact long tier balance and ordinary family");
        restored.craftingMachine().flushPendingReturns();
        restored.getLogic().getReturnInv().injectIntoNetwork(receiver, IActionSource.empty(), stack -> {});
        helper.assertTrue(received.get(high) == 4_294_967_294L && received.get(extreme) == 16L,
                "Repeated legacy settlement and injection cannot duplicate paid lightning");
        restored.onChunkUnloaded();
        helper.succeed();
    }

    private static void consume(GameTestHelper helper, GenericStackInv inventory, AEKey key, long count) {
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (key.equals(inventory.getKey(slot))) {
                helper.assertTrue(inventory.extract(slot, key, count, Actionable.MODULATE) == count,
                        "Native lane consumes the exact amount from its sole shared owner");
                return;
            }
        }
        helper.fail("Requested resource has no native family slot");
    }

    private static String mixedState(PatternInterfaceBlockEntity host, MachineControllerBlockEntity controller) {
        return "queueCopies=" + host.pendingPatterns().totalCopies()
                + ", groups=" + host.pendingPatterns().groups().size()
                + ", activeLanes=" + controller.runtimeSnapshot().factory().activeLaneCount()
                + ", activeRecipe=" + controller.runtimeSnapshot().crafting().recipeId()
                + ", iron=" + amount(host, AEItemKey.of(Items.IRON_INGOT))
                + ", water=" + amount(host, AEFluidKey.of(Fluids.WATER))
                + ", high=" + amount(host, LoadedLightningBridge.key(HIGH_VOLTAGE))
                + ", extreme=" + amount(host, LoadedLightningBridge.key(EXTREME_HIGH_VOLTAGE))
                + ", gold=" + amount(host, AEItemKey.of(Items.GOLD_INGOT));
    }

    private static long amount(PatternInterfaceBlockEntity host, AEKey key) {
        long amount = 0L;
        var inventory = host.getLogic().getReturnInv();
        for (int slot = 0; slot < inventory.size(); slot++) if (key.equals(inventory.getKey(slot))) amount += inventory.getAmount(slot);
        return amount;
    }
}
