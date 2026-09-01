package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerRegistry;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract tests for requirement and output registry dispatch used by custom recipes.
 *
 * @author howxu <dev@howxu.cn>
 */
class CustomRecipeContractTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void registered_output_dispatches_to_its_requirement_and_round_trips_through_the_canonical_type() {
        MachineOutput output = new MachineOutput.ItemOutput(new ItemStack(Items.IRON_INGOT, 2), 1F);

        MachineRequirement requirement = OutputRegistry.toRequirement(output, List.of("contract"));

        assertThat(OutputRegistry.matchesOutputRequirement(output, requirement)).isTrue();
        assertThat(OutputRegistry.fromRequirement(requirement)).isEqualTo(output);
        assertThat(MachineOutput.copyOf(output)).isEqualTo(output).isNotSameAs(output);
    }

    @Test
    void requirement_registry_dispatches_registered_input_types_only() {
        MachineRequirement input = MachineRequirement.fromInput(new MachineIngredient.ItemIngredient(Ingredient.of(Items.COAL), 1));

        RequirementHandlerRegistry.registerBuiltIns();

        assertThat(RequirementHandlerRegistry.handlerFor(input.type())).isNotNull();
        assertThat(input.io()).isEqualTo(cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.IOType.INPUT);
        assertThat(IOType.INPUT.isInput()).isTrue();
    }
}
