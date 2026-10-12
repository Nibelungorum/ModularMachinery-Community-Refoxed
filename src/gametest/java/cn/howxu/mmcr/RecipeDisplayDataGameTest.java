package cn.howxu.mmcr;

import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.component.ComponentPredicate;
import cn.howxu.mmcr.api.recipe.component.DataComponentPredicateSet;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.IOType;
import cn.howxu.mmcr.api.recipe.requirement.FluidRequirement;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.client.recipe.MachineRecipeData;
import cn.howxu.mmcr.client.recipe.RecipeDisplayEntry;
import cn.howxu.mmcr.compat.mekanism.loaded.LoadedChemicalRequirement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.common.crafting.DataComponentIngredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.DataComponentFluidIngredient;
import net.neoforged.fml.ModList;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/** Direct server-runtime assertions for registry-dependent neutral display data.
 * @author howxu <dev@howxu.cn>
 */
public final class RecipeDisplayDataGameTest {
    private RecipeDisplayDataGameTest() { }

    public static void displayCopiesOutputComponents(GameTestHelper helper) {
        ItemStack original = new ItemStack(Items.DIAMOND);
        original.set(DataComponents.CUSTOM_NAME, Component.literal("display-owner"));
        MachineRecipe recipe = recipe("display_components", List.of(),
                List.of(new MachineOutput.ItemOutput(original, 0.5F)));
        RecipeDisplayEntry output = MachineRecipeData.from(recipe, helper.getLevel().registryAccess()).entries().getFirst();
        ItemStack displayed = output.items().getFirst();
        helper.assertTrue(ItemStack.isSameItemSameComponents(original, displayed),
                "Display must retain canonical output components");
        helper.assertTrue(output.io() == IOType.OUTPUT && output.chance() == 0.5F,
                "Canonical output direction and chance must survive the display conversion");
        displayed.remove(DataComponents.CUSTOM_NAME);
        helper.assertTrue(output.items().getFirst().has(DataComponents.CUSTOM_NAME),
                "Changing a returned display stack must not mutate the snapshot");
        helper.succeed();
    }

    public static void displayResolvesRegistryExactComponents(GameTestHelper helper) {
        JsonObject levels = new JsonObject();
        levels.addProperty("minecraft:sharpness", 4);
        JsonObject enchantments = new JsonObject();
        enchantments.add("levels", levels);
        DataComponentPredicateSet components = new DataComponentPredicateSet(Map.of(
                DataComponents.ENCHANTMENTS, ComponentPredicate.exact(enchantments)));
        ItemRequirement input = new ItemRequirement(IOType.INPUT, Ingredient.of(Items.DIAMOND_SWORD, Items.IRON_SWORD),
                2, ItemStack.EMPTY, 1F, components, 0.25F);
        ItemRequirement output = new ItemRequirement(IOType.OUTPUT, null, 0,
                new ItemStack(Items.GOLDEN_SWORD), 0.5F, components, 1F);
        MachineRecipeData data = MachineRecipeData.from(recipe("display_exact_registry", List.of(input, output), List.of()),
                helper.getLevel().registryAccess());
        var sharpness = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.SHARPNESS);
        for (RecipeDisplayEntry entry : data.entries()) {
            helper.assertTrue(entry.exactComponents(),
                    "Registry-dependent Exact constraints must be exportable using live display ops");
            for (ItemStack candidate : entry.items()) {
                helper.assertTrue(candidate.get(DataComponents.ENCHANTMENTS).getLevel(sharpness) == 4,
                        "All input alternatives and the output must retain the exact enchantment");
                helper.assertTrue(components.matches(candidate, data.componentOps()),
                        "The materialized candidate must satisfy the original constraint with the same ops");
            }
        }
        helper.assertTrue(!components.exactPatch().isPresent(),
                "Plain JSON exactPatch alone cannot resolve the registry-dependent enchantment");
        helper.assertTrue(data.entries().getFirst().items().size() == 2,
                "Resolving Exact components must retain every input alternative");
        helper.assertTrue(data.entries().getFirst().chance() == 0.25F,
                "Input consumption probability must survive registry-aware candidate resolution");
        helper.succeed();
    }

    public static void displayRetainsUnexportedConstraints(GameTestHelper helper) {
        DataComponentPredicateSet range = new DataComponentPredicateSet(Map.of(
                DataComponents.REPAIR_COST, ComponentPredicate.range(1, 4)));
        JsonObject levels = new JsonObject();
        levels.addProperty("missing:unregistered_enchantment", 1);
        JsonObject enchantments = new JsonObject();
        enchantments.add("levels", levels);
        DataComponentPredicateSet unresolved = new DataComponentPredicateSet(Map.of(
                DataComponents.ENCHANTMENTS, ComponentPredicate.exact(enchantments)));
        MachineRecipeData data = MachineRecipeData.from(recipe("display_unexported", List.of(
                new ItemRequirement(IOType.INPUT, Ingredient.of(Items.DIAMOND_SWORD), 1, ItemStack.EMPTY, 1F, range, 1F),
                new ItemRequirement(IOType.INPUT, Ingredient.of(Items.IRON_SWORD), 1, ItemStack.EMPTY, 1F, unresolved, 1F)
        ), List.of()), helper.getLevel().registryAccess());
        for (RecipeDisplayEntry entry : data.entries()) {
            helper.assertTrue(!entry.exactComponents(), "Partial and unresolved constraints must prevent export");
            helper.assertTrue(!entry.components().isEmpty() && entry.tooltip().size() >= 2,
                    "Unexportable constraints must retain both their predicates and full display explanation");
        }
        helper.succeed();
    }

    public static void displayPreservesInputPatchAndEmptyTags(GameTestHelper helper) {
        ItemStack named = new ItemStack(Items.DIAMOND_SWORD);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("ingredient-patch"));
        Ingredient ingredient = DataComponentIngredient.of(false, named);
        var missingTag = TagKey.create(Registries.ITEM, MMCR.id("missing_display_tag"));
        Ingredient missing = Ingredient.of(missingTag);
        Ingredient mixed = Ingredient.fromValues(Stream.of(new Ingredient.TagValue(missingTag),
                new Ingredient.ItemValue(new ItemStack(Items.BARRIER)), new Ingredient.TagValue(ItemTags.PLANKS)));
        ItemStack barrier = new ItemStack(Items.BARRIER);
        barrier.set(DataComponents.CUSTOM_NAME, Component.literal("Empty Tag: " + missingTag.location()));
        DataComponentPredicateSet components = new DataComponentPredicateSet(Map.of(
                DataComponents.CUSTOM_NAME, ComponentPredicate.exact(
                        ComponentSerialization.CODEC.encodeStart(JsonOps.INSTANCE, Component.literal("constraint")).getOrThrow())));
        MachineRecipeData data = MachineRecipeData.from(recipe("display_input_patch", List.of(
                new ItemRequirement(IOType.INPUT, ingredient, 3, ItemStack.EMPTY),
                new ItemRequirement(IOType.INPUT, missing, 1, ItemStack.EMPTY),
                new ItemRequirement(IOType.INPUT, mixed, 1, ItemStack.EMPTY),
                new ItemRequirement(IOType.INPUT, Ingredient.of(barrier), 1, ItemStack.EMPTY),
                new ItemRequirement(IOType.INPUT, missing, 1, ItemStack.EMPTY, 1F, components, 1F)
        ), List.of()), helper.getLevel().registryAccess());
        RecipeDisplayEntry first = data.entries().getFirst();
        helper.assertTrue(first.items().getFirst().has(DataComponents.CUSTOM_NAME),
                "Input candidate copying must not discard the ingredient's existing component patch");
        first.items().getFirst().remove(DataComponents.CUSTOM_NAME);
        helper.assertTrue(first.items().getFirst().has(DataComponents.CUSTOM_NAME)
                        && ingredient.getItems()[0].has(DataComponents.CUSTOM_NAME),
                "A display candidate must own its components independently of the source and returned copies");
        RecipeDisplayEntry empty = data.entries().get(1);
        helper.assertTrue(empty.items().isEmpty() && !empty.exactComponents(),
                "Empty tags must have no candidates and must not become exportable by vacuous matching");
        helper.assertTrue(((ItemRequirement) empty.requirement()).item().equals(missing),
                "Filtering display candidates must retain the original tag requirement");
        RecipeDisplayEntry alternatives = data.entries().get(2);
        helper.assertTrue(alternatives.items().stream().anyMatch(stack -> stack.is(Items.BARRIER))
                        && alternatives.items().stream().anyMatch(stack -> stack.is(Items.OAK_PLANKS)),
                "Filtering a missing tag must retain explicit barriers and populated tag alternatives");
        helper.assertTrue(alternatives.items().stream().allMatch(stack ->
                        (stack.is(Items.BARRIER) || stack.is(ItemTags.PLANKS)) && !stack.has(DataComponents.CUSTOM_NAME))
                        && alternatives.exactComponents(),
                "Mixed ingredients must export real alternatives without the empty-tag placeholder");
        RecipeDisplayEntry explicitBarrier = data.entries().get(3);
        helper.assertTrue(explicitBarrier.exactComponents() && explicitBarrier.items().size() == 1
                        && ItemStack.isSameItemSameComponents(barrier, explicitBarrier.items().getFirst()),
                "An explicit barrier must remain exportable even with the empty-tag placeholder's name");
        RecipeDisplayEntry constrainedEmpty = data.entries().getLast();
        helper.assertTrue(constrainedEmpty.items().isEmpty() && !constrainedEmpty.exactComponents()
                        && constrainedEmpty.components().equals(components) && constrainedEmpty.tooltip().size() >= 2,
                "Empty tags with exact constraints must retain their predicates and explanation without fake candidates");
        helper.succeed();
    }

    public static void displayCopiesFluidComponentsRoundTrip(GameTestHelper helper) {
        FluidStack fluid = new FluidStack(Fluids.WATER, 1_234);
        fluid.set(DataComponents.CUSTOM_NAME, Component.literal("fluid-owner"));
        MachineRecipe original = recipe("display_fluid_patch", List.of(new FluidRequirement(IOType.INPUT,
                DataComponentFluidIngredient.of(false, fluid), 250, FluidStack.EMPTY)),
                List.of(new MachineOutput.FluidOutput(fluid, 0.5F)));
        var ops = helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE);
        MachineRecipe decoded = MachineRecipe.CODEC.codec().parse(ops,
                MachineRecipe.CODEC.codec().encodeStart(ops, original).getOrThrow()).getOrThrow();
        MachineRecipeData data = MachineRecipeData.from(decoded, helper.getLevel().registryAccess());
        for (RecipeDisplayEntry entry : data.entries()) {
            FluidStack candidate = entry.fluids().getFirst();
            helper.assertTrue(FluidStack.isSameFluidSameComponents(fluid, candidate),
                    "Input and output fluid component identity must survive recipe codec and display conversion");
            candidate.remove(DataComponents.CUSTOM_NAME);
            candidate.setAmount(1);
            helper.assertTrue(entry.fluids().getFirst().has(DataComponents.CUSTOM_NAME),
                    "Mutating returned fluids must not alter the display snapshot");
        }
        helper.assertTrue(data.entries().getFirst().amount() == 250 && data.entries().getLast().amount() == 1_234,
                "Amounts must remain independent of component identity across the round trip");
        helper.succeed();
    }

    public static void displayResolvesChemicalOutput(GameTestHelper helper) {
        if (!ModList.get().isLoaded("mekanism")) {
            helper.succeed();
            return;
        }
        ResourceLocation hydrogen = ResourceLocation.parse("mekanism:hydrogen");
        MachineRecipeData data = MachineRecipeData.from(recipe("display_chemical_output", List.of(
                LoadedChemicalRequirement.output(hydrogen, 5_000_000_000L, 0.25F)), List.of()),
                helper.getLevel().registryAccess());
        RecipeDisplayEntry entry = data.entries().getFirst();
        helper.assertTrue(entry.io() == IOType.OUTPUT && entry.kind() == RecipeDisplayEntry.Kind.CHEMICAL,
                "Chemical outputs must remain in the neutral resource list");
        helper.assertTrue(entry.chemicals().equals(List.of(hydrogen)) && entry.amount() == 5_000_000_000L,
                "Loaded native lookup must return the actual chemical ID without narrowing its long amount");
        helper.assertTrue(entry.chance() == 0.25F, "Chemical output chance must survive lookup");
        helper.succeed();
    }

    private static MachineRecipe recipe(String id, List<MachineRequirement> requirements, List<MachineOutput> outputs) {
        return MachineRecipe.fromCanonical(MMCR.id(id), MMCR.id("test_cube"), 20, requirements, outputs,
                List.of(), 0, 1, false, false, false, Set.of());
    }
}
