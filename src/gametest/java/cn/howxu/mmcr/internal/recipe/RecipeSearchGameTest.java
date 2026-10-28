package cn.howxu.mmcr.internal.recipe;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.plan.PlanningReservations;
import cn.howxu.mmcr.internal.storage.LongEnergyStorage;
import cn.howxu.mmcr.api.compat.ae2lt.LightningTier;
import cn.howxu.mmcr.api.compat.mekanism.ChemicalIngredient;
import cn.howxu.mmcr.api.recipe.CraftingContext;
import cn.howxu.mmcr.api.recipe.EffectiveRecipeResolver;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.component.ComponentPredicate;
import cn.howxu.mmcr.api.recipe.component.DataComponentPredicateSet;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.compat.ae2lt.LightningRequirement;
import cn.howxu.mmcr.compat.ae2lt.LightningStorage;
import cn.howxu.mmcr.compat.ae2lt.loaded.LightningCapability;
import cn.howxu.mmcr.compat.mekanism.loaded.ChemicalPortBlockEntity;
import cn.howxu.mmcr.compat.mekanism.loaded.ChemicalPortCapability;
import cn.howxu.mmcr.compat.mekanism.loaded.HeatPortBlockEntity;
import cn.howxu.mmcr.compat.mekanism.loaded.HeatPortCapability;
import cn.howxu.mmcr.compat.mekanism.loaded.LoadedChemicalRequirement;
import cn.howxu.mmcr.compat.mekanism.loaded.LoadedHeatRequirement;
import cn.howxu.mmcr.internal.capability.EnergyHatchCapability;
import cn.howxu.mmcr.internal.capability.ItemBusCapability;
import cn.howxu.mmcr.internal.multiblock.ModuleConnectionStatus;
import cn.howxu.mmcr.internal.runtime.ControllerRuntimeSnapshot;
import cn.howxu.mmcr.internal.runtime.StructureSnapshot;
import cn.howxu.mmcr.internal.storage.BulkItemStorage;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.util.IOType;
import com.google.gson.JsonPrimitive;
import mekanism.common.registries.MekanismChemicals;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.ItemTags;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import mekanism.api.chemical.ChemicalStack;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/** Runtime-only component/registry differential checks; no fixed tick completion assumptions.
 * @author howxu <dev@howxu.cn>
 */
public final class RecipeSearchGameTest {
    public static void componentPredicatesAndOutputStackLimits(GameTestHelper helper) {
        BulkItemStorage input = new BulkItemStorage(64L, null);
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        sword.set(DataComponents.DAMAGE, 7);
        input.forceInsert(0, sword, 1L, false);
        CapabilitySnapshot inputs = new CapabilitySnapshot(List.of(new ItemBusCapability(input, IOType.INPUT)));
        for (int damage : List.of(7, 8)) {
            var components = new DataComponentPredicateSet(Map.of(DataComponents.DAMAGE,
                    ComponentPredicate.exact(new JsonPrimitive(damage))));
            var requirement = new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.DIAMOND_SWORD),
                    1, ItemStack.EMPTY, 1F, List.of(), components, 1F);
            compare(helper, inputs, List.of(requirement));
        }
        BulkItemStorage secondInput = new BulkItemStorage(64L, null);
        ItemStack secondSword = sword.copy();
        secondSword.set(DataComponents.DAMAGE, 8);
        secondInput.forceInsert(0, secondSword, 1L, false);
        CapabilitySnapshot variants = new CapabilitySnapshot(List.of(new ItemBusCapability(input, IOType.INPUT),
                new ItemBusCapability(secondInput, IOType.INPUT)));
        for (int secondDamage : List.of(8, 9)) {
            List<MachineRequirement> requirements = List.of(7, secondDamage).stream().map(damage ->
                    (MachineRequirement) new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.DIAMOND_SWORD),
                            1, ItemStack.EMPTY, 1F, List.of(), new DataComponentPredicateSet(Map.of(DataComponents.DAMAGE,
                            ComponentPredicate.exact(new JsonPrimitive(damage)))), 1F)).toList();
            compare(helper, variants, requirements);
        }
        BulkItemStorage tagInput = new BulkItemStorage(64L, null);
        tagInput.forceInsert(0, new ItemStack(Items.OAK_LOG), 4L, false);
        var logs = new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(ItemTags.LOGS), 1, ItemStack.EMPTY);
        compare(helper, new CapabilitySnapshot(List.of(new ItemBusCapability(tagInput, IOType.INPUT))), List.of(logs));
        compare(helper, inputs, List.of(logs));
        BulkItemStorage output = new BulkItemStorage(64L, null);
        CapabilitySnapshot outputs = new CapabilitySnapshot(List.of(new ItemBusCapability(output, IOType.OUTPUT)));
        var tool = new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0, sword);
        compare(helper, outputs, List.of(tool));
        ItemStack named = new ItemStack(Items.GOLD_INGOT);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("snapshot output"));
        named.set(DataComponents.MAX_STACK_SIZE, 4);
        compare(helper, outputs, List.of(new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0, named)));
        helper.assertTrue(input.amount(0) == 1L && output.amount(0) == 0L,
                "Both planners simulate component-bearing resources without paying or producing them");
        helper.succeed();
    }

    public static void chemicalAndHeatMatchNativeHandlers(GameTestHelper helper) {
        BlockPos chemicalPos = new BlockPos(0, 1, 0), heatPos = new BlockPos(1, 1, 0), outputPos = new BlockPos(1, 1, 1);
        helper.setBlock(chemicalPos, ModBlocks.BLOCKS.get("chemical_input_hatch_basic").get().defaultBlockState());
        helper.setBlock(heatPos, ModBlocks.BLOCKS.get("heat_input_hatch").get().defaultBlockState());
        helper.setBlock(outputPos, ModBlocks.BLOCKS.get("heat_output_hatch").get().defaultBlockState());
        ChemicalPortBlockEntity chemical = helper.getBlockEntity(chemicalPos);
        HeatPortBlockEntity heat = helper.getBlockEntity(heatPos);
        HeatPortBlockEntity heatOutput = helper.getBlockEntity(outputPos);
        chemical.chemicalTank().setStack(new ChemicalStack(MekanismChemicals.OXYGEN, 1_200L));
        heat.heatCapacitor().setHeat(800D * heat.heatCapacitor().getHeatCapacity());
        var energy = new LongEnergyStorage(100L, 100L, null);
        energy.setAmount(30L);
        double beforeHeat = heat.heatCapacitor().getHeat();
        var lightning = new TestLightningStorage(2, 1_000L);
        lightning.setContents(0, LightningTier.HIGH_VOLTAGE, 120L);
        lightning.setContents(1, LightningTier.EXTREME_HIGH_VOLTAGE, 60L);
        var lightningOutput = new TestLightningStorage(1, 100L);
        var capabilities = new CapabilitySnapshot(List.of(new ChemicalPortCapability(chemical),
                new HeatPortCapability(heat), new HeatPortCapability(heatOutput), new EnergyHatchCapability(energy, IOType.INPUT),
                new LightningCapability(lightning, IOType.INPUT), new LightningCapability(lightningOutput, IOType.OUTPUT)));
        var oxygen = LoadedChemicalRequirement.input(ChemicalIngredient.chemical(ResourceLocation.parse("mekanism:oxygen"), 300L));
        compare(helper, capabilities, List.of(oxygen, oxygen, new EnergyRequirement(5L),
                LoadedHeatRequirement.minimumTemperature(600D), LoadedHeatRequirement.outputHeat(10D)));
        compare(helper, capabilities, List.of(oxygen, LoadedHeatRequirement.minimumTemperature(900D)));
        compare(helper, capabilities, List.of(oxygen, new EnergyRequirement(5L),
                LoadedHeatRequirement.minimumTemperature(600D), LightningRequirement.input(LightningTier.HIGH_VOLTAGE, 30L),
                LightningRequirement.input(LightningTier.EXTREME_HIGH_VOLTAGE, 20L),
                LightningRequirement.output(LightningTier.HIGH_VOLTAGE, 10L, 1F)));
        compare(helper, capabilities, List.of(LightningRequirement.input(LightningTier.EXTREME_HIGH_VOLTAGE, 61L)));
        helper.assertTrue(chemical.chemicalTank().getStored() == 1_200L && energy.getAmountAsLong() == 30L
                        && heat.heatCapacitor().getHeat() == beforeHeat && lightning.amount(0) == 120L
                        && lightning.amount(1) == 60L && lightningOutput.amount(0) == 0L,
                "Chemical, heat, FE and lightning search must leave the original resources untouched");
        helper.succeed();
    }

    private static void compare(GameTestHelper helper, CapabilitySnapshot capabilities, List<MachineRequirement> requirements) {
        MachineRecipe recipe = MachineRecipe.fromCanonical(MMCR.id("search_runtime_oracle"), MMCR.id("search_runtime_pool"),
                20, requirements, List.of(), List.of(), 0, 1, false, true, false, Set.of());
        var expected = new CraftingContext(capabilities).planStartResult(recipe, 16L);
        var runtime = new ControllerRuntimeSnapshot(StructureSnapshot.empty(), 0L, 0L, 0L, Map.of(), Map.of(), Set.of(),
                ModuleConnectionStatus.notRequired(), 0, null, null, List.of(), List.of(), List.of(), "", "", 0,
                false, false, 0, 0, 16L);
        var effective = new EffectiveRecipeResolver().resolve(recipe, runtime, List.of());
        var snapshot = RecipeSearchCapture.capture(runtime, 0L, 16L, List.of(effective), capabilities, false);
        Thread owner = Thread.currentThread();
        var actual = CompletableFuture.supplyAsync(() -> {
            if (Thread.currentThread() == owner) throw new AssertionError("Search must execute on a worker");
            return new RecipeSearchPlanner(snapshot).advance();
        }).join();
        if (expected.successful()) {
            helper.assertTrue(actual instanceof RecipeSearchPlanner.Match match
                            && match.parallelism() == expected.plan().parallelism()
                            && match.scope().equals(expected.plan().inputScope()),
                    "Native snapshot match must retain synchronous scope and feasible parallelism: " + actual);
        } else {
            helper.assertTrue(actual instanceof RecipeSearchPlanner.NoMatch noMatch
                            && noMatch.failures().size() == 1
                            && noMatch.failures().getFirst().reasonId().equals(expected.failure().reason().id()),
                    "Native snapshot rejection must retain the synchronous failure kind: " + actual);
        }
    }

    /** Runtime lightning storage for differential search checks.
     * @author howxu <dev@howxu.cn>
     */
    private static final class TestLightningStorage implements LightningStorage {
        private final LightningTier[] tiers;
        private final long[] amounts;
        private final long capacity;

        TestLightningStorage(int size, long capacity) {
            tiers = new LightningTier[size];
            amounts = new long[size];
            this.capacity = capacity;
        }

        void setContents(int slot, LightningTier tier, long amount) { tiers[slot] = tier; amounts[slot] = amount; }
        @Override public int size() { return tiers.length; }
        @Override public Object identity() { return this; }
        @Override public Object address(int slot) { return slot; }
        @Override public Object key(LightningTier tier) { return tier; }
        @Override public Object storedKey(int slot) { return tiers[slot]; }
        @Override public long amount(int slot) { return amounts[slot]; }
        @Override public long capacity(int slot, LightningTier tier) { return capacity; }
        @Override public long extract(int slot, LightningTier tier, long amount, boolean simulate) {
            throw new AssertionError("Search must not consume lightning");
        }
        @Override public long insert(int slot, LightningTier tier, long amount, boolean simulate) {
            throw new AssertionError("Search must not produce lightning");
        }
        @Override public long insert(LightningTier tier, long amount, boolean simulate) {
            throw new AssertionError("Search must not produce lightning");
        }
        @Override public long reserveOutput(LightningTier tier, long amount, PlanningReservations reservations) {
            long remaining = amount;
            for (int slot = 0; slot < size() && remaining > 0L; slot++) {
                Object current = reservations.nativeKey(this, slot, tiers[slot]);
                long stored = reservations.nativeAmount(this, slot, amounts[slot]);
                if (stored > 0L && current != tier) continue;
                long moved = Math.min(remaining, Math.max(0L, capacity - stored));
                if (moved > 0L && reservations.reserveNativeInsert(this, slot, tier, tiers[slot], amounts[slot], capacity, moved)) {
                    remaining -= moved;
                }
            }
            return amount - remaining;
        }
    }
}
