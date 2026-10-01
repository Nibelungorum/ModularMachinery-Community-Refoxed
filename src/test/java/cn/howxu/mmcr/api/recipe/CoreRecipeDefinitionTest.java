package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.recipe.component.ComponentPredicate;
import cn.howxu.mmcr.api.recipe.component.DataComponentPredicateSet;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.IOType;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.api.recipe.requirement.FluidRequirement;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.registration.StructureRegistration;
import cn.howxu.mmcr.internal.api.facade.recipe.RecipeAdapters;
import cn.howxu.mmcr.internal.registration.MachineRecipeConverter;
import cn.howxu.mmcr.test.TestBootstrap;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Verifies single-source core recipe declarations and derived resource views.
 * @author howxu <dev@howxu.cn>
 */
class CoreRecipeDefinitionTest {
    @BeforeAll
    static void bootstrap() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void canonical_requirements_drive_views_and_compilation_with_metadata_and_copies() {
        var components = new DataComponentPredicateSet(Map.of(DataComponents.DAMAGE,
                ComponentPredicate.exact(new Dynamic<>(JsonOps.INSTANCE, new JsonPrimitive(2)))));
        var stack = new ItemStack(Items.GOLD_NUGGET, 3);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("canonical output"));
        var fluid = new FluidStack(Fluids.WATER, 250);
        List<MachineRequirement> values = List.of(
                new ItemRequirement(IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 2, ItemStack.EMPTY,
                        1F, List.of("item_in"), components, 0.25F),
                new ItemRequirement(IOType.OUTPUT, null, 0, stack, 0.5F, List.of("item_out"), components, 1F),
                new FluidRequirement(IOType.INPUT, FluidIngredient.of(Fluids.WATER), 100, FluidStack.EMPTY,
                        1F, List.of("fluid_in"), 0.75F),
                new FluidRequirement(IOType.OUTPUT, null, 0, fluid, 0.5F, List.of("fluid_out"), 1F),
                new EnergyRequirement(IOType.OUTPUT, 40, List.of("energy_out")),
                new EnergyRequirement(IOType.INPUT, 20, List.of("energy_in")));
        var builder = MachineRecipeBuilder.recipe(MMCR.id("canonical_definition")).recipePool(MMCR.id("pool"));
        values.forEach(builder::requirement);
        var definition = builder.build();
        var encoded = definition.requirements().stream().map(value ->
                MachineRequirement.CODEC.encodeStart(JsonOps.INSTANCE, value).getOrThrow()).toList();
        stack.setCount(1);
        stack.remove(DataComponents.CUSTOM_NAME);
        fluid.setAmount(1);
        definition.itemOutputs().getFirst().stack().setCount(1);
        definition.fluidOutputs().getFirst().stack().setAmount(1);
        assertThat(definition.itemInputs()).singleElement().satisfies(value -> {
            assertThat(value.tags()).containsExactly("item_in");
            assertThat(value.components()).isEqualTo(components);
            assertThat(value.consumeChance()).isEqualTo(0.25F);
        });
        assertThat(definition.fluidInputs().getFirst().consumeChance()).isEqualTo(0.75F);
        assertThat(definition.energyInputs().getFirst().io()).isEqualTo(IOType.INPUT);
        assertThat(definition.energyOutputs().getFirst().io()).isEqualTo(IOType.OUTPUT);
        assertThat(definition.itemOutputs().getFirst().stack().getCount()).isEqualTo(3);
        assertThat(definition.itemOutputs().getFirst().stack().get(DataComponents.CUSTOM_NAME))
                .isEqualTo(Component.literal("canonical output"));
        assertThat(definition.fluidOutputs().getFirst().stack().getAmount()).isEqualTo(250);
        var recipe = MachineRecipeConverter.toRecipe(definition,
                new StructureRegistration.Snapshot(Map.of(), Map.of(), Map.of(), Map.of()));
        assertThat(recipe.requirements()).extracting(value -> MachineRequirement.CODEC.encodeStart(JsonOps.INSTANCE, value).getOrThrow())
                .containsExactlyElementsOf(encoded);
        assertThat(RecipeAdapters.wrap(definition).itemOutputs().getFirst().chance()).isEqualTo(0.5F);
        assertThatThrownBy(() -> definition.itemInputs().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void zero_canonical_values_survive_build_and_views_while_scalar_energy_stays_strict() {
        var builder = MachineRecipeBuilder.recipe(MMCR.id("zero_canonical")).recipePool(MMCR.id("pool"))
                .requirement(new ItemRequirement(IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 0,
                        ItemStack.EMPTY, 1F, List.of("zero")))
                .requirement(new ItemRequirement(IOType.OUTPUT, null, 0, ItemStack.EMPTY, 0F, List.of("zero")))
                .requirement(new FluidRequirement(IOType.INPUT, FluidIngredient.of(Fluids.WATER), 0,
                        FluidStack.EMPTY, 1F, List.of("zero")))
                .requirement(new FluidRequirement(IOType.OUTPUT, null, 0, FluidStack.EMPTY, 0F, List.of("zero")))
                .requirement(new EnergyRequirement(IOType.INPUT, 0, List.of("zero")))
                .requirement(new EnergyRequirement(IOType.OUTPUT, 0, List.of("zero")));
        var definition = builder.build();
        var view = RecipeAdapters.wrap(definition);
        assertThat(view.itemInputs().getFirst().count()).isZero();
        assertThat(view.itemOutputs().getFirst().stack().isEmpty()).isTrue();
        assertThat(view.fluidInputs().getFirst().amount()).isZero();
        assertThat(view.fluidOutputs().getFirst().stack().isEmpty()).isTrue();
        assertThat(view.energyInputs().getFirst().fePerTick()).isZero();
        assertThat(view.energyOutputs().getFirst().fePerTick()).isZero();
        assertThat(definition.requirements()).allSatisfy(value -> assertThat(value.tags()).containsExactly("zero"));
        var compiled = MachineRecipeConverter.toRecipe(definition,
                new StructureRegistration.Snapshot(Map.of(), Map.of(), Map.of(), Map.of()));
        assertThat(compiled.requirements()).extracting(MachineRequirement::io)
                .containsExactly(IOType.INPUT, IOType.OUTPUT, IOType.INPUT, IOType.OUTPUT, IOType.INPUT, IOType.OUTPUT);
        assertThatThrownBy(() -> builder.inputEnergy(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> builder.outputEnergy(-1)).isInstanceOf(IllegalArgumentException.class);
    }
}
