package cn.howxu.mmcr.internal.recipe;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.async.AsyncResourceValue;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.FluidRequirement;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.internal.capability.NativeAsyncResourceValues;
import cn.howxu.mmcr.test.TestBootstrap;
import com.mojang.datafixers.util.Either;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;
import net.neoforged.neoforge.common.crafting.IngredientType;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies membership indexing without using custom display values as an acceptance contract.
 * @author howxu <dev@howxu.cn>
 */
class RecipeSearchResourcesTest {
    @BeforeAll
    static void bootstrap() throws Exception { TestBootstrap.bootstrap(); }

    @Test
    void native_item_membership_never_decodes_unrelated_values_and_preserves_variants() {
        RecipeSearchResources resources = new RecipeSearchResources();
        var iron = NativeAsyncResourceValues.item(ItemResource.of(Items.IRON_INGOT));
        // A sentinel outside the membership must never reach native deserialization.
        var unrelated = new AsyncResourceValue(MMCR.id("unrelated"), "not serialized components");
        resources.add(ItemRequirement.TYPE.id(), iron);
        resources.add(ItemRequirement.TYPE.id(), unrelated);
        resources.add(FluidRequirement.TYPE.id(), unrelated);
        var requirement = new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 1, ItemStack.EMPTY);
        assertThat(resources.matching(requirement)).containsExactly(iron);
        assertThat(resources.all(FluidRequirement.TYPE.id())).containsExactly(unrelated);
        assertThat(resources.all(ItemRequirement.TYPE.id())).containsExactly(iron, unrelated);
    }

    @Test
    void simple_fluid_membership_does_not_decode_unrelated_values() {
        RecipeSearchResources resources = new RecipeSearchResources();
        var water = NativeAsyncResourceValues.fluid(FluidResource.of(Fluids.WATER));
        resources.add(FluidRequirement.TYPE.id(), water);
        resources.add(FluidRequirement.TYPE.id(), new AsyncResourceValue(MMCR.id("unrelated"), "not serialized components"));
        var requirement = new FluidRequirement(RecipeModifier.IOType.INPUT, FluidIngredient.of(Fluids.WATER),
                100, FluidStack.EMPTY, 1F, List.of());
        assertThat(resources.matching(requirement)).containsExactly(water);
    }

    @Test
    void unknown_holder_sets_use_live_membership_on_each_capture() {
        AtomicBoolean ironAllowed = new AtomicBoolean(true);
        HolderSet<Item> holders = new HolderSet.ListBacked<>() {
            @Override protected List<Holder<Item>> contents() {
                return List.of((ironAllowed.get() ? Items.IRON_INGOT : Items.GOLD_INGOT).builtInRegistryHolder());
            }
            @Override public boolean contains(Holder<Item> holder) { return contents().contains(holder); }
            @Override public boolean isBound() { return true; }
            @Override public Either<TagKey<Item>, List<Holder<Item>>> unwrap() { return Either.right(contents()); }
            @Override public Optional<TagKey<Item>> unwrapKey() { return Optional.empty(); }
        };
        var requirement = new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(holders), 1, ItemStack.EMPTY);
        var iron = NativeAsyncResourceValues.item(ItemResource.of(Items.IRON_INGOT));
        var gold = NativeAsyncResourceValues.item(ItemResource.of(Items.GOLD_INGOT));
        RecipeSearchResources first = new RecipeSearchResources();
        first.add(ItemRequirement.TYPE.id(), iron);
        first.add(ItemRequirement.TYPE.id(), gold);
        assertThat(first.matching(requirement)).containsExactly(iron);
        ironAllowed.set(false);
        RecipeSearchResources second = new RecipeSearchResources();
        second.add(ItemRequirement.TYPE.id(), iron);
        second.add(ItemRequirement.TYPE.id(), gold);
        assertThat(second.matching(requirement)).containsExactly(gold);
    }

    @Test
    void unbound_native_tags_keep_native_nonmatching_behavior() {
        var values = new RecipeSearchResources();
        values.add(ItemRequirement.TYPE.id(), NativeAsyncResourceValues.item(ItemResource.of(Items.IRON_INGOT)));
        var ingredient = Ingredient.of(HolderSet.emptyNamed(BuiltInRegistries.ITEM,
                TagKey.create(Registries.ITEM, MMCR.id("unbound_test_tag"))));
        assertThat(values.matching(new ItemRequirement(RecipeModifier.IOType.INPUT, ingredient, 1, ItemStack.EMPTY))).isEmpty();
    }

    @Test
    void a_custom_simple_ingredient_can_accept_an_undisplayed_item() {
        RecipeSearchResources resources = new RecipeSearchResources();
        var gold = NativeAsyncResourceValues.item(ItemResource.of(Items.GOLD_INGOT));
        resources.add(ItemRequirement.TYPE.id(), gold);
        Ingredient ingredient = new Ingredient(new ICustomIngredient() {
            public boolean test(ItemStack stack) { return stack.is(Items.GOLD_INGOT); }
            public Stream<Holder<Item>> items() { return Stream.of(Items.IRON_INGOT.builtInRegistryHolder()); }
            public boolean isSimple() { return true; }
            public IngredientType<?> getType() { return null; }
        });
        assertThat(resources.matching(new ItemRequirement(RecipeModifier.IOType.INPUT, ingredient, 1, ItemStack.EMPTY)))
                .containsExactly(gold);
    }
}
