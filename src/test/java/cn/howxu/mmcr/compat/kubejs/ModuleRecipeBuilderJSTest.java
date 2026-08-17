package cn.howxu.mmcr.compat.kubejs;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.api.machine.MachineRegistration;
import cn.howxu.mmcr.api.recipe.MachineIngredient;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.SmartInterfaceRequirement;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author howxu <dev@howxu.cn>
 */
class ModuleRecipeBuilderJSTest {

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @AfterEach
    void cleanup() {
        MachineDefinitions.clearForTesting();
        RecipeRegistry.clearForTesting();
    }

    @Test
    void builder_declares_required_hosts_with_stable_deduplication() {
        var builder = new MachineRecipeBuilderJS("mmcr:module_recipe")
                .requiredHost("mmcr:first")
                .requiredHosts("mmcr:second", "mmcr:first", "mmcr:third");

        assertThat(builder.requiredHostIds)
                .containsExactly(Identifier.parse("mmcr:first"), Identifier.parse("mmcr:second"), Identifier.parse("mmcr:third"));
    }

    @Test
    void build_preserves_required_host_declaration_order() {
        Identifier machineId = MMCR.id("module_machine");
        Identifier recipeId = MMCR.id("module_recipe");
        MachineDefinitions.register(MachineRegistration.builder(machineId).build());

        new MachineRecipeBuilderJS(recipeId)
                .machine(machineId.toString())
                .requiredHosts("mmcr:first", "mmcr:second", "mmcr:first", "mmcr:third")
                .build();

        assertThat(RecipeRegistry.getRecipe(recipeId).requiredHostIds())
                .containsExactly(Identifier.parse("mmcr:first"), Identifier.parse("mmcr:second"), Identifier.parse("mmcr:third"));
    }

    @Test
    void create_object_preserves_complete_public_recipe_values() {
        Identifier machineId = MMCR.id("module_machine");
        MachineDefinitions.register(MachineRegistration.builder(machineId).build());
        Items.DIAMOND.builtInRegistryHolder().bindComponents(DataComponentMap.EMPTY);
        Fluids.WATER.builtInRegistryHolder().bindComponents(DataComponentMap.EMPTY);
        var itemInput = new MachineIngredient.ItemIngredient(Ingredient.of(Items.IRON_INGOT), 2);
        var fluidInput = new MachineIngredient.FluidIngredient(FluidIngredient.of(Fluids.WATER), 500);
        var energyOutput = new MachineIngredient.EnergyIngredient(cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.IOType.OUTPUT, 20);
        var fluidOutput = new FluidStack(Fluids.WATER, 250);
        MachineRequirement requirement = SmartInterfaceRequirement.input("temperature", 25F);

        MachineRecipe recipe = new MachineRecipeBuilderJS("mmcr:kubejs_full_recipe")
                .machine(machineId.toString())
                .inputs(java.util.List.of(itemInput, fluidInput, energyOutput))
                .outputs(java.util.List.of(new ItemStack(Items.DIAMOND)))
                .fluidOutputs(java.util.List.of(fluidOutput))
                .requirements(java.util.List.of(requirement))
                .priority(7).maxThreads(3).cancelIfPerTickFails(true).allowPartialOutputs()
                .requiredHosts("mmcr:space_elevator")
                .createObject();

        assertThat(recipe.priority()).isEqualTo(7);
        assertThat(recipe.maxThreads()).isEqualTo(3);
        assertThat(recipe.inputs()).containsExactly(itemInput, fluidInput);
        assertThat(recipe.energyOutputs()).containsExactly(20);
        assertThat(recipe.outputs()).singleElement().satisfies(output -> assertThat(output.getItem()).isSameAs(Items.DIAMOND));
        assertThat(recipe.fluidOutputs()).singleElement().satisfies(output -> {
            assertThat(output.getFluid()).isSameAs(Fluids.WATER);
            assertThat(output.getAmount()).isEqualTo(250);
        });
        assertThat(recipe.requirements()).contains(requirement);
        assertThat(recipe.doesCancelRecipeOnPerTickFailure()).isTrue();
        assertThat(recipe.allowPartialOutputs()).isTrue();
        assertThat(recipe.requiredHostIds()).containsExactly(Identifier.parse("mmcr:space_elevator"));
    }

    @Test
    void create_object_rejects_negative_ingredient_counts() {
        Identifier machineId = MMCR.id("module_machine");
        MachineDefinitions.register(MachineRegistration.builder(machineId).build());

        assertThatThrownBy(() -> new MachineRecipeBuilderJS("mmcr:negative_input")
                .machine(machineId.toString())
                .addInput(new MachineIngredient.EnergyIngredient(-1))
                .createObject())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be negative");
    }

    @Test
    void direct_output_lists_preserve_non_negative_normalized_values() {
        Identifier machineId = MMCR.id("module_machine");
        MachineDefinitions.register(MachineRegistration.builder(machineId).build());
        Items.DIAMOND.builtInRegistryHolder().bindComponents(DataComponentMap.EMPTY);
        Fluids.WATER.builtInRegistryHolder().bindComponents(DataComponentMap.EMPTY);

        var itemOutput = new ItemStack(Items.DIAMOND, -1);
        var fluidOutput = new FluidStack(Fluids.WATER, -1);

        assertThat(itemOutput.getCount()).isZero();
        assertThat(fluidOutput.getAmount()).isZero();
        assertThat(new MachineRecipeBuilderJS("mmcr:negative_item_output")
                .machine(machineId.toString())
                .outputs(java.util.List.of(itemOutput))
                .createObject()
                .outputs()).singleElement().satisfies(ItemStack::isEmpty);
        assertThat(new MachineRecipeBuilderJS("mmcr:negative_fluid_output")
                .machine(machineId.toString())
                .fluidOutputs(java.util.List.of(fluidOutput))
                .createObject()
                .fluidOutputs()).singleElement().satisfies(FluidStack::isEmpty);
    }

}
