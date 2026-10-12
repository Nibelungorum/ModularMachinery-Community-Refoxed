package cn.howxu.mmcr.compat.emi;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.recipe.MachineIngredient;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import cn.howxu.mmcr.api.recipe.component.DataComponentPredicateSet;
import cn.howxu.mmcr.api.recipe.component.ComponentPredicate;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerRegistry;
import cn.howxu.mmcr.api.recipe.requirement.RequirementType;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.IOType;
import cn.howxu.mmcr.client.recipe.MachineRecipeData;
import cn.howxu.mmcr.compat.botania.ManaRequirement;
import cn.howxu.mmcr.compat.jei.MachineStructureDisplay;
import cn.howxu.mmcr.registry.ModDataComponents;
import cn.howxu.mmcr.registry.ModItems;
import cn.howxu.mmcr.test.RecipeTestSupport;
import cn.howxu.mmcr.test.TestBootstrap;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.Comparison;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.ListEmiIngredient;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/** @author howxu <dev@howxu.cn> */
class MachineEmiRecipeTest {
    @BeforeAll static void bootstrap() throws Exception {
        TestBootstrap.bootstrap();
        if (!ModDataComponents.BLUEPRINT_MACHINE.isBound()) {
            var field = DeferredHolder.class.getDeclaredField("holder");
            field.setAccessible(true);
            field.set(ModDataComponents.BLUEPRINT_MACHINE, Holder.direct(DataComponentType.<ResourceLocation>builder()
                    .persistent(ResourceLocation.CODEC).networkSynchronized(ResourceLocation.STREAM_CODEC).build()));
        }
    }

    @Test void idsRetainBothNamespacesAndOriginalPaths() {
        var first = recipe("first:shared/path", List.of(), List.of(new ItemStack(Items.DIAMOND)));
        var second = recipe("second:shared/path", List.of(), List.of(new ItemStack(Items.DIAMOND)));
        assertThat(first.getId()).isEqualTo(ResourceLocation.parse("mmcr:/recipe/first/shared/path"));
        assertThat(second.getId()).isEqualTo(ResourceLocation.parse("mmcr:/recipe/second/shared/path"));
        assertThat(first.getId()).isNotEqualTo(second.getId());
    }

    @Test void poolCategoryUsesExistingTitleAndSortsPriorityBeforeOriginalId() {
        var pool = ResourceLocation.parse("addon:shared/path");
        var category = MachineEmiPlugin.poolCategory(pool, EmiStack.EMPTY);
        var low = RecipeTestSupport.create(ResourceLocation.parse("addon:a"), pool, 20, List.of(), List.of(), List.of(), 0, 1);
        var highZ = RecipeTestSupport.create(ResourceLocation.parse("addon:z"), pool, 20, List.of(), List.of(), List.of(), 10, 1);
        var highA = highZ.withId(ResourceLocation.parse("addon:b"));
        var recipes = List.of(low, highZ, highA).stream().map(value ->
                new MachineEmiRecipe(category, MachineRecipeData.from(value, null), List.of())).toList();
        assertThat(category.getId()).isEqualTo(ResourceLocation.parse("addon:recipe_pool/shared/path"));
        assertThat(category.getName()).isEqualTo(Component.translatable(pool.toLanguageKey("recipe_pool")));
        assertThat(recipes.stream().sorted(category.getSort()).map(value -> value.data().recipeId()).toList())
                .containsExactly(highA.id(), highZ.id(), low.id());
    }

    @Test void structureHasOwnedDefaultMaterialsAndBoundBlueprintWithoutTreeOrCraftableStatus() {
        Machine machine = new Machine() {
            @Override public ResourceLocation registryName() { return ResourceLocation.parse("addon:structure/path"); }
            @Override public MachineControllerSpec controller() { return MachineControllerSpec.defaultsFor(registryName()); }
            @Override public BlockArray pattern() { return new BlockArray(Map.of(
                    BlockPos.ZERO, new BlockPredicate.OfBlock(Blocks.STONE),
                    new BlockPos(1, 0, 0), new BlockPredicate.OfBlock(Blocks.STONE))); }
        };
        var recipe = new MachineStructureEmiRecipe(MachineEmiPlugin.structureCategory(EmiStack.EMPTY), MachineStructureDisplay.from(machine));
        assertThat(recipe.getId()).isEqualTo(ResourceLocation.parse("mmcr:/structure/addon/structure/path"));
        assertThat(recipe.getInputs()).singleElement().satisfies(input -> assertThat(input.getAmount()).isEqualTo(2));
        assertThat(recipe.supportsRecipeTree()).isFalse();
        assertThat(recipe.hideCraftable()).isTrue();
        assertThat(recipe.getOutputs()).singleElement().satisfies(output -> {
            assertThat(output.getItemStack().getItem()).isSameAs(ModItems.BLUEPRINT.get());
            assertThat(output.getItemStack().get(ModDataComponents.BLUEPRINT_MACHINE.get())).isEqualTo(machine.registryName());
        });
        recipe.getInputs().getFirst().setAmount(100);
        assertThat(recipe.getInputs().getFirst().getAmount()).isEqualTo(2);
    }

    @Test void allInputsRemainInCostWhenViewportCannotShowThem() {
        var inputs = IntStream.range(0, 80).mapToObj(i ->
                new MachineIngredient.ItemIngredient(Ingredient.of(Items.IRON_INGOT), i + 1)).toList();
        var recipe = recipe("mmcr:many_inputs", inputs, List.of(new ItemStack(Items.DIAMOND)));
        assertThat(recipe.getInputs()).hasSize(80);
        assertThat(recipe.getInputs().getLast().getAmount()).isEqualTo(80);
        assertThat(recipe.supportsRecipeTree()).isTrue();
        recipe.getInputs().getFirst().setAmount(999);
        assertThat(recipe.getInputs().getFirst().getAmount()).isEqualTo(1);
    }

    @Test void nonConsumedInputsStayQueryableWithTheirRemainders() {
        var recipe = recipe("mmcr:keep", List.of(new MachineIngredient.ItemIngredient(
                Ingredient.of(Items.IRON_INGOT), 3, DataComponentPredicateSet.EMPTY, 0F)), List.of(new ItemStack(Items.DIAMOND)));
        assertThat(recipe.getInputs()).singleElement().satisfies(input -> {
            assertThat(input.getAmount()).isEqualTo(3);
            assertThat(input.getEmiStacks().getFirst().getRemainder().getAmount()).isEqualTo(3);
        });
        assertThat(recipe.supportsRecipeTree()).isTrue();
    }

    @Test void multiCandidateGetterAndSlotMutationsCannotChangeOwnedRecipeOrWidget() {
        var components = new DataComponentPredicateSet(Map.of(DataComponents.REPAIR_COST,
                ComponentPredicate.exact(new Dynamic<>(JsonOps.INSTANCE, new JsonPrimitive(3)))));
        for (float consumeChance : new float[]{0F, 0.25F}) {
            var recipe = recipe("mmcr:owned_alternatives", List.of(new MachineIngredient.ItemIngredient(
                    Ingredient.of(Items.IRON_INGOT, Items.GOLD_INGOT), 3, components, consumeChance)),
                    List.of(new ItemStack(Items.DIAMOND)));
            var widget = new MachineRecipeEmiWidget(recipe, 168, 80, row -> 10);
            var slot = widget.slots().getFirst();
            var exposed = recipe.getInputs().getFirst();
            var separate = recipe.getInputs().getFirst();
            assertThat(exposed).isInstanceOf(ListEmiIngredient.class);
            assertThat(exposed.getEmiStacks()).hasSize(2);
            for (int i = 0; i < 2; i++) {
                assertThat(exposed.getEmiStacks().get(i)).isNotSameAs(separate.getEmiStacks().get(i));
            }

            mutateCandidates(exposed);
            assertOwnedAlternatives(recipe.getInputs().getFirst(), consumeChance);
            assertOwnedAlternatives(separate, consumeChance);
            assertOwnedAlternatives(slot.getStack(), consumeChance);
            assertThat(slot.quantityText()).isEqualTo("3");
            assertThat(slot.tooltipText()).contains(Component.translatable("jei.mmcr.machine_recipe.item_count", "3"));
            assertThat(slot.entry().amount()).isEqualTo(3);
            assertThat(slot.entry().chance()).isEqualTo(consumeChance);

            // An EMI query receives an owned copy too, not the widget's render/tooltip candidates.
            mutateCandidates(slot.getStack());
            assertOwnedAlternatives(slot.getStack(), consumeChance);
            assertOwnedAlternatives(recipe.getInputs().getFirst(), consumeChance);

            mutateCandidates(recipe.resources().getFirst().ingredient());
            var rebuilt = new MachineRecipeEmiWidget(recipe, 168, 80, row -> 10);
            assertOwnedAlternatives(rebuilt.slots().getFirst().getStack(), consumeChance);
            assertOwnedAlternatives(recipe.getInputs().getFirst(), consumeChance);
            assertThat(recipe.data().entries().getFirst().amount()).isEqualTo(3);
            assertThat(recipe.data().entries().getFirst().items()).allSatisfy(candidate -> {
                assertThat(candidate.getCount()).isEqualTo(3);
                assertThat(candidate.get(DataComponents.REPAIR_COST)).isEqualTo(3);
            });
            assertThat(recipe.supportsRecipeTree()).isTrue();
        }
    }

    @Test void deepCopyKeepsNestedWrapperAndCandidateAmountsChancesAndComparisons() {
        ItemStack iron = new ItemStack(Items.IRON_INGOT);
        iron.set(DataComponents.REPAIR_COST, 3);
        var first = EmiStack.of(iron, 4).setChance(0.2F).comparison(Comparison.compareComponents())
                .setRemainder(EmiStack.of(Items.BUCKET).setAmount(2).setChance(0.3F));
        var second = EmiStack.of(Items.GOLD_INGOT).setAmount(9).setChance(0.6F);
        var nested = new ListEmiIngredient(List.of(first, second), 11).setChance(0.4F);
        var original = new ListEmiIngredient(List.of(nested, new SourceEmiStack(7)), 23).setChance(0.8F);
        var copied = (ListEmiIngredient) EmiIngredientAdapters.deepCopy(original);
        var copiedNested = (ListEmiIngredient) copied.getIngredients().getFirst();

        assertThat(copied.getAmount()).isEqualTo(23);
        assertThat(copied.getChance()).isEqualTo(0.8F);
        assertThat(copiedNested.getAmount()).isEqualTo(11);
        assertThat(copiedNested.getChance()).isEqualTo(0.4F);
        assertThat(copied.getEmiStacks()).extracting(EmiStack::getAmount).containsExactly(4L, 9L, 7L);
        assertThat(copied.getEmiStacks()).extracting(EmiStack::getChance).containsExactly(0.2F, 0.6F, 1F);
        var copiedFirst = copied.getEmiStacks().getFirst();
        assertThat(copiedFirst.getRemainder()).isNotSameAs(first.getRemainder());
        assertThat(copiedFirst.getRemainder().getAmount()).isEqualTo(2);
        assertThat(copiedFirst.getRemainder().getChance()).isEqualTo(0.3F);
        ItemStack different = iron.copy();
        different.set(DataComponents.REPAIR_COST, 4);
        assertThat(copiedFirst.isEqual(EmiStack.of(iron))).isTrue();
        assertThat(copiedFirst.isEqual(EmiStack.of(different))).isFalse();

        copiedNested.setAmount(100).setChance(0.9F);
        copiedFirst.getRemainder().setAmount(101);
        copiedFirst.setAmount(102).setChance(0.95F).comparison(Comparison.of((a, b) -> true));
        assertThat(nested.getAmount()).isEqualTo(11);
        assertThat(nested.getChance()).isEqualTo(0.4F);
        assertThat(first.getAmount()).isEqualTo(4);
        assertThat(first.getChance()).isEqualTo(0.2F);
        assertThat(first.getRemainder().getAmount()).isEqualTo(2);
        assertThat(first.isEqual(EmiStack.of(different))).isFalse();
    }

    private static void mutateCandidates(EmiIngredient ingredient) {
        var candidates = ingredient.getEmiStacks();
        // Mutate the existing remainder on one candidate and replace it on the other.
        if (!candidates.getFirst().getRemainder().isEmpty()) {
            candidates.getFirst().getRemainder().setAmount(998).setChance(0.1F);
        }
        candidates.getLast().setRemainder(EmiStack.of(Items.BUCKET).setAmount(997));
        candidates.forEach(candidate -> candidate.setAmount(999).setChance(0.9F)
                .comparison(Comparison.of((a, b) -> true)));
        ingredient.setAmount(996).setChance(0.8F);
    }

    private static void assertOwnedAlternatives(EmiIngredient ingredient, float consumeChance) {
        float nativeChance = consumeChance == 0F ? 1F : consumeChance;
        assertThat(ingredient.getAmount()).isEqualTo(3);
        assertThat(ingredient.getChance()).isEqualTo(nativeChance);
        assertThat(ingredient.getEmiStacks()).extracting(EmiStack::getKey).containsExactly(Items.IRON_INGOT, Items.GOLD_INGOT);
        assertThat(ingredient.getEmiStacks()).allSatisfy(candidate -> {
            assertThat(candidate.getAmount()).isEqualTo(3);
            assertThat(candidate.getChance()).isEqualTo(nativeChance);
            assertThat(candidate.get(DataComponents.REPAIR_COST)).isEqualTo(3);
            if (consumeChance == 0F) {
                assertThat(candidate.getRemainder()).isNotSameAs(candidate);
                assertThat(candidate.getRemainder().getAmount()).isEqualTo(3);
                assertThat(candidate.getRemainder().getChance()).isEqualTo(1F);
                assertThat(candidate.getRemainder().isEqual(candidate)).isTrue();
            } else assertThat(candidate.getRemainder().isEmpty()).isTrue();
            ItemStack matching = new ItemStack(candidate.getItemStack().getItem());
            matching.set(DataComponents.REPAIR_COST, 3);
            matching.set(DataComponents.CUSTOM_NAME, Component.literal("unconstrained"));
            assertThat(candidate.isEqual(EmiStack.of(matching))).isTrue();
            matching.set(DataComponents.REPAIR_COST, 4);
            assertThat(candidate.isEqual(EmiStack.of(matching))).isFalse();
        });
    }

    @Test void zeroChanceOutputIsVisibleButNeverAProductionSource() {
        var recipe = recipe("mmcr:zero", List.of(), List.of(
                MachineRequirement.itemOutput(new ItemStack(Items.DIAMOND), 0F)));
        assertThat(recipe.getOutputs()).isEmpty();
        assertThat(recipe.resources()).hasSize(1);
        assertThat(recipe.supportsRecipeTree()).isFalse();
    }

    @Test void unavailableIngredientsAndManaKeepTheirDetailsAndDisableTree() {
        var empty = recipe("mmcr:empty", List.of(new MachineIngredient.ItemIngredient(Ingredient.EMPTY, 2)),
                List.of(new ItemStack(Items.DIAMOND)));
        assertThat(empty.supportsRecipeTree()).isFalse();
        assertThat(empty.details()).anySatisfy(row -> assertThat(row.text()).isEqualTo(
                Component.translatable("emi.mmcr.recipe.empty_ingredient")));
        try (var ignored = RequirementHandlerRegistry.openTestScope()) {
            RequirementHandlerRegistry.register(ManaRequirement.TYPE);
            var mana = recipe("mmcr:mana", List.of(ManaRequirement.input(20)), List.of(new ItemStack(Items.DIAMOND)));
            assertThat(mana.supportsRecipeTree()).isFalse();
            assertThat(mana.getInputs()).isEmpty();
            assertThat(mana.details()).anySatisfy(row -> assertThat(row.text()).isEqualTo(
                    Component.translatable("jei.mmcr.machine_recipe.mana_input", "20")));
        }
    }

    @Test void unknownRequirementKeepsItsTypeAndExplanationWithoutInventedIO() {
        try (var ignored = RequirementHandlerRegistry.openTestScope()) {
            RequirementHandlerRegistry.register(UnknownRequirement.TYPE);
            var recipe = recipe("mmcr:unknown", List.of(new UnknownRequirement(IOType.INPUT)), List.of(new ItemStack(Items.DIAMOND)));
            assertThat(recipe.getInputs()).isEmpty();
            assertThat(recipe.supportsRecipeTree()).isFalse();
            assertThat(recipe.details()).anySatisfy(row -> assertThat(row.text()).isEqualTo(
                    Component.translatable("emi.mmcr.recipe.unknown_resource", UnknownRequirement.TYPE.id().toString())));
            assertThat(recipe.details()).anySatisfy(row -> assertThat(row.text()).isEqualTo(
                    Component.literal(UnknownRequirement.TYPE.id().toString())));
        }
    }

    @Test void informationOwnsNestedComponentsAndLists() {
        var data = recipe("mmcr:information", List.of(), List.of()).data();
        var nested = Component.literal("captured");
        var information = new ArrayList<Component>(List.of(Component.translatable("test.info", nested)));
        var recipe = new MachineEmiRecipe(category(), data, information);
        nested.append(" changed");
        information.clear();
        assertThat(recipe.information()).hasSize(1);
        assertThat(recipe.information().getFirst()).isEqualTo(Component.translatable("test.info", Component.literal("captured")));
        assertThat(recipe.details()).filteredOn(row -> row.text().equals(recipe.information().getFirst())).hasSize(1);
    }

    static MachineEmiRecipe recipe(String id, List<?> inputs, List<?> outputs) {
        var data = MachineRecipeData.from(RecipeTestSupport.create(ResourceLocation.parse(id), MMCR.id("emi_pool"),
                20, inputs, outputs), null);
        return new MachineEmiRecipe(category(), data, List.of());
    }

    static EmiRecipeCategory category() { return new EmiRecipeCategory(MMCR.id("recipe_pool/emi_pool"), EmiStack.EMPTY); }

    /** A registered resource with no EMI representation. @author howxu <dev@howxu.cn> */
    private record UnknownRequirement(IOType io) implements MachineRequirement {
        private static final MapCodec<UnknownRequirement> CODEC = RecipeModifier.IO_TYPE_CODEC.fieldOf("io")
                .xmap(UnknownRequirement::new, UnknownRequirement::io);
        private static final RequirementType<UnknownRequirement> TYPE = new RequirementType.Definition<>(
                MMCR.id("unknown_emi_resource"), CODEC, (requirement, capabilities, context) -> null,
                requirement -> new UnknownRequirement(requirement.io()));
        @Override public RequirementType<UnknownRequirement> type() { return TYPE; }
    }
}
