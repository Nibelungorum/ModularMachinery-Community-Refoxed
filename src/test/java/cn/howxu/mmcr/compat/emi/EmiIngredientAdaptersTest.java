package cn.howxu.mmcr.compat.emi;

import cn.howxu.mmcr.api.recipe.MachineIngredient;
import cn.howxu.mmcr.api.recipe.OutputRegistry;
import cn.howxu.mmcr.api.recipe.component.ComponentPredicate;
import cn.howxu.mmcr.api.recipe.component.DataComponentPredicateSet;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.IOType;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerRegistry;
import cn.howxu.mmcr.client.recipe.MachineRecipeData;
import cn.howxu.mmcr.client.recipe.RecipeDisplayEntry;
import cn.howxu.mmcr.client.recipe.RecipeDisplayEntry.Kind;
import cn.howxu.mmcr.compat.ars_nouveau.ArsNouveauRecipeTypes;
import cn.howxu.mmcr.compat.ars_nouveau.SourceRequirement;
import cn.howxu.mmcr.compat.botania.ManaRequirement;
import cn.howxu.mmcr.test.RecipeTestSupport;
import cn.howxu.mmcr.test.TestBootstrap;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import dev.emi.emi.api.stack.Comparison;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author howxu <dev@howxu.cn>
 */
class EmiIngredientAdaptersTest {
    @BeforeAll
    static void bootstrap() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void itemSnapshotComponentsAndLongAmountSurviveConversionAndCopy() {
        ItemStack candidate = new ItemStack(Items.DIAMOND_SWORD, 3);
        candidate.set(DataComponents.CUSTOM_NAME, Component.literal("snapshot"));
        candidate.set(DataComponents.REPAIR_COST, 4);
        var entry = entry(Kind.ITEM, IOType.OUTPUT, 5_000_000_000L, 0.25F,
                List.of(candidate), List.of(), DataComponentPredicateSet.EMPTY, true);
        candidate.remove(DataComponents.CUSTOM_NAME);

        var converted = EmiIngredientAdapters.convert(entry);
        EmiStack stack = converted.getEmiStacks().getFirst();

        assertThat(converted.getAmount()).isEqualTo(entry.amount());
        assertThat(stack.getAmount()).isEqualTo(entry.amount());
        assertThat(stack.getChance()).isEqualTo(0.25F);
        assertThat(stack.getComponentChanges()).isEqualTo(entry.items().getFirst().getComponentsPatch());
        stack.copy().setAmount(2);
        ItemStack displayed = stack.getItemStack();
        displayed.remove(DataComponents.CUSTOM_NAME);
        assertThat(entry.items().getFirst().get(DataComponents.CUSTOM_NAME))
                .isEqualTo(Component.literal("snapshot"));
        assertThat(entry.items().getFirst().getCount()).isEqualTo(3);
        assertThat(stack.get(DataComponents.REPAIR_COST)).isEqualTo(4);
    }

    @Test
    void exactComparisonProjectsBothSidesOntoOnlyConstrainedKeys() {
        var components = exactRepairCost(3);
        ItemStack candidate = new ItemStack(Items.DIAMOND_SWORD);
        candidate.set(DataComponents.REPAIR_COST, 3);
        EmiStack constrained = EmiIngredientAdapters.convert(entry(Kind.ITEM, IOType.INPUT, 2, 1F,
                List.of(candidate), List.of(), components, true)).getEmiStacks().getFirst();
        ItemStack matching = candidate.copy();
        matching.set(DataComponents.CUSTOM_NAME, Component.literal("unconstrained"));
        matching.set(DataComponents.DAMAGE, 7);
        EmiStack other = EmiStack.of(matching, 8);

        assertThat(constrained.isEqual(other)).isTrue();
        assertThat(other.isEqual(constrained)).isTrue();
        matching.set(DataComponents.REPAIR_COST, 4);
        EmiStack different = EmiStack.of(matching);
        assertThat(constrained.isEqual(different)).isFalse();
        assertThat(different.isEqual(constrained)).isFalse();
        EmiStack absent = EmiStack.of(new ItemStack(Items.DIAMOND_SWORD));
        assertThat(constrained.isEqual(absent)).isFalse();
        assertThat(absent.isEqual(constrained)).isFalse();
        assertThat(constrained.copy().isEqual(different)).isFalse();
    }

    @Test
    void constraintsOnDefaultComponentsCompareEffectiveValues() {
        ItemStack candidate = new ItemStack(Items.DIAMOND_SWORD);
        candidate.set(DataComponents.DAMAGE, 0);
        var components = new DataComponentPredicateSet(Map.of(DataComponents.DAMAGE,
                ComponentPredicate.exact(new Dynamic<>(JsonOps.INSTANCE, new JsonPrimitive(0)))));
        EmiStack constrained = EmiIngredientAdapters.convert(entry(Kind.ITEM, IOType.INPUT, 1, 1F,
                List.of(candidate), List.of(), components, true)).getEmiStacks().getFirst();
        EmiStack nativeDefault = EmiStack.of(new ItemStack(Items.DIAMOND_SWORD));

        assertThat(constrained.isEqual(nativeDefault)).isTrue();
        assertThat(nativeDefault.isEqual(constrained)).isTrue();
    }

    @Test
    void unconstrainedItemsKeepNativeComparison() {
        EmiStack stack = EmiIngredientAdapters.convert(entry(Kind.ITEM, IOType.INPUT, 1, 1F,
                List.of(new ItemStack(Items.IRON_INGOT)), List.of(),
                DataComponentPredicateSet.EMPTY, true)).getEmiStacks().getFirst();

        stack.comparison(comparison -> {
            assertThat(comparison).isSameAs(Comparison.DEFAULT_COMPARISON);
            return comparison;
        });
    }

    @Test
    void everyNonConsumedCandidateKeepsFullAmountAndIndependentRemainder() {
        var converted = EmiIngredientAdapters.convert(entry(Kind.ITEM, IOType.INPUT, 8, 0F,
                List.of(new ItemStack(Items.IRON_INGOT), new ItemStack(Items.GOLD_INGOT)),
                List.of(), DataComponentPredicateSet.EMPTY, true));

        assertThat(converted.getAmount()).isEqualTo(8);
        assertThat(converted.getChance()).isEqualTo(1F);
        assertThat(converted.getEmiStacks()).hasSize(2).allSatisfy(stack -> {
            assertThat(stack.getAmount()).isEqualTo(8);
            assertThat(stack.getItemStack().getCount()).isEqualTo(8);
            assertThat(stack.getRemainder().getAmount()).isEqualTo(8);
            assertThat(stack.getRemainder().isEqual(stack)).isTrue();
            assertThat(stack.getRemainder()).isNotSameAs(stack);
            stack.copy().getRemainder().setAmount(1);
            assertThat(stack.getRemainder().getAmount()).isEqualTo(8);
        });
    }

    @Test
    void chanceDoesNotTurnCandidateQuantitiesIntoExpectedValues() {
        var converted = EmiIngredientAdapters.convert(entry(Kind.ITEM, IOType.INPUT, 7, 0.25F,
                List.of(new ItemStack(Items.IRON_INGOT), new ItemStack(Items.GOLD_INGOT)),
                List.of(), DataComponentPredicateSet.EMPTY, true));

        assertThat(converted.getAmount()).isEqualTo(7);
        assertThat(converted.getChance()).isEqualTo(0.25F);
        assertThat(converted.getEmiStacks()).allSatisfy(stack -> {
            assertThat(stack.getAmount()).isEqualTo(7);
            assertThat(stack.getChance()).isEqualTo(0.25F);
            assertThat(stack.getRemainder().isEmpty()).isTrue();
        });
    }

    @Test
    void fluidConversionKeepsPatchQuantityAndChanceWithoutMutatingSnapshot() {
        FluidStack candidate = new FluidStack(Fluids.WATER, 300);
        candidate.set(DataComponents.CUSTOM_NAME, Component.literal("fluid snapshot"));
        var entry = entry(Kind.FLUID, IOType.OUTPUT, 5_000_000_000L, 0.5F,
                List.of(), List.of(candidate), DataComponentPredicateSet.EMPTY, true);
        candidate.remove(DataComponents.CUSTOM_NAME);

        EmiStack converted = EmiIngredientAdapters.convert(entry).getEmiStacks().getFirst();

        assertThat(converted.getKey()).isSameAs(Fluids.WATER);
        assertThat(converted.getAmount()).isEqualTo(entry.amount());
        assertThat(converted.getChance()).isEqualTo(0.5F);
        assertThat(converted.getComponentChanges()).isEqualTo(entry.fluids().getFirst().getComponentsPatch());
        converted.copy().setAmount(1);
        assertThat(entry.fluids().getFirst().getAmount()).isEqualTo(300);
        assertThat(entry.fluids().getFirst().get(DataComponents.CUSTOM_NAME))
                .isEqualTo(Component.literal("fluid snapshot"));
    }

    @Test
    void nonConsumedFluidCandidatesKeepTheirQuantityAsRemainder() {
        var converted = EmiIngredientAdapters.convert(entry(Kind.FLUID, IOType.INPUT, 1_234, 0F,
                List.of(), List.of(new FluidStack(Fluids.WATER, 1), new FluidStack(Fluids.LAVA, 1)),
                DataComponentPredicateSet.EMPTY, true));

        assertThat(converted.getEmiStacks()).hasSize(2).allSatisfy(stack -> {
            assertThat(stack.getAmount()).isEqualTo(1_234);
            assertThat(stack.getRemainder().getAmount()).isEqualTo(1_234);
            assertThat(stack.getRemainder().isEqual(stack)).isTrue();
        });
    }

    @Test
    void sourceConversionUsesOneIdentityForEitherDirection() {
        EmiStack input = EmiIngredientAdapters.convert(entry(Kind.SOURCE, IOType.INPUT, 9_000_000_000L,
                1F, List.of(), List.of(), DataComponentPredicateSet.EMPTY, true)).getEmiStacks().getFirst();
        EmiStack output = EmiIngredientAdapters.convert(entry(Kind.SOURCE, IOType.OUTPUT, 12,
                0.5F, List.of(), List.of(), DataComponentPredicateSet.EMPTY, true)).getEmiStacks().getFirst();

        assertThat(input.isEqual(output)).isTrue();
        assertThat(output.isEqual(input)).isTrue();
        assertThat(input.getAmount()).isEqualTo(9_000_000_000L);
        assertThat(output.getChance()).isEqualTo(0.5F);
    }

    @Test
    void sourceMaterialSupportsTreeWithoutItemComponentConstraints() {
        try (var requirements = RequirementHandlerRegistry.openTestScope();
             var outputs = OutputRegistry.openTestScope()) {
            ArsNouveauRecipeTypes.register();
            var data = data(List.of(SourceRequirement.input(5_000_000_000L)),
                    List.of(new ItemStack(Items.DIAMOND)));

            assertThat(EmiIngredientAdapters.supportsRecipeTree(data)).isTrue();
        }
    }

    @Test
    void unknownAndUnavailableChemicalResourcesKeepTheirEntryForTextFallback() {
        var unknown = entry(Kind.TEXT, IOType.OUTPUT, 10, 1F, List.of(), List.of(),
                DataComponentPredicateSet.EMPTY, true);
        var chemical = new RecipeDisplayEntry(IOType.INPUT, ResourceLocation.parse("mekanism:chemical"),
                Kind.CHEMICAL, unknown.requirement(), 4_000, 1F, List.of(), List.of(),
                List.of(ResourceLocation.parse("mekanism:oxygen")), DataComponentPredicateSet.EMPTY,
                true, unknown.tooltip());

        assertThat(EmiIngredientAdapters.convert(unknown)).isSameAs(EmiStack.EMPTY);
        assertThat(EmiIngredientAdapters.convert(chemical)).isSameAs(EmiStack.EMPTY);
        assertThat(unknown.amount()).isEqualTo(10);
        assertThat(unknown.tooltip()).isNotEmpty();
        assertThat(chemical.chemicals()).containsExactly(ResourceLocation.parse("mekanism:oxygen"));
    }

    @Test
    void recipeTreeRequiresCompleteInputsAndAtLeastOneProductiveOutput() {
        var input = new MachineIngredient.ItemIngredient(Ingredient.of(Items.IRON_INGOT), 2);
        assertThat(EmiIngredientAdapters.supportsRecipeTree(data(List.of(input),
                List.of(new ItemStack(Items.IRON_NUGGET, 4))))).isTrue();
        assertThat(EmiIngredientAdapters.supportsRecipeTree(data(List.of(input), List.of()))).isFalse();
        assertThat(EmiIngredientAdapters.supportsRecipeTree(data(List.of(input),
                List.of(MachineRequirement.itemOutput(new ItemStack(Items.IRON_NUGGET), 0F))))).isFalse();
        assertThat(EmiIngredientAdapters.supportsRecipeTree(data(
                List.of(new MachineIngredient.ItemIngredient(Ingredient.EMPTY, 2)),
                List.of(new ItemStack(Items.IRON_NUGGET))))).isFalse();
    }

    @Test
    void unresolvedConstraintsAndUnknownResourcesDisableTreeButRemainDisplayable() {
        var input = new MachineIngredient.ItemIngredient(Ingredient.of(Items.DIAMOND_SWORD), 1,
                new DataComponentPredicateSet(Map.of(DataComponents.DAMAGE, ComponentPredicate.range(1, 4))), 1F);
        var data = data(List.of(input), List.of(new ItemStack(Items.DIAMOND)));
        RecipeDisplayEntry entry = data.entries().stream().filter(value -> value.io() == IOType.INPUT)
                .findFirst().orElseThrow();

        assertThat(EmiIngredientAdapters.convert(entry).isEmpty()).isFalse();
        assertThat(EmiIngredientAdapters.supportsRecipeTree(data)).isFalse();
        try (var requirements = RequirementHandlerRegistry.openTestScope()) {
            RequirementHandlerRegistry.register(ManaRequirement.TYPE);
            var unsupported = data(List.of(ManaRequirement.input(20)), List.of(new ItemStack(Items.DIAMOND)));
            assertThat(unsupported.entries()).anyMatch(value -> value.kind() == Kind.TEXT);
            assertThat(EmiIngredientAdapters.supportsRecipeTree(unsupported)).isFalse();
        }
    }

    private static DataComponentPredicateSet exactRepairCost(int value) {
        return new DataComponentPredicateSet(Map.of(DataComponents.REPAIR_COST,
                ComponentPredicate.exact(new Dynamic<>(JsonOps.INSTANCE, new JsonPrimitive(value)))));
    }

    private static RecipeDisplayEntry entry(Kind kind, IOType io, long amount, float chance,
                                            List<ItemStack> items, List<FluidStack> fluids,
                                            DataComponentPredicateSet components, boolean exact) {
        MachineRequirement requirement = MachineRequirement.itemOutput(new ItemStack(Items.DIAMOND));
        return new RecipeDisplayEntry(io, ResourceLocation.parse("mmcr:test_resource"), kind, requirement,
                amount, chance, items, fluids, List.of(), components, exact,
                List.of(Component.translatable("jei.mmcr.source")));
    }

    private static MachineRecipeData data(List<?> inputs, List<?> outputs) {
        return MachineRecipeData.from(RecipeTestSupport.create(ResourceLocation.parse("mmcr:emi_adapter_test"),
                ResourceLocation.parse("mmcr:test_cube"), 20, inputs, outputs), null);
    }
}
