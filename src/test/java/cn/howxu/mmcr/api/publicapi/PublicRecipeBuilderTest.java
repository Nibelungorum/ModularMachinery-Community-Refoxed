package cn.howxu.mmcr.api.publicapi;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.recipe.component.DataComponentPredicateSet;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.publicapi.recipe.MachineRecipeBuilder;
import cn.howxu.mmcr.api.publicapi.recipe.MachineRecipeDefinition;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

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

    @Test
    void builds_item_fluid_energy_recipe_with_scalar_options_and_immutable_values() {
        MachineRecipeDefinition recipe = MachineRecipeBuilder.recipe(id("recipe"), id("machine"))
                .duration(20).priority(3).maxThreads(4).cancelIfPerTickFails(true)
                .parallelized(true).allowPartialOutputs(true)
                .inputItem(Items.IRON_INGOT, 2)
                .inputFluid(net.minecraft.world.level.material.Fluids.WATER, 1000)
                .inputEnergy(40)
                .outputItem(new ItemStack(Items.GOLD_INGOT, 2))
                .outputFluid(net.minecraft.world.level.material.Fluids.LAVA, 250)
                .outputEnergy(10)
                .build();

        assertThat(recipe.tickTime()).isEqualTo(20);
        assertThat(recipe.priority()).isEqualTo(3);
        assertThat(recipe.maxThreads()).isEqualTo(4);
        assertThat(recipe.requirements()).hasSize(6);
        assertThat(recipe.requirements()).allSatisfy(requirement -> assertThat(requirement).isNotNull());
        assertThat(recipe.requirements()).isUnmodifiable();
        assertThat(recipe.modifiers()).isUnmodifiable();
    }

    @Test
    void preserves_item_tag_component_and_consume_chance_and_output_chance() {
        MachineRecipeDefinition recipe = MachineRecipeBuilder.recipe(id("predicates"), id("machine"))
                .inputItem(Ingredient.of(Items.IRON_INGOT), 2)
                .inputItemTag(net.minecraft.tags.ItemTags.create(Identifier.parse("c:ingots/iron")), 3)
                .inputItem(Ingredient.of(Items.GOLD_INGOT), 1, DataComponentPredicateSet.EMPTY, 0.25F)
                .outputChance(new ItemStack(Items.DIAMOND), 0.4F)
                .build();

        assertThat(recipe.requirements()).filteredOn(requirement -> requirement instanceof ItemRequirement)
                .extracting("consumeChance").contains(0.25F);
        assertThat(recipe.requirements()).filteredOn(requirement -> requirement instanceof ItemRequirement)
                .extracting("chance").contains(0.4F);
    }

    @Test
    void retains_explicit_requirements_smart_interface_level_host_and_modifier_without_deriving_duplicates() {
        EnergyRequirement explicit = new EnergyRequirement(RecipeModifier.IOType.INPUT, 12);
        MachineRecipeBuilder.SmartInterface smart = MachineRecipeBuilder.SmartInterface.input("Mode", 1F);
        MachineRecipeDefinition recipe = MachineRecipeBuilder.recipe(id("explicit"), id("machine"))
                .requirement(explicit).requirement(smart)
                .modifier(new RecipeModifier("", RecipeModifier.IOType.INPUT, 2F,
                        RecipeModifier.Operation.MULTIPLY, false))
                .levelRequirement(id("coil"), id("coil_level"))
                .requiredHost(id("host"))
                .build();

        assertThat(recipe.requirements()).containsExactly(explicit, smart);
        assertThat(recipe.levelRequirements()).hasSize(1);
        assertThat(recipe.requiredHostIds()).containsExactly(id("host"));
        assertThat(recipe.modifiers()).hasSize(1);
    }

    @Test
    void rejects_invalid_ranges() {
        assertThatThrownBy(() -> MachineRecipeBuilder.recipe(id("bad"), id("machine")).duration(0))
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
}
