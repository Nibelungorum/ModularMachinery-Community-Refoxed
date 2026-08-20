package cn.howxu.mmcr.api.publicapi;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.publicapi.recipe.ItemRequirement;
import cn.howxu.mmcr.api.publicapi.recipe.RecipeIo;
import cn.howxu.mmcr.api.publicapi.recipe.SmartInterfaceRequirement;
import cn.howxu.mmcr.api.recipe.component.DataComponentPredicateSet;
import cn.howxu.mmcr.api.machine.level.MachineLevelRegistry;
import cn.howxu.mmcr.api.publicapi.recipe.MachineRecipeBuilder;
import cn.howxu.mmcr.api.publicapi.recipe.MachineRecipeDefinition;
import cn.howxu.mmcr.api.publicapi.event.MMCRMachineStructuresEvent;
import cn.howxu.mmcr.api.publicapi.machine.ModifierDefinition;
import cn.howxu.mmcr.internal.api.PublicRecipeAdapter;
import cn.howxu.mmcr.test.TestBootstrap;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import org.nibelungorum.builtin.PublicBuiltinLevelDefinitions;
import net.minecraft.resources.Identifier;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.Map;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies public startup recipe builder values and conversion-ready immutability.
 *
 * @author howxu <dev@howxu.cn>
 */
class PublicRecipeBuilderTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @BeforeEach
    void restoreDefaultMachineLevels() {
        cn.howxu.mmcr.api.publicapi.event.MMCRMachineStructuresEvent.resetCollector();
        var event = cn.howxu.mmcr.api.publicapi.event.MMCRMachineStructuresEvent.prepare(java.util.Set.of());
        PublicBuiltinLevelDefinitions.register(event);
        MachineLevelRegistry.installSnapshot(event.levelTypes().values(), event.levels().values());
    }

    @Test
    void builds_item_fluid_energy_recipe_with_scalar_options_and_immutable_values() {
        MachineRecipeDefinition recipe = MachineRecipeBuilder.recipe(id("recipe"), id("machine"))
                .duration(20).priority(3).maxThreads(4).cancelIfPerTickFails(true)
                .parallelized(true).allowPartialOutputs(true)
                .inputItem(Items.IRON_INGOT, 2)
                .inputFluid(net.minecraft.world.level.material.Fluids.WATER, 1000)
                .inputEnergy(40)
                .outputItem(new ItemStack(Items.GOLD_INGOT, 2))
                .outputFluid(net.minecraft.world.level.material.Fluids.WATER, 250)
                .outputEnergy(10)
                .build();

        assertThat(recipe.tickTime()).isEqualTo(20);
        assertThat(recipe.priority()).isEqualTo(3);
        assertThat(recipe.maxThreads()).isEqualTo(4);
        assertThat(recipe.requirements()).hasSize(6);
        assertThat(recipe.requirements()).allSatisfy(requirement -> assertThat(requirement).isNotNull());
        assertThat(recipe.requirements()).isUnmodifiable();
        assertThat(recipe.modifierIds()).isUnmodifiable();
    }

    @Test
    void preserves_item_tag_component_and_consume_chance_and_output_chance() {
        MachineRecipeDefinition recipe = MachineRecipeBuilder.recipe(id("predicates"), id("machine"))
                .inputItem(Ingredient.of(Items.IRON_INGOT), 2)
                .inputItemTag(net.minecraft.tags.ItemTags.create(Identifier.parse("c:ingots/iron")), 3)
                .inputItem(Ingredient.of(Items.GOLD_INGOT), 1, DataComponentPredicateSet.EMPTY, 0.25F)
                .outputChance(new ItemStack(Items.DIAMOND), 0.4F)
                .build();

        assertThat(recipe.requirements()).filteredOn(ItemRequirement.class::isInstance)
                .extracting(ItemRequirement.class::cast)
                .extracting(ItemRequirement::consumeChance).contains(0.25F);
        assertThat(recipe.requirements()).filteredOn(ItemRequirement.class::isInstance)
                .extracting(ItemRequirement.class::cast)
                .extracting(ItemRequirement::chance).contains(0.4F);
    }

    @Test
    void retains_explicit_requirements_smart_interface_level_host_and_modifier_without_deriving_duplicates() {
        var explicit = new cn.howxu.mmcr.api.publicapi.recipe.EnergyRequirement(RecipeIo.INPUT, 12);
        SmartInterfaceRequirement smart = SmartInterfaceRequirement.input("Mode", 1F);
        MachineRecipeDefinition recipe = MachineRecipeBuilder.recipe(id("explicit"), id("machine"))
                .requirement(explicit).requirement(smart)
                .modifier(id("snapshot_modifier"))
                .levelRequirement(PublicBuiltinLevelDefinitions.THERMAL_SMELTING_COIL_TYPE, PublicBuiltinLevelDefinitions.COPPER_COIL)
                .requiredHost(id("host"))
                .build();

        assertThat(recipe.requirements()).containsExactly(explicit, smart);
        assertThat(recipe.levelRequirements()).hasSize(1);
        assertThat(recipe.requiredHostIds()).containsExactly(id("host"));
        assertThat(recipe.modifierIds()).hasSize(1);
    }

    @Test
    void adapts_public_recipe_values_to_internal_recipe_semantics() {
        ItemStack itemOutput = new ItemStack(Items.GOLD_INGOT, 2);
        FluidStack fluidOutput = new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 250);
        var definition = MachineRecipeBuilder.recipe(id("adapter"), id("machine"))
                .inputItem(Ingredient.of(Items.IRON_INGOT), 2, components(), 0.25F)
                .inputFluid(net.minecraft.world.level.material.Fluids.WATER, 1000)
                .inputEnergy(40)
                .outputChance(itemOutput, 0.4F)
                .outputFluid(net.minecraft.world.level.material.Fluids.WATER, 250)
                .outputEnergy(10)
                .levelRequirement(PublicBuiltinLevelDefinitions.THERMAL_SMELTING_COIL_TYPE, PublicBuiltinLevelDefinitions.COPPER_COIL)
                .requiredHost(id("host"))
                .modifier(id("snapshot_modifier"))
                .build();
        var recipe = PublicRecipeAdapter.toRecipe(definition, new MMCRMachineStructuresEvent.Snapshot(
                Map.of(),
                Map.of(),
                Map.of(PublicBuiltinLevelDefinitions.COPPER_COIL, MachineLevelRegistry.getLevel(PublicBuiltinLevelDefinitions.COPPER_COIL)),
                Map.of(id("snapshot_modifier"), new ModifierDefinition(List.of(new cn.howxu.mmcr.api.recipe.modifier.RecipeModifier(
                        "item", cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.IOType.OUTPUT, 2F,
                        cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.Operation.MULTIPLY, true))))));

        assertThat(recipe.requirements()).hasSize(6);
        assertThat(recipe.requirements()).anySatisfy(requirement -> {
            assertThat(requirement).isInstanceOf(cn.howxu.mmcr.api.recipe.requirement.ItemRequirement.class);
            var item = (cn.howxu.mmcr.api.recipe.requirement.ItemRequirement) requirement;
            assertThat(item.io()).isEqualTo(cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.IOType.INPUT);
            assertThat(item.count()).isEqualTo(2);
            assertThat(item.consumeChance()).isEqualTo(0.25F);
            assertThat(item.components()).isEqualTo(components());
        });
        var smartRecipe = PublicRecipeAdapter.toRecipe(MachineRecipeBuilder.recipe(id("adapter_smart"), id("machine"))
                .requirement(SmartInterfaceRequirement.input("Mode", 1F, 2F)).build(),
                new MMCRMachineStructuresEvent.Snapshot(Map.of(), Map.of(), Map.of(), Map.of()));
        assertThat(smartRecipe.requirements()).singleElement().satisfies(requirement -> {
            assertThat(requirement).isInstanceOf(cn.howxu.mmcr.api.recipe.requirement.SmartInterfaceRequirement.class);
            var smart = (cn.howxu.mmcr.api.recipe.requirement.SmartInterfaceRequirement) requirement;
            assertThat(smart.interfaceType()).isEqualTo("Mode");
            assertThat(smart.minValue()).isEqualTo(1F);
            assertThat(smart.maxValue()).isEqualTo(2F);
        });
        assertThat(recipe.requirements()).anySatisfy(requirement -> assertThat(requirement.type()).isEqualTo("fluid"));
        assertThat(recipe.requirements()).anySatisfy(requirement -> assertThat(requirement.type()).isEqualTo("energy"));
        assertThat(recipe.modifiers()).singleElement().satisfies(modifier -> {
            assertThat(modifier.getTarget()).isEqualTo("item");
            assertThat(modifier.getIOTarget()).isEqualTo(cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.IOType.OUTPUT);
            assertThat(modifier.getModifier()).isEqualTo(2F);
            assertThat(modifier.getOperation()).isEqualTo(cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.Operation.MULTIPLY);
            assertThat(modifier.affectsChance()).isTrue();
        });
        assertThat(recipe.levelRequirements()).singleElement().satisfies(level -> {
            assertThat(level.typeId()).isEqualTo(PublicBuiltinLevelDefinitions.THERMAL_SMELTING_COIL_TYPE);
            assertThat(level.levelId()).isEqualTo(PublicBuiltinLevelDefinitions.COPPER_COIL);
        });
        assertThat(recipe.requiredHostIds()).containsExactly(id("host"));
    }

    @Test
    void defensively_copies_item_and_fluid_stacks_at_input_and_accessor_boundaries() {
        ItemStack item = new ItemStack(Items.IRON_INGOT, 2);
        FluidStack fluid = new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 1000);
        MachineRecipeDefinition recipe = MachineRecipeBuilder.recipe(id("copies"), id("machine"))
                .outputItem(item).outputFluid(net.minecraft.world.level.material.Fluids.WATER, 1000).build();
        var fluidOutput = new cn.howxu.mmcr.api.publicapi.recipe.FluidOutput(fluid);
        item.setCount(1);
        fluid.setAmount(1);

        ItemStack itemAccessor = recipe.itemOutputs().getFirst().stack();
        FluidStack fluidAccessor = recipe.fluidOutputs().getFirst().stack();
        assertThat(itemAccessor.getCount()).isEqualTo(2);
        assertThat(fluidAccessor.getAmount()).isEqualTo(1000);
        itemAccessor.setCount(1);
        fluidAccessor.setAmount(1);
        assertThat(recipe.itemOutputs().getFirst().stack().getCount()).isEqualTo(2);
        assertThat(recipe.fluidOutputs().getFirst().stack().getAmount()).isEqualTo(1000);
        FluidStack explicitFluidAccessor = fluidOutput.stack();
        assertThat(explicitFluidAccessor.getAmount()).isEqualTo(1000);
        explicitFluidAccessor.setAmount(1);
        assertThat(fluidOutput.stack().getAmount()).isEqualTo(1000);
    }

    @Test
    void rejects_invalid_ranges() {
        assertThatThrownBy(() -> MachineRecipeBuilder.recipe(id("bad"), id("machine")).duration(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SmartInterfaceRequirement.input(" ", 1F))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SmartInterfaceRequirement.input("Mode", Float.NaN))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SmartInterfaceRequirement.input("Mode", 2F, 1F))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MachineRecipeBuilder.recipe(id("bad"), id("machine")).inputItem(Items.STICK, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MachineRecipeBuilder.recipe(id("bad"), id("machine"))
                .outputChance(new ItemStack(Items.STICK), 2F))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Identifier id(String path) {
        return MMCR.id(path);
    }

    private static DataComponentPredicateSet components() {
        return new DataComponentPredicateSet(Map.of(DataComponents.REPAIR_COST,
                cn.howxu.mmcr.api.recipe.component.ComponentPredicate.exact(
                        new Dynamic<>(JsonOps.INSTANCE, JsonOps.INSTANCE.createInt(1)))));
    }
}
