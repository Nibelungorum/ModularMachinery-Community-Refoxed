package cn.howxu.mmcr.client.recipe;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.IOType;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerRegistry;
import cn.howxu.mmcr.api.recipe.requirement.RequirementType;
import cn.howxu.mmcr.compat.mekanism.loaded.LoadedChemicalRequirement;
import cn.howxu.mmcr.test.RecipeTestSupport;
import cn.howxu.mmcr.test.TestBootstrap;
import com.mojang.serialization.MapCodec;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Behaviour and ownership tests for the neutral recipe data boundary.
 * @author howxu <dev@howxu.cn>
 */
class MachineRecipeDataTest {
    @BeforeAll static void bootstrap() throws Exception { TestBootstrap.bootstrap(); }

    @Test void ordersSnapshotsByPriorityThenIdWithoutChangingRecipes() {
        MachineRecipe low = RecipeTestSupport.create(MMCR.id("a_low"), MMCR.id("display_pool"), 20,
                List.of(), List.of(), List.of(), 0, 1);
        MachineRecipe highZ = RecipeTestSupport.create(MMCR.id("z_high"), MMCR.id("display_pool"), 20,
                List.of(), List.of(), List.of(), 10, 1);
        MachineRecipe highA = highZ.withId(MMCR.id("a_high"));
        assertThat(List.of(low, highZ, highA).stream().map(recipe -> MachineRecipeData.from(recipe, null))
                .sorted(MachineRecipeData.ORDER).map(MachineRecipeData::recipeId))
                .containsExactly(highA.id(), highZ.id(), low.id());
        assertThat(low.priority()).isZero();
    }

    @Test void metadataOwnsNestedTextAndLists() {
        var nested = Component.literal("original");
        var text = Component.translatable("test.row", nested);
        var tooltips = new ArrayList<Component>(List.of(text));
        RecipeMetadataRow row = new RecipeMetadataRow(text, tooltips, List.of());
        nested.append(" source mutation");
        tooltips.clear();
        row.text().getSiblings().add(Component.literal("returned mutation"));
        assertThat(row.text().getSiblings()).isEmpty();
        var arguments = ((TranslatableContents) row.text().getContents()).getArgs();
        assertThat(((Component) arguments[0]).getString()).isEqualTo("original");
        assertThat(row.tooltip()).hasSize(1);
        assertThatThrownBy(() -> row.tooltip().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test void cachedRowsResolveConfigurationDependentTextWhenRead() {
        AtomicReference<String> current = new AtomicReference<>("first unit");
        RecipeMetadataRow row = RecipeMetadataRow.deferredText(() -> Component.literal(current.get()));
        assertThat(row.text().getString()).isEqualTo("first unit");
        current.set("changed unit");
        assertThat(row.text().getString()).isEqualTo("changed unit");
    }

    @Test void retainsLongEnergyAndImmutableSnapshotLists() {
        MachineRecipe recipe = MachineRecipe.fromCanonical(MMCR.id("display_long"), MMCR.id("display_pool"), 20,
                List.of(new EnergyRequirement(IOType.INPUT, Long.MAX_VALUE)), List.of(), List.of(),
                0, 1, false, false, false, Set.of());
        MachineRecipeData data = MachineRecipeData.from(recipe, null);
        assertThat(((EnergyRequirement) data.requirements().getFirst()).fePerTick()).isEqualTo(Long.MAX_VALUE);
        assertThatThrownBy(() -> data.entries().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> data.metadata().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test void unknownInputAndOutputRemainVisibleAsText() {
        try (var ignored = RequirementHandlerRegistry.openTestScope()) {
            RequirementHandlerRegistry.register(UnknownRequirement.TYPE);
            MachineRecipe recipe = MachineRecipe.fromCanonical(MMCR.id("unknown_display"), MMCR.id("display_pool"), 20,
                    List.of(new UnknownRequirement(IOType.INPUT), new UnknownRequirement(IOType.OUTPUT)),
                    List.of(), List.of(), 0, 1, false, false, false, Set.of());
            assertThat(MachineRecipeData.from(recipe, null).entries()).hasSize(2).allSatisfy(entry -> {
                assertThat(entry.kind()).isEqualTo(RecipeDisplayEntry.Kind.TEXT);
                assertThat(entry.tooltip()).singleElement().satisfies(text ->
                        assertThat(text.getString()).isEqualTo(UnknownRequirement.TYPE.id().toString()));
            }).extracting(RecipeDisplayEntry::io).containsExactly(IOType.INPUT, IOType.OUTPUT);
        }
    }

    @Test void chemicalOutputRetainsLongAmountAndChanceWithoutLoadedLookup() {
        try (var ignored = RequirementHandlerRegistry.openTestScope()) {
            RequirementHandlerRegistry.register(LoadedChemicalRequirement.TYPE);
            MachineRecipe recipe = MachineRecipe.fromCanonical(MMCR.id("chemical_display"), MMCR.id("display_pool"), 20,
                    List.of(LoadedChemicalRequirement.output(MMCR.id("unavailable_chemical"), 5_000_000_000L, 0.25F)),
                    List.of(), List.of(), 0, 1, false, false, false, Set.of());
            assertThat(MachineRecipeData.from(recipe, null).entries()).singleElement().satisfies(entry -> {
                assertThat(entry.io()).isEqualTo(IOType.OUTPUT);
                assertThat(entry.kind()).isEqualTo(RecipeDisplayEntry.Kind.CHEMICAL);
                assertThat(entry.amount()).isEqualTo(5_000_000_000L);
                assertThat(entry.chance()).isEqualTo(0.25F);
                assertThat(entry.chemicals()).isEmpty();
                assertThat(entry.exactComponents()).isFalse();
            });
        }
    }

    @Test void returnedCandidatesCannotChangeTheSnapshotOrCanonicalRequirement() {
        MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("owned_candidates"), MMCR.id("display_pool"), 20,
                List.of(new ItemRequirement(IOType.INPUT, Ingredient.of(Items.COAL), 3, ItemStack.EMPTY)), List.of());
        RecipeDisplayEntry entry = MachineRecipeData.from(recipe, null).entries().getFirst();
        entry.items().getFirst().setCount(64);
        assertThat(entry.items().getFirst().getCount()).isEqualTo(3);
        assertThat(((ItemRequirement) recipe.requirements().getFirst()).count()).isEqualTo(3);
        assertThatThrownBy(() -> entry.items().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    /** A canonically registered extension with no viewer adapter. @author howxu <dev@howxu.cn> */
    private record UnknownRequirement(IOType io) implements MachineRequirement {
        private static final MapCodec<UnknownRequirement> CODEC = RecipeModifier.IO_TYPE_CODEC.fieldOf("io")
                .xmap(UnknownRequirement::new, UnknownRequirement::io);
        private static final RequirementType<UnknownRequirement> TYPE = new RequirementType.Definition<>(
                MMCR.id("unknown_display_resource"), CODEC, (requirement, capabilities, context) -> null,
                requirement -> new UnknownRequirement(requirement.io()));
        @Override public RequirementType<UnknownRequirement> type() { return TYPE; }
    }
}
