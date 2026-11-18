package cn.howxu.mmcr;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.externalstorage.GenericStackInv;
import cn.howxu.mmcr.api.capability.storage.ResourceStorage;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.ActiveMachineRecipe;
import cn.howxu.mmcr.internal.sync.MachineRecipeSyncCodec;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import net.minecraft.network.RegistryFriendlyByteBuf;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.compat.ae2lt.loaded.LoadedLightningBridge;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.adapter.AE2ResourceFamilies;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.kind.PatternInterfaceKind;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.storage.PatternRequestState;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.PatternInterfaceBlockEntity;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static cn.howxu.mmcr.api.compat.ae2lt.LightningTier.EXTREME_HIGH_VOLTAGE;
import static cn.howxu.mmcr.api.compat.ae2lt.LightningTier.HIGH_VOLTAGE;

/** @author howxu <dev@howxu.cn> */
public class LightningPatternGameTest {
    /** Samples exported from this admission test on b78869fe, using real AE2 registries. */
    private static String legacyFixture(String file) {
        try (var stream = LightningPatternGameTest.class.getResourceAsStream("/legacy/b78869fe/" + file)) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8).strip();
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }

    public void nativePatternAdmissionAndPersistence(GameTestHelper helper) {
        var family = LoadedLightningBridge.FAMILY;
        var machineId = MMCR.id("lightning_pattern_admission_test");
        var recipeId = MMCR.id("lightning_pattern_admission_recipe");
        BlockPos controllerPos = new BlockPos(1, 1, 0);
        BlockPos portPos = new BlockPos(1, 2, 0);
        var portBlock = ModBlocks.BLOCKS.get("ae2_me_pattern_interface").get();
        helper.setBlock(portPos, portBlock.defaultBlockState());
        helper.setBlock(controllerPos, ModBlocks.controllerFor(MMCR.id("test_cube")).get().defaultBlockState()
                .setValue(MachineControllerBlock.FACING, Direction.SOUTH));
        var host = helper.getBlockEntity(portPos, PatternInterfaceBlockEntity.class);
        var controller = helper.getBlockEntity(controllerPos, MachineControllerBlockEntity.class);
        var machine = new DynamicMachine(machineId, "Lightning Pattern Test", new BlockArray(Map.of(
                new BlockPos(0, 1, 0), new BlockPredicate.OfBlock(portBlock))));
        if (!MachineRegistry.containsStatic(machineId)) MachineRegistry.register(machine);
        var registries = helper.getLevel().registryAccess();
        var ops = registries.createSerializationContext(JsonOps.INSTANCE);
        var recipe = MachineRecipe.CODEC.codec().parse(ops, JsonParser.parseString(legacyFixture("lightning.json"))).getOrThrow();
        var buf = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(HexFormat.of().parseHex(legacyFixture("lightning.hex"))), registries);
        try {
            helper.assertTrue(ActiveMachineRecipe.sameDefinition(recipe, MachineRecipeSyncCodec.decode(buf), registries)
                    && !buf.isReadable(), "The old recipe packet and datapack still describe the same recipe");
        } finally {
            buf.release();
        }
        RecipeRegistry.registerStatic(recipe);
        controller.setMachine(machine);
        controller.requestImmediateStructureCheck();
        var iron = AEItemKey.of(Items.IRON_INGOT);
        var water = AEFluidKey.of(Fluids.WATER);
        var high = family.adapter().toKey(HIGH_VOLTAGE);
        var extreme = family.adapter().toKey(EXTREME_HIGH_VOLTAGE);
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(controller.structureSnapshot().formed(),
                        "Controller forms with the real pattern interface"))
                .thenExecute(() -> {
                    var encoded = PatternDetailsHelper.encodeProcessingPattern(
                            List.of(new GenericStack(iron, 3L), new GenericStack(water, 1_000L),
                                    new GenericStack(high, 5L), new GenericStack(extreme, 7L)),
                            List.of(new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 1L),
                                    new GenericStack(high, 4L), new GenericStack(extreme, 5L)));
                    var previous = ItemStack.CODEC.parse(ops, JsonParser.parseString(legacyFixture("pattern.json"))).getOrThrow();
                    helper.assertTrue(ItemStack.isSameItemSameComponents(previous, encoded), "Processing pattern retains both native tier keys and quantities");
                    host.getLogic().getPatternInv().addItems(previous);
                    var pattern = host.getLogic().getAvailablePatterns().getFirst();
                    KeyCounter[] holders = {new KeyCounter(), new KeyCounter(), new KeyCounter(), new KeyCounter()};
                    holders[0].add(iron, 3L);
                    holders[1].add(water, 1_000L);
                    holders[2].add(high, 5L);
                    holders[3].add(extreme, 7L);
                    helper.assertTrue(host.craftingMachine().pushPattern(pattern, holders, null),
                            "A real decoded mixed pattern reaches controller admission");
                    helper.assertTrue(recipeId.equals(controller.runtimeSnapshot().crafting().recipeId())
                                    && controller.runtimeSnapshot().crafting().parallelism() == 1L
                                    && host.pendingPatterns().isEmpty(),
                            "Mixed admission immediately pays one complete recipe on one thread, without precollection");
                    var returns = host.getLogic().getReturnInv().getAvailableStacks();
                    helper.assertTrue(holders[0].get(iron) == 0L && holders[1].get(water) == 0L
                                    && holders[2].get(high) == 0L && holders[3].get(extreme) == 0L
                                    && host.pendingPatterns().isEmpty(),
                            "The original AE2 holders are accepted once");
                    helper.assertTrue(returns.get(iron) == 2L && returns.get(water) == 750L
                                    && returns.get(high) == 3L && returns.get(extreme) == 4L
                                    && returns.get(AEItemKey.of(Items.GOLD_INGOT)) == 0L,
                            "Each family returns exactly its unconsumed amount, without finishing on the push stack");
                    KeyCounter[] next = {new KeyCounter(), new KeyCounter(), new KeyCounter(), new KeyCounter()};
                    next[0].add(iron, 3L);
                    next[1].add(water, 1_000L);
                    next[2].add(high, 5L);
                    next[3].add(extreme, 7L);
                    helper.assertTrue(!host.craftingMachine().pushPattern(pattern, next, null)
                                    && next[0].get(iron) == 3L && next[1].get(water) == 1_000L
                                    && next[2].get(high) == 5L && next[3].get(extreme) == 7L
                                    && host.pendingPatterns().isEmpty(),
                            "An active thread rejects the next mixed request without taking any resource family");
                    var afterRejection = host.getLogic().getReturnInv().getAvailableStacks();
                    helper.assertTrue(afterRejection.get(iron) == 2L && afterRejection.get(water) == 750L
                                    && afterRejection.get(high) == 3L && afterRejection.get(extreme) == 4L,
                            "Busy rejection leaves all native return balances unchanged");
                    var restored = reload(helper, host);
                    var savedReturns = restored.getLogic().getReturnInv().getAvailableStacks();
                    helper.assertTrue(savedReturns.get(high) == 3L && savedReturns.get(extreme) == 4L
                                    && savedReturns.get(iron) == 2L && savedReturns.get(water) == 750L,
                            "Native pattern NBT preserves mixed leftovers");
                    restored.onChunkUnloaded();
                }).thenSucceed();
    }

    public void mixedLaneRollbackLongPersistenceAndNativeReturn(GameTestHelper helper) {
        var family = LoadedLightningBridge.FAMILY;
        helper.setBlock(BlockPos.ZERO, ModBlocks.BLOCKS.get("ae2_me_pattern_interface").get().defaultBlockState());
        var host = helper.getBlockEntity(BlockPos.ZERO, PatternInterfaceBlockEntity.class);
        var iron = AEItemKey.of(Items.IRON_INGOT);
        var water = AEFluidKey.of(Fluids.WATER);
        var high = family.adapter().toKey(HIGH_VOLTAGE);
        var extreme = family.adapter().toKey(EXTREME_HIGH_VOLTAGE);
        var holders = new KeyCounter();
        holders.add(iron, 12L);
        holders.add(water, 4_000L);
        holders.add(high, 8_589_934_588L);
        holders.add(extreme, 22L);
        var original = new PatternRequestState(new KeyCounter[]{holders}, 1);
        for (boolean commit : new boolean[]{false, true}) {
            try (var transaction = Transaction.openRoot()) {
                for (int lane = 0; lane < 2; lane++) {
                    var slice = new KeyCounter();
                    for (var entry : holders) slice.add(entry.getKey(), entry.getLongValue() / 2L);
                    var state = new PatternRequestState(new KeyCounter[]{slice}, AE2ResourceFamilies.all().size());
                    var items = AE2ResourceFamilies.ITEM.patternRequestView(state);
                    var fluids = AE2ResourceFamilies.FLUID.patternRequestView(state);
                    var lightning = family.patternRequestView(state);
                    extract(helper, items, ItemResource.of(Items.IRON_INGOT), 1L, transaction);
                    extract(helper, fluids, FluidResource.of(Fluids.WATER), 500L, transaction);
                    extract(helper, lightning, HIGH_VOLTAGE, 2_147_483_647L, transaction);
                    extract(helper, lightning, EXTREME_HIGH_VOLTAGE, 3L, transaction);
                    helper.assertTrue(items.accept(host.itemReturnStorage(), transaction)
                                    && fluids.accept(host.fluidReturnStorage(), transaction)
                                    && lightning.accept(host.returnStorage(family), transaction),
                            "All per-lane views return their own leftovers transactionally");
                }
                original.acceptAll(transaction);
                if (commit) transaction.commit();
            }
            if (!commit) {
                helper.assertTrue(holders.get(high) == 8_589_934_588L && holders.get(extreme) == 22L
                                && host.getLogic().getReturnInv().isEmpty(),
                        "Root rollback restores original holders and every lane return");
            }
        }
        helper.assertTrue(holders.get(high) == 0L && holders.get(extreme) == 0L, "Successful batch accepts original holders only once");
        var restored = reload(helper, host);
        helper.assertTrue(restored.returnStorage(family) == restored.returnStorage(family), "Return view is cached");
        var inventory = restored.getLogic().getReturnInv();
        var received = new GenericStackInv(Set.of(AEKeyType.items(), AEKeyType.fluids(), family.adapter().keyType()),
                null, GenericStackInv.Mode.STORAGE, 4);
        inventory.injectIntoNetwork(received, IActionSource.empty(), stack -> {});
        var balances = received.getAvailableStacks();
        helper.assertTrue(inventory.isEmpty() && balances.get(iron) == 10L && balances.get(water) == 3_000L
                        && balances.get(high) == 4_294_967_294L && balances.get(extreme) == 16L,
                "Reloaded native inventory injects exact long amounts, with each tier returned once");
        inventory.injectIntoNetwork(received, IActionSource.empty(), stack -> {});
        helper.assertTrue(received.getAvailableStacks().get(high) == 4_294_967_294L,
                "Repeating native return work cannot return the same lightning twice");
        restored.onChunkUnloaded();
        helper.succeed();
    }

    private static <R> void extract(GameTestHelper helper, ResourceStorage<R> storage, R resource,
                                     long amount, Transaction transaction) {
        for (int slot = 0; slot < storage.size(); slot++) {
            if (resource.equals(storage.resource(slot))) {
                helper.assertTrue(storage.extract(slot, resource, amount, transaction) == amount, "Requested resource is consumed exactly");
                return;
            }
        }
        helper.fail("Requested resource has no family slot");
    }

    private static PatternInterfaceBlockEntity reload(GameTestHelper helper, PatternInterfaceBlockEntity host) {
        var saved = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
        host.saveAdditional(saved);
        host.onChunkUnloaded();
        var restored = PatternInterfaceKind.INSTANCE.entityFactory().create(helper.absolutePos(new BlockPos(4, 0, 0)), host.getBlockState());
        restored.setLevel(helper.getLevel());
        restored.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), saved.buildResult()));
        return restored;
    }
}
