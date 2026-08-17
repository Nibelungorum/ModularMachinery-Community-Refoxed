package cn.howxu.mmcr.compat.kubejs;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.api.machine.MachineRegistration;
import cn.howxu.mmcr.api.recipe.MachineIngredient;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.recipe.requirement.FluidRequirement;
import cn.howxu.mmcr.api.recipe.requirement.SmartInterfaceRequirement;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import com.google.gson.JsonParser;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

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

    @Test
    void builder_preserves_component_tag_inputs_and_explicit_requirements() {
        Identifier machineId = MMCR.id("module_machine");
        MachineDefinitions.register(MachineRegistration.builder(machineId).build());
        Items.EMERALD.builtInRegistryHolder().bindComponents(DataComponentMap.EMPTY);
        Fluids.LAVA.builtInRegistryHolder().bindComponents(DataComponentMap.EMPTY);

        var recipe = new MachineRecipeBuilderJS("mmcr:component_tag_recipe")
                .machine(machineId.toString())
                .tagInputWithComponents("minecraft:planks", 2, JsonParser.parseString("""
                        {"minecraft:custom_name":{"text":"Validated"}}
                        """), 0.5F)
                .addRequirement(new KubeJSApi().itemOutputRequirement("minecraft:emerald", 1, 0.25F))
                .addRequirement(new KubeJSApi().fluidOutputRequirement("minecraft:lava", 250, 0.5F))
                .createObject();

        var input = (MachineIngredient.ItemIngredient) recipe.inputs().getFirst();
        assertThat(input.count()).isEqualTo(2);
        assertThat(input.consumeChance()).isEqualTo(0.5F);
        assertThat(input.components().isEmpty()).isFalse();
        assertThat(recipe.requirements()).hasSize(3);
        assertThat(recipe.requirements().stream().filter(ItemRequirement.class::isInstance).map(ItemRequirement.class::cast)
                .filter(requirement -> requirement.io() == cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.IOType.OUTPUT).toList()).singleElement().satisfies(requirement -> {
            assertThat(requirement.stack().getItem()).isSameAs(Items.EMERALD);
            assertThat(requirement.chance()).isEqualTo(0.25F);
        });
        assertThat(recipe.requirements().stream().filter(FluidRequirement.class::isInstance).map(FluidRequirement.class::cast).toList()).singleElement().satisfies(requirement -> {
            assertThat(requirement.stack().getFluid()).isSameAs(Fluids.LAVA);
            assertThat(requirement.stack().getAmount()).isEqualTo(250);
            assertThat(requirement.chance()).isEqualTo(0.5F);
        });
    }

    @Test
    void builder_maps_parallelized_and_keeps_false_as_the_compatible_default() {
        Identifier machineId = MMCR.id("module_machine");
        MachineDefinitions.register(MachineRegistration.builder(machineId).build());

        assertThat(new MachineRecipeBuilderJS("mmcr:serial")
                .machine(machineId.toString()).createObject().isParallelized()).isFalse();
        assertThat(new MachineRecipeBuilderJS("mmcr:parallel")
                .machine(machineId.toString()).parallelized().createObject().isParallelized()).isTrue();
        assertThat(new MachineRecipeBuilderJS("mmcr:explicit_serial")
                .machine(machineId.toString()).parallelized(false).createObject().isParallelized()).isFalse();
    }

    @Test
    void explicit_requirements_can_disable_automatic_legacy_field_derivation() {
        Identifier machineId = MMCR.id("module_machine");
        MachineDefinitions.register(MachineRegistration.builder(machineId).build());
        var iron = new MachineIngredient.ItemIngredient(Ingredient.of(Items.IRON_INGOT), 1);
        var apple = new MachineIngredient.ItemIngredient(Ingredient.of(Items.APPLE), 1);

        var recipe = new MachineRecipeBuilderJS("mmcr:legacy_input_explicit_requirements")
                .machine(machineId.toString())
                .inputs(List.of(iron))
                .requirements(List.of(MachineRequirement.fromInput(apple)))
                .deriveRequirements(false)
                .createObject();

        assertThat(recipe.requirements()).containsExactly(MachineRequirement.fromInput(apple));
        assertThat(recipe.requirements()).doesNotContain(MachineRequirement.fromInput(iron));
    }

}
