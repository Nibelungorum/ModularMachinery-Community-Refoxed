package cn.howxu.mmcr;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.CraftingBlockEntity;
import appeng.core.definitions.AEBlockEntities;
import appeng.core.definitions.AEBlocks;
import appeng.crafting.CraftingLink;
import appeng.crafting.CraftingPlan;
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.crafting.execution.ExecutingCraftingJob;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.crafting.pattern.AEPatternDecoder;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.helpers.MachineSource;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.pattern.PatternBatchDetails;
import cn.howxu.mmcr.internal.item.PatternBatchData;
import cn.howxu.mmcr.registry.ModDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/** Actual AE definition codec, long input extraction and native CPU/job persistence.
 * @author howxu <dev@howxu.cn>
 */
public final class PatternBatchCodecGameTest {
    private static final ResourceLocation POOL = MMCR.id("pattern_batch_codec_test");
    private static final ResourceLocation RECIPE = MMCR.id("pattern_batch_codec_recipe");

    private PatternBatchCodecGameTest() { }

    public static void batchDefinitionRoundTripsDistinctMultipliers(GameTestHelper helper) {
        IPatternDetails original = original(helper);
        var unit = new PatternBatchDetails(original, data(1));
        var full = restore(helper, unit.scaled(256));
        var tail = restore(helper, unit.scaled(16));
        helper.assertTrue(full.equals(unit.scaled(256)) && full.hashCode() == unit.scaled(256).hashCode()
                        && !full.equals(tail) && full.unitPattern().equals(unit) && tail.unitPattern().equals(unit),
                "Native definition serialization preserves distinct full/tail identities and the exact provider unit");
        helper.assertTrue(original.getDefinition().get(ModDataComponents.PATTERN_BATCH.get()) == null
                        && full.original().getDefinition().equals(original.getDefinition())
                        && AEPatternDecoder.INSTANCE.decodePattern(original.getDefinition(), helper.getLevel()).equals(original),
                "Virtual definitions do not modify the physical pattern or its unrelated components");
        extractAndReinject(helper, original, full, 256);
        extractAndReinject(helper, original, tail, 16);
        extractAndReinject(helper, original, restore(helper, unit.scaled(2_147_483_647L)), 2_147_483_647L);
        helper.succeed();
    }

    public static void invalidBatchDefinitionCannotBecomeAnUnscaledTask(GameTestHelper helper) {
        var original = original(helper);
        assertInvalid(helper, marked(original.getDefinition(), new PatternBatchData(2, POOL, RECIPE, 1, 256)));
        assertInvalid(helper, marked(original.getDefinition(), data(Long.MAX_VALUE)));
        assertInvalid(helper, marked(original.getDefinition(), new PatternBatchData(1, POOL, RECIPE, Long.MAX_VALUE, 2)));
        for (var binding : List.of(new PatternBatchData(1, POOL, MMCR.id("missing_batch_recipe"), 1, 256),
                new PatternBatchData(1, MMCR.id("wrong_batch_pool"), RECIPE, 1, 256))) {
            var saved = marked(original.getDefinition(), binding);
            helper.assertTrue(PatternDetailsHelper.decodePattern(roundTrip(helper, saved), helper.getLevel())
                            instanceof PatternBatchDetails restored && restored.getDefinition().equals(saved),
                    "Self-contained identities restore independently of current catalog execution eligibility");
        }
        assertInvalid(helper, marked(AEItemKey.of(Items.STONE), data(256)));
        assertMalformedMarker(helper, original, marker -> marker.putLong("multiplier", 0));
        assertMalformedMarker(helper, original, marker -> marker.putLong("recipe_crafts_per_pattern", -1));
        assertMalformedMarker(helper, original, marker -> marker.remove("recipe"));
        assertMalformedMarker(helper, original, marker -> marker.putString("version", "broken"));
        var unit = new PatternBatchDetails(original, data(1));
        boolean rejected = false;
        try {
            new PatternBatchDetails(unit, data(256));
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        helper.assertTrue(rejected && original.getDefinition().get(ModDataComponents.PATTERN_BATCH.get()) == null,
                "Nested wrapping is rejected without modifying the base");
        helper.succeed();
    }

    public static void nativeExecutingJobReloadKeepsFullAndRemainder(GameTestHelper helper) {
        var original = original(helper);
        var full = new PatternBatchDetails(original, data(256));
        var tail = full.scaled(16);
        Map<IPatternDetails, Long> tasks = Map.of(full, 39L, tail, 1L);
        CraftingCpuLogic cpu = nativeJob(helper, tasks);
        KeyCounter materials = new KeyCounter();
        for (var input : original.getInputs()) {
            var template = input.getPossibleInputs()[0];
            long amount = Math.multiplyExact(Math.multiplyExact(template.amount(), input.getMultiplier()), 10_000L);
            materials.add(template.what(), amount);
            cpu.getInventory().insert(template.what(), amount, Actionable.MODULATE);
        }
        for (int round = 0; round < 2; round++) {
            CompoundTag saved = new CompoundTag();
            cpu.writeToNBT(saved, helper.getLevel().registryAccess());
            var restored = cluster(helper).craftingLogic;
            restored.readFromNBT(saved, helper.getLevel().registryAccess());
            helper.assertTrue(restored.hasJob() && taskCounts(restored).equals(tasks),
                    "Repeated native CPU reload preserves 256x39 and 16x1 independently");
            for (var entry : materials) helper.assertTrue(restored.getStored(entry.getKey()) == entry.getLongValue(),
                    "Native CPU owns the exact undispatched long material balance after reload");
            for (var output : original.getOutputs()) helper.assertTrue(restored.getWaitingFor(output.what()) == 0,
                    "Undispatched reload does not invent waiting output accounting");
            cpu = restored;
        }
        helper.succeed();
    }

    private static IPatternDetails original(GameTestHelper helper) {
        if (!MachineRegistry.containsStatic(POOL)) {
            MachineRegistry.register(new DynamicMachine(POOL, "Pattern batch codec test", new BlockArray(Map.of())));
        }
        if (RecipeRegistry.getRecipe(RECIPE) == null) RecipeRegistry.registerStatic(MachineRecipe.fromCanonical(
                RECIPE, POOL, 20, List.of(), List.of(), List.of(), 0, 1, false, true, false, Set.of()));
        ItemStack iron = new ItemStack(Items.IRON_INGOT);
        iron.set(DataComponents.CUSTOM_NAME, Component.literal("Batch input components"));
        ItemStack encoded = PatternDetailsHelper.encodeProcessingPattern(
                List.of(new GenericStack(AEItemKey.of(iron), 3), new GenericStack(AEFluidKey.of(Fluids.WATER), 1000)),
                List.of(new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 2), new GenericStack(AEFluidKey.of(Fluids.LAVA), 500)));
        encoded.set(DataComponents.CUSTOM_NAME, Component.literal("Physical pattern components"));
        var pattern = PatternDetailsHelper.decodePattern(encoded, helper.getLevel());
        helper.assertTrue(pattern != null, "Physical processing pattern decodes");
        return pattern;
    }

    private static PatternBatchData data(long multiplier) {
        return new PatternBatchData(1, POOL, RECIPE, 1, multiplier);
    }

    private static AEItemKey marked(AEItemKey key, PatternBatchData data) {
        var stack = key.toStack();
        stack.set(ModDataComponents.PATTERN_BATCH.get(), data);
        return AEItemKey.of(stack);
    }

    private static PatternBatchDetails restore(GameTestHelper helper, PatternBatchDetails pattern) {
        var definition = roundTrip(helper, pattern.getDefinition());
        var decoded = AEPatternDecoder.INSTANCE.decodePattern(definition, helper.getLevel());
        helper.assertTrue(decoded instanceof PatternBatchDetails
                        && decoded.equals(PatternDetailsHelper.decodePattern(definition, helper.getLevel())),
                "Direct and standard native decoding claim the same batch protocol");
        return (PatternBatchDetails) decoded;
    }

    private static AEItemKey roundTrip(GameTestHelper helper, AEItemKey key) {
        var restored = AEItemKey.fromTag(helper.getLevel().registryAccess(), key.toTag(helper.getLevel().registryAccess()));
        helper.assertTrue(key.equals(restored), "Registry-aware AE key NBT preserves all components");
        return restored;
    }

    private static void extractAndReinject(GameTestHelper helper, IPatternDetails original, PatternBatchDetails batch, long multiplier) {
        var inventory = new ListCraftingInventory(key -> { });
        var supplied = new KeyCounter();
        for (int i = 0; i < original.getInputs().length; i++) {
            var input = original.getInputs()[i];
            var scaled = batch.getInputs()[i];
            var template = input.getPossibleInputs()[0];
            helper.assertTrue(List.of(input.getPossibleInputs()).equals(List.of(scaled.getPossibleInputs()))
                            && scaled.isValid(template.what(), helper.getLevel())
                            && scaled.getMultiplier() == Math.multiplyExact(input.getMultiplier(), multiplier),
                    "Scaling preserves alternatives/components and scales the actual native multiplier");
            long amount = Math.multiplyExact(template.amount(), scaled.getMultiplier());
            supplied.add(template.what(), amount);
            inventory.insert(template.what(), amount, Actionable.MODULATE);
        }
        var outputs = new KeyCounter();
        var extracted = CraftingCpuHelper.extractPatternInputs(batch, inventory, helper.getLevel(), outputs, new KeyCounter());
        helper.assertTrue(extracted != null && inventory.list.isEmpty(), "Native extraction consumes the exact scaled item/fluid request");
        for (var output : original.getOutputs()) helper.assertTrue(outputs.get(output.what()) == Math.multiplyExact(output.amount(), multiplier),
                "Native extraction records exact long promised outputs");
        CraftingCpuHelper.reinjectPatternInputs(inventory, extracted);
        for (var entry : supplied) helper.assertTrue(inventory.list.get(entry.getKey()) == entry.getLongValue(),
                "Native rejection/reinjection restores the entire input balance");
    }

    private static void assertInvalid(GameTestHelper helper, AEItemKey key) {
        var persisted = roundTrip(helper, key);
        boolean rejected = false;
        try {
            PatternDetailsHelper.decodePattern(persisted, helper.getLevel());
        } catch (IllegalArgumentException | ArithmeticException expected) {
            rejected = true;
        }
        helper.assertTrue(rejected, "Invalid marked definitions fail explicitly rather than becoming bare tasks");
        IPatternDetails damaged = new IPatternDetails() {
            @Override public AEItemKey getDefinition() { return persisted; }
            @Override public IInput[] getInputs() { return new IInput[0]; }
            @Override public List<GenericStack> getOutputs() { return List.of(new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 1)); }
        };
        var cpu = nativeJob(helper, Map.of(damaged, 1L));
        var saved = new CompoundTag();
        cpu.writeToNBT(saved, helper.getLevel().registryAccess());
        boolean rejectedRestore = false;
        try {
            cluster(helper).craftingLogic.readFromNBT(saved, helper.getLevel().registryAccess());
        } catch (IllegalArgumentException | ArithmeticException expected) {
            rejectedRestore = true;
        }
        helper.assertTrue(rejectedRestore && taskCounts(cpu).get(damaged) == 1L,
                "Native CPU restore reports a damaged task instead of silently dropping/unscaling it");
    }

    private static void assertMalformedMarker(GameTestHelper helper, IPatternDetails original, Consumer<CompoundTag> corrupt) {
        var tag = marked(original.getDefinition(), data(256)).toTag(helper.getLevel().registryAccess());
        corrupt.accept(tag.getCompound("components").getCompound("mmcr:pattern_batch"));
        var key = AEItemKey.fromTag(helper.getLevel().registryAccess(), tag);
        helper.assertTrue(key != null && key.get(ModDataComponents.PATTERN_BATCH.get()) != null,
                "Partial component decoding cannot discard the invalid batch marker");
        assertInvalid(helper, key);
    }

    private static CraftingCpuLogic nativeJob(GameTestHelper helper, Map<IPatternDetails, Long> tasks) {
        var cluster = cluster(helper);
        var plan = new CraftingPlan(new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 20_000), 1, false, false,
                new KeyCounter(), new KeyCounter(), new KeyCounter(), tasks);
        try {
            Class<?> listener = Class.forName(ExecutingCraftingJob.class.getName() + "$CraftingDifferenceListener");
            var constructor = ExecutingCraftingJob.class.getDeclaredConstructor(ICraftingPlan.class, listener, CraftingLink.class, Integer.class);
            constructor.setAccessible(true);
            var callback = Proxy.newProxyInstance(listener.getClassLoader(), new Class<?>[]{listener}, (proxy, method, args) -> null);
            var linkData = new CompoundTag();
            linkData.putUUID("craftId", UUID.randomUUID());
            linkData.putBoolean("standalone", true);
            linkData.putBoolean("req", false);
            var job = constructor.newInstance(plan, callback, new CraftingLink(linkData, cluster), null);
            field(CraftingCpuLogic.class, "job").set(cluster.craftingLogic, job);
            return cluster.craftingLogic;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot construct native AE job fixture", exception);
        }
    }

    static Map<IPatternDetails, Long> taskCounts(CraftingCpuLogic cpu) {
        try {
            var job = field(CraftingCpuLogic.class, "job").get(cpu);
            var tasks = (Map<?, ?>) field(ExecutingCraftingJob.class, "tasks").get(job);
            Map<IPatternDetails, Long> counts = new HashMap<>();
            for (var entry : tasks.entrySet()) counts.put((IPatternDetails) entry.getKey(),
                    field(entry.getValue().getClass(), "value").getLong(entry.getValue()));
            return counts;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot inspect native task map", exception);
        }
    }

    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        var field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static CraftingCPUCluster cluster(GameTestHelper helper) {
        var position = helper.absolutePos(BlockPos.ZERO);
        var cluster = new CraftingCPUCluster(position, position);
        var core = new CraftingBlockEntity(AEBlockEntities.CRAFTING_STORAGE.get(), position,
                AEBlocks.CRAFTING_STORAGE_64K.block().defaultBlockState());
        core.setLevel(helper.getLevel());
        core.setCoreBlock(true);
        try {
            field(CraftingCPUCluster.class, "machineSrc").set(cluster, new MachineSource(core));
            field(CraftingBlockEntity.class, "cluster").set(core, cluster);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot bind native CPU core fixture", exception);
        }
        return cluster;
    }
}
