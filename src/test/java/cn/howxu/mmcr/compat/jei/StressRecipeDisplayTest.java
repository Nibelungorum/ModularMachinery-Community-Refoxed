package cn.howxu.mmcr.compat.jei;

import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.IOType;
import cn.howxu.mmcr.api.recipe.requirement.FluidRequirement;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.compat.create.CreateRecipeTypes;
import cn.howxu.mmcr.compat.create.StressRequirement;
import cn.howxu.mmcr.test.TestBootstrap;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Stress remains typed recipe data and never becomes an unknown JEI ingredient slot.
 * @author howxu <dev@howxu.cn>
 */
class StressRecipeDisplayTest {
    @BeforeAll
    static void bootstrap() throws Exception {
        TestBootstrap.bootstrap();
        CreateRecipeTypes.register();
    }

    @Test
    void mixed_requirements_keep_normal_entries_in_order_and_signed_stress_in_typed_lists() {
        var item = new ItemRequirement(IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 2, ItemStack.EMPTY);
        var fluid = new FluidRequirement(IOType.INPUT, FluidIngredient.of(Fluids.WATER), 100, FluidStack.EMPTY);
        var ordinary = display(List.of(item, fluid), List.of());
        var mixed = display(List.of(StressRequirement.input(8, 32, List.of("drive")), item,
                StressRequirement.output(16, -64, List.of("generator")), fluid), List.of());

        assertThat(mixed.stressInputs()).containsExactly(new MachineRecipeDisplay.StressDisplay(8, 32, 0, List.of("drive")));
        assertThat(mixed.stressOutputs()).containsExactly(new MachineRecipeDisplay.StressDisplay(16, 0, -64, List.of("generator")));
        assertThat(mixed.entries()).hasSize(ordinary.entries().size());
        assertThat(mixed.entries()).extracting(JeiDisplayEntry::typeId)
                .containsExactlyElementsOf(ordinary.entries().stream().map(JeiDisplayEntry::typeId).toList());
        assertThat(mixed.itemInputs()).hasSize(1);
        assertThat(mixed.fluidInputs()).hasSize(1);
        assertThat(mixed.energyInputs()).isEmpty();
        assertThat(mixed.energyOutputs()).isEmpty();
        assertThat(ordinary.stressInputs()).isEmpty();
        assertThat(ordinary.stressOutputs()).isEmpty();
    }

    @Test
    void typed_lists_use_effective_base_stress_modifiers_without_scaling_rpm_or_duration() {
        var display = display(List.of(StressRequirement.input(8, 32, List.of()),
                StressRequirement.output(16, -64, List.of())), List.of(
                new RecipeModifier("create:stress", IOType.INPUT, 2, RecipeModifier.Operation.MULTIPLY, false),
                new RecipeModifier("create:stress", IOType.OUTPUT, 3, RecipeModifier.Operation.MULTIPLY, false)));
        assertThat(display.stressInputs()).containsExactly(new MachineRecipeDisplay.StressDisplay(16, 32, 0, List.of()));
        assertThat(display.stressOutputs()).containsExactly(new MachineRecipeDisplay.StressDisplay(48, 0, -64, List.of()));
        assertThat(display.durationTicks()).isEqualTo(20);
        assertThat(display.entries()).isEmpty();
        assertThatThrownBy(() -> display.stressInputs().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void display_tags_are_copied_and_immutable() {
        var tags = new ArrayList<>(List.of("drive"));
        var display = new MachineRecipeDisplay.StressDisplay(8, 32, 0, tags);
        tags.clear();
        assertThat(display.tags()).containsExactly("drive");
        assertThatThrownBy(() -> display.tags().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    private static MachineRecipeDisplay display(List<MachineRequirement> requirements, List<RecipeModifier> modifiers) {
        var id = ResourceLocation.parse("test:stress_display");
        return MachineRecipeDisplay.from(MachineRecipe.fromCanonical(id, id, 20, requirements, List.of(),
                modifiers, 0, 1, false, false, false, Set.of()));
    }
}
