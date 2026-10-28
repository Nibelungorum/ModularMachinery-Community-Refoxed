package cn.howxu.mmcr.internal.recipe;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.storage.LongValueStorage;
import cn.howxu.mmcr.api.compat.mekanism.ChemicalIngredient;
import cn.howxu.mmcr.api.compat.ae2lt.LightningTier;
import cn.howxu.mmcr.compat.ae2lt.LightningRequirement;
import cn.howxu.mmcr.compat.ae2lt.loaded.LightningCapability;
import cn.howxu.mmcr.api.recipe.CraftingContext;
import cn.howxu.mmcr.api.recipe.EffectiveRecipeResolver;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.component.ComponentPredicate;
import cn.howxu.mmcr.api.recipe.component.DataComponentPredicateSet;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
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
import cn.howxu.mmcr.internal.storage.LongResourceStorage;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.util.IOType;
import com.google.gson.JsonPrimitive;
import mekanism.common.registries.MekanismChemicals;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.ItemTags;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

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
        try (Transaction transaction = Transaction.openRoot()) {
            input.insert(0, ItemResource.of(sword), 1L, transaction);
            transaction.commit();
        }
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
        try (Transaction transaction = Transaction.openRoot()) {
            secondInput.insert(0, ItemResource.of(secondSword), 1L, transaction);
            transaction.commit();
        }
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
        try (Transaction transaction = Transaction.openRoot()) {
            tagInput.insert(0, ItemResource.of(Items.OAK_LOG), 4L, transaction);
            transaction.commit();
        }
        var logs = new ItemRequirement(RecipeModifier.IOType.INPUT,
                Ingredient.of(BuiltInRegistries.ITEM.get(ItemTags.LOGS).orElseThrow()), 1, ItemStack.EMPTY);
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
        var chemical = helper.getBlockEntity(chemicalPos, ChemicalPortBlockEntity.class);
        var heat = helper.getBlockEntity(heatPos, HeatPortBlockEntity.class);
        var heatOutput = helper.getBlockEntity(outputPos, HeatPortBlockEntity.class);
        chemical.chemicalTank().setContents(MekanismChemicals.OXYGEN.asResource(), 1_200L, null);
        try (var transaction = Transaction.openRoot()) {
            heat.heatCapacitor().setHeat(800D * heat.heatCapacitor().getHeatCapacity(), transaction);
            transaction.commit();
        }
        var energy = new LongValueStorage(100L, 100L, null);
        energy.setAmount(30L);
        double beforeHeat = heat.heatCapacitor().getHeat();
        var lightning = new LongResourceStorage<>(LightningTier.class, 2, 1_000L, ignored -> false, null);
        lightning.setContents(0, LightningTier.HIGH_VOLTAGE, 120L);
        lightning.setContents(1, LightningTier.EXTREME_HIGH_VOLTAGE, 60L);
        var lightningOutput = new LongResourceStorage<>(LightningTier.class, 1, 100L, ignored -> false, null);
        var capabilities = new CapabilitySnapshot(List.of(new ChemicalPortCapability(chemical),
                new HeatPortCapability(heat), new HeatPortCapability(heatOutput), new EnergyHatchCapability(energy, IOType.INPUT),
                new LightningCapability(lightning, IOType.INPUT), new LightningCapability(lightningOutput, IOType.OUTPUT)));
        var oxygen = LoadedChemicalRequirement.input(ChemicalIngredient.chemical(Identifier.parse("mekanism:oxygen"), 300L));
        compare(helper, capabilities, List.of(oxygen, oxygen, new EnergyRequirement(5L),
                LoadedHeatRequirement.minimumTemperature(600D), LoadedHeatRequirement.outputHeat(10D)));
        compare(helper, capabilities, List.of(oxygen, LoadedHeatRequirement.minimumTemperature(900D)));
        compare(helper, capabilities, List.of(oxygen, new EnergyRequirement(5L),
                LoadedHeatRequirement.minimumTemperature(600D), LightningRequirement.input(LightningTier.HIGH_VOLTAGE, 30L),
                LightningRequirement.input(LightningTier.EXTREME_HIGH_VOLTAGE, 20L),
                LightningRequirement.output(LightningTier.HIGH_VOLTAGE, 10L, 1F)));
        compare(helper, capabilities, List.of(LightningRequirement.input(LightningTier.EXTREME_HIGH_VOLTAGE, 61L)));
        helper.assertTrue(chemical.chemicalTank().amountAsLong() == 1_200L && energy.amount() == 30L
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
}
