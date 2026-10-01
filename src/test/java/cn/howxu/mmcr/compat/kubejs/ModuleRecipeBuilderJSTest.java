package cn.howxu.mmcr.compat.kubejs;

import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.IOType;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.api.machine.MachineRegistration;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.recipe.MachineIngredient;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.MachineRecipeJson;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.recipe.requirement.FluidRequirement;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.api.recipe.requirement.SmartInterfaceRequirement;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.test.RecipeTestSupport;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import com.google.gson.JsonParser;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import java.util.Map;

import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
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

    @BeforeEach
    void beginRegistryPhase() {
        MachineDefinitions.beginRegistryPhase();
    }

    @AfterEach
    void cleanup() {
        MachineDefinitions.clearForTesting();
        MachineRegistry.clearForTesting();
        RecipeRegistry.clearForTesting();
    }

    @Test
    void builder_declares_required_hosts_with_stable_deduplication() {
        var builder = new MachineRecipeBuilderJS("mmcr:module_recipe")
                .requiredHost("mmcr:first")
                .requiredHosts("mmcr:second", "mmcr:first", "mmcr:third");

        assertThat(builder.requiredHostIds)
                .containsExactly(ResourceLocation.parse("mmcr:first"), ResourceLocation.parse("mmcr:second"), ResourceLocation.parse("mmcr:third"));
    }

    @Test
    void build_preserves_required_host_declaration_order() {
        ResourceLocation machineId = MMCR.id("module_machine");
        ResourceLocation recipeId = MMCR.id("module_recipe");
        MachineDefinitions.register(MachineRegistration.builder(machineId).build());

        new MachineRecipeBuilderJS(recipeId)
                .recipePool(machineId.toString())
                .requiredHosts("mmcr:first", "mmcr:second", "mmcr:first", "mmcr:third")
                .build();

        assertThat(RecipeRegistry.getRecipe(recipeId).requiredHostIds())
                .containsExactly(ResourceLocation.parse("mmcr:first"), ResourceLocation.parse("mmcr:second"), ResourceLocation.parse("mmcr:third"));
    }

    @Test
    void public_recipe_builder_creates_recipe_object() {
        ResourceLocation machineId = MMCR.id("module_machine");
        MachineDefinitions.register(MachineRegistration.builder(machineId).build());

        var recipe = new MachineRecipeBuilderJS("mmcr:event_recipe")
                .recipePool(machineId.toString())
                .tickTime(1)
                .createObject();

        assertThat(recipe.id()).isEqualTo(MMCR.id("event_recipe"));
    }

    @Test
    void recipe_builder_requires_explicit_recipe_pool_without_machine_ownership_api() {
        ResourceLocation poolId = MMCR.id("explicit_recipe_pool");
        MachineDefinitions.register(MachineRegistration.builder(poolId).build());

        assertThat(new MachineRecipeBuilderJS("mmcr:explicit_pool_recipe")
                .recipePool(poolId.toString())
                .createObject().recipePoolId()).isEqualTo(poolId);
        assertThatThrownBy(() -> new MachineRecipeBuilderJS("mmcr:missing_pool_recipe").createObject())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("recipePool() not called");
        assertThat(List.of(MachineRecipeBuilderJS.class.getMethods()))
                .extracting(java.lang.reflect.Method::getName)
                .doesNotContain("machine");
    }

    @Test
    void create_object_preserves_complete_public_recipe_values() {
        ResourceLocation machineId = MMCR.id("module_machine");
        MachineDefinitions.register(MachineRegistration.builder(machineId).build());
        var itemInput = new MachineIngredient.ItemIngredient(Ingredient.of(Items.IRON_INGOT), 2);
        var fluidInput = new MachineIngredient.FluidIngredient(FluidIngredient.of(Fluids.WATER), 500);
        var energyOutput = new MachineIngredient.EnergyIngredient(RecipeModifier.IOType.OUTPUT, 20);
        var fluidOutput = new FluidStack(Fluids.WATER, 250);
        MachineRequirement smartRequirement = SmartInterfaceRequirement.input("temperature", 25F);

        MachineRecipe recipe = new MachineRecipeBuilderJS("mmcr:kubejs_full_recipe")
                .recipePool(machineId.toString())
                .inputs(List.of(itemInput, fluidInput, energyOutput))
                .outputs(List.of(new ItemStack(Items.DIAMOND)))
                .fluidOutputs(List.of(fluidOutput))
                .requirements(List.of(smartRequirement))
                .priority(7).maxThreads(3).cancelIfPerTickFails(true).allowPartialOutputs()
                .requiredHosts("mmcr:space_elevator")
                .createObject();

        assertThat(recipe.priority()).isEqualTo(7);
        assertThat(recipe.maxThreads()).isEqualTo(3);
        assertThat(recipe.requirements()).filteredOn(requirement -> requirement.io() == RecipeModifier.IOType.INPUT)
                .hasSize(3);
        assertThat(recipe.requirements()).filteredOn(requirement -> requirement.io() == RecipeModifier.IOType.OUTPUT)
                .anySatisfy(requirement -> assertThat(requirement)
                        .isInstanceOfSatisfying(EnergyRequirement.class, energy -> assertThat(energy.fePerTick()).isEqualTo(20)));
        assertThat(recipe.machineOutputs()).filteredOn(MachineOutput.ItemOutput.class::isInstance).singleElement()
                .isInstanceOfSatisfying(MachineOutput.ItemOutput.class,
                        output -> assertThat(output.stack().getItem()).isSameAs(Items.DIAMOND));
        assertThat(recipe.machineOutputs()).filteredOn(MachineOutput.FluidOutput.class::isInstance).singleElement()
                .isInstanceOfSatisfying(MachineOutput.FluidOutput.class, output -> {
                    assertThat(output.stack().getFluid()).isSameAs(Fluids.WATER);
                    assertThat(output.stack().getAmount()).isEqualTo(250);
                });
        assertThat(recipe.requirements()).contains(smartRequirement);
        assertThat(recipe.doesCancelRecipeOnPerTickFailure()).isTrue();
        assertThat(recipe.allowPartialOutputs()).isTrue();
        assertThat(recipe.requiredHostIds()).containsExactly(ResourceLocation.parse("mmcr:space_elevator"));
    }

    @Test
    void energy_input_and_output_shortcuts_emit_per_tick_requirements_with_correct_io() {
        ResourceLocation machineId = MMCR.id("module_machine");
        MachineDefinitions.register(MachineRegistration.builder(machineId).build());

        MachineRecipe recipe = new MachineRecipeBuilderJS("mmcr:energy_io_shortcuts")
                .recipePool(machineId.toString())
                .iFEt(40)
                .oFEt(20)
                .createObject();

        assertThat(recipe.requirements()).filteredOn(EnergyRequirement.class::isInstance)
                .map(EnergyRequirement.class::cast)
                .containsExactlyInAnyOrder(
                        new EnergyRequirement(RecipeModifier.IOType.INPUT, 40),
                        new EnergyRequirement(RecipeModifier.IOType.OUTPUT, 20));
    }

    @Test
    void energy_output_shortcut_alone_produces_output_directed_requirement() {
        ResourceLocation machineId = MMCR.id("module_machine");
        MachineDefinitions.register(MachineRegistration.builder(machineId).build());

        MachineRecipe recipe = new MachineRecipeBuilderJS("mmcr:energy_output_alone")
                .recipePool(machineId.toString())
                .oFEt(75)
                .createObject();

        assertThat(recipe.requirements()).singleElement().isInstanceOfSatisfying(EnergyRequirement.class, energy -> {
            assertThat(energy.io()).isEqualTo(RecipeModifier.IOType.OUTPUT);
            assertThat(energy.fePerTick()).isEqualTo(75);
        });
    }

    @Test
    void create_object_matches_shared_json_parser_for_complete_recipe_values() {
        ResourceLocation machineId = MMCR.id("module_machine");
        ResourceLocation recipeId = MMCR.id("shared_parser_recipe");
        MachineDefinitions.register(MachineRegistration.builder(machineId).build());
        MachineRegistry.register(new DynamicMachine(machineId, "Module Machine", new BlockArray(Map.of())));
        var input = new MachineIngredient.ItemIngredient(Ingredient.of(Items.IRON_INGOT), 2, null, 0.5F);
        var output = new ItemStack(Items.DIAMOND, 1);
        var builder = new MachineRecipeBuilderJS(recipeId)
                .recipePool(machineId.toString())
                .tickTime(20)
                .inputs(List.of(input))
                .outputs(List.of(output))
                .energyPerTick(80)
                .priority(7)
                .maxThreads(3)
                .parallelized()
                .cancelIfPerTickFails(true)
                .allowPartialOutputs()
                .requiredHosts("mmcr:space_elevator")
                .deriveRequirements(false)
                .requirements(List.of(MachineRequirement.fromInput(input)));

        MachineRecipe built = builder.createObject();
        JsonObject json = new JsonObject();
        json.addProperty("type", "mmcr:machine_recipe");
        json.addProperty("recipe_pool", machineId.toString());
        json.addProperty("tick_time", 20);
        json.add("outputs", MachineOutput.CODEC.listOf().encodeStart(JsonOps.INSTANCE,
                List.of(new MachineOutput.ItemOutput(output, 1F))).getOrThrow());
        json.addProperty("priority", 7);
        json.addProperty("max_threads", 3);
        json.addProperty("parallelized", true);
        json.addProperty("cancelIfPerTickFails", true);
        json.addProperty("allow_partial_outputs", true);
        json.add("required_host_ids", ResourceLocation.CODEC.listOf().encodeStart(JsonOps.INSTANCE,
                List.of(ResourceLocation.parse("mmcr:space_elevator"))).getOrThrow());
        json.add("requirements", MachineRequirement.CODEC.listOf().encodeStart(JsonOps.INSTANCE,
                List.of(MachineRequirement.fromInput(input))).getOrThrow());

        MachineRecipe parsed = MachineRecipeJson.parse(recipeId, json, VanillaRegistries.createLookup());
        assertThat(built).usingRecursiveComparison().isEqualTo(parsed);
    }

    @Test
    void create_object_rejects_negative_ingredient_counts() {
        ResourceLocation machineId = MMCR.id("module_machine");
        MachineDefinitions.register(MachineRegistration.builder(machineId).build());

        assertThatThrownBy(() -> new MachineRecipeBuilderJS("mmcr:negative_input")
                .recipePool(machineId.toString())
                .addInput(new MachineIngredient.EnergyIngredient(-1))
                .createObject())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be negative");
    }

    @Test
    void direct_output_lists_preserve_non_negative_normalized_values() {
        ResourceLocation machineId = MMCR.id("module_machine");
        MachineDefinitions.register(MachineRegistration.builder(machineId).build());

        var itemOutput = new ItemStack(Items.DIAMOND, -1);
        var fluidOutput = new FluidStack(Fluids.WATER, -1);

        assertThat(itemOutput.getCount()).isZero();
        assertThat(fluidOutput.getAmount()).isZero();
        assertThat(new MachineRecipeBuilderJS("mmcr:negative_item_output")
                .recipePool(machineId.toString())
                .outputs(List.of(itemOutput))
                .createObject()
                .machineOutputs()).singleElement().isInstanceOfSatisfying(MachineOutput.ItemOutput.class,
                        output -> assertThat(output.stack().isEmpty()).isTrue());
        assertThat(new MachineRecipeBuilderJS("mmcr:negative_fluid_output")
                .recipePool(machineId.toString())
                .fluidOutputs(List.of(fluidOutput))
                .createObject()
                .machineOutputs()).singleElement().isInstanceOfSatisfying(MachineOutput.FluidOutput.class,
                        output -> assertThat(output.stack().isEmpty()).isTrue());
    }

    @Test
    void builder_preserves_component_tag_inputs_and_explicit_requirements() {
        ResourceLocation machineId = MMCR.id("module_machine");
        MachineDefinitions.register(MachineRegistration.builder(machineId).build());

        var recipe = new MachineRecipeBuilderJS("mmcr:component_tag_recipe")
                .recipePool(machineId.toString())
                .tagInputWithComponents("minecraft:planks", 2, JsonParser.parseString("""
                        {"minecraft:custom_name":{"text":"Validated"}}
                        """), 0.5F)
                .addRequirement(new KubeJSApi().itemOutputRequirement("minecraft:emerald", 1, 0.25F))
                .addRequirement(new KubeJSApi().fluidOutputRequirement("minecraft:lava", 250, 0.5F))
                .createObject();

        var input = recipe.requirements().stream()
                .filter(ItemRequirement.class::isInstance)
                .map(ItemRequirement.class::cast)
                .filter(requirement -> requirement.io() == RecipeModifier.IOType.INPUT)
                .findFirst().orElseThrow();
        assertThat(input.count()).isEqualTo(2);
        assertThat(input.consumeChance()).isEqualTo(0.5F);
        assertThat(input.components().isEmpty()).isFalse();
        assertThat(recipe.requirements()).hasSize(3);
        assertThat(recipe.requirements().stream().filter(ItemRequirement.class::isInstance).map(ItemRequirement.class::cast)
                .filter(requirement -> requirement.io() == RecipeModifier.IOType.OUTPUT).toList()).singleElement().satisfies(requirement -> {
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
        ResourceLocation machineId = MMCR.id("module_machine");
        MachineDefinitions.register(MachineRegistration.builder(machineId).build());

        assertThat(new MachineRecipeBuilderJS("mmcr:serial")
                .recipePool(machineId.toString()).createObject().isParallelized()).isFalse();
        assertThat(new MachineRecipeBuilderJS("mmcr:parallel")
                .recipePool(machineId.toString()).parallelized().createObject().isParallelized()).isTrue();
        assertThat(new MachineRecipeBuilderJS("mmcr:explicit_serial")
                .recipePool(machineId.toString()).parallelized(false).createObject().isParallelized()).isFalse();
    }

    @Test
    void explicit_requirements_can_disable_automatic_input_derivation() {
        ResourceLocation machineId = MMCR.id("module_machine");
        MachineDefinitions.register(MachineRegistration.builder(machineId).build());
        var iron = new MachineIngredient.ItemIngredient(Ingredient.of(Items.IRON_INGOT), 1);
        var apple = new MachineIngredient.ItemIngredient(Ingredient.of(Items.APPLE), 1);

        var recipe = new MachineRecipeBuilderJS("mmcr:legacy_input_explicit_requirements")
                .recipePool(machineId.toString())
                .inputs(List.of(iron))
                .requirements(List.of(MachineRequirement.fromInput(apple)))
                .deriveRequirements(false)
                .createObject();

        assertThat(recipe.requirements()).containsExactly(MachineRequirement.fromInput(apple));
        assertThat(recipe.requirements()).doesNotContain(MachineRequirement.fromInput(iron));
    }

    @Test
    void derive_requirements_false_with_empty_requirements_keeps_recipe_requirements_empty() {
        ResourceLocation machineId = MMCR.id("module_machine");
        MachineDefinitions.register(MachineRegistration.builder(machineId).build());
        var iron = new MachineIngredient.ItemIngredient(Ingredient.of(Items.IRON_INGOT), 1);

        var recipe = new MachineRecipeBuilderJS("mmcr:legacy_input_no_requirements")
                .recipePool(machineId.toString())
                .inputs(List.of(iron))
                .deriveRequirements(false)
                .createObject();

        assertThat(recipe.requirements()).isEmpty();
    }

    @Test
    void create_object_rejects_zero_tick_time() {
        ResourceLocation machineId = MMCR.id("module_machine");
        MachineDefinitions.register(MachineRegistration.builder(machineId).build());

        assertThatThrownBy(() -> new MachineRecipeBuilderJS("mmcr:zero_tick")
                .recipePool(machineId.toString())
                .tickTime(0)
                .createObject())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tick time must be >= 1");
    }

    @Test
    void machine_recipe_constructor_rejects_zero_tick_time() {
        ResourceLocation machineId = MMCR.id("module_machine");

        assertThatThrownBy(() -> RecipeTestSupport.create(MMCR.id("zero_tick_constructor"), machineId, 0, List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tick time must be >= 1");
    }

}
