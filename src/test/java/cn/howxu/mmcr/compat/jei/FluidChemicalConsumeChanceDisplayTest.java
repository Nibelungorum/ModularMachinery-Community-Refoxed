package cn.howxu.mmcr.compat.jei;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.compat.mekanism.ChemicalIngredient;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.FluidRequirement;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerRegistry;
import cn.howxu.mmcr.compat.mekanism.MekanismRecipeTypes;
import cn.howxu.mmcr.compat.mekanism.loaded.LoadedChemicalRequirement;
import cn.howxu.mmcr.test.TestBootstrap;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.Chemical;
import mekanism.api.chemical.ChemicalBuilder;
import mekanism.api.chemical.ChemicalStack;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the JEI display surfaces fluid and chemical input consumeChance to its overlay/tooltip code path.
 *
 * @author howxu <dev@howxu.cn>
 */
class FluidChemicalConsumeChanceDisplayTest {
    private static final ResourceLocation MACHINE = MMCR.id("display_consume_chance");

    @BeforeAll
    static void bootstrap() throws Exception {
        TestBootstrap.bootstrap();
        RequirementHandlerRegistry.register(LoadedChemicalRequirement.TYPE);
        LoadedChemicalRequirement.installUnavailableHandler();
        registerOxygen();
    }

    @SuppressWarnings({"unchecked", "removal"})
    private static void registerOxygen() {
        ResourceKey<Chemical> key = ResourceKey.create(MekanismAPI.CHEMICAL_REGISTRY_NAME,
                ResourceLocation.parse("mekanism:oxygen"));
        MappedRegistry<Chemical> registry = (MappedRegistry<Chemical>) MekanismAPI.CHEMICAL_REGISTRY;
        if (registry.getHolder(key).isPresent()) return;
        registry.unfreeze();
        if (registry.get(MekanismAPI.EMPTY_CHEMICAL_KEY) == null) {
            Registry.registerForHolder(registry, MekanismAPI.EMPTY_CHEMICAL_KEY, MekanismAPI.EMPTY_CHEMICAL);
        }
        Registry.registerForHolder(registry, key,
                new Chemical(ChemicalBuilder.builder()) {
                    @Override
                    public boolean isRadioactive() {
                        return false;
                    }
                });
        registry.freeze();
    }

    @Test
    void fluid_input_display_carries_consume_chance() {
        MachineRequirement fluidInput = new FluidRequirement(RecipeModifier.IOType.INPUT,
                FluidIngredient.of(Fluids.WATER), 1000, FluidStack.EMPTY, 1F, List.of(), 0.25F);
        MachineRecipe recipe = MachineRecipe.fromCanonical(MMCR.id("fluid_consume"), MACHINE, 20,
                List.of(fluidInput), List.of(), List.of(), 0, 1, false, false, false, Set.of());

        MachineRecipeDisplay display = MachineRecipeDisplay.from(recipe);

        MachineRecipeDisplay.FluidInputDisplay fluidDisplay = display.fluidInputs().get(0);
        assertThat(fluidDisplay.amount()).isEqualTo(1000);
        assertThat(fluidDisplay.consumeChance()).isEqualTo(0.25F);
    }

    @Test
    void chemical_input_display_carries_consume_chance() {
        MachineRequirement chemicalInput = new LoadedChemicalRequirement(RecipeModifier.IOType.INPUT,
                ChemicalIngredient.chemical(ResourceLocation.parse("mekanism:oxygen"), 1_000L), 1F, List.of(), 0F);
        MachineRecipe recipe = MachineRecipe.fromCanonical(MMCR.id("chemical_consume"), MACHINE, 20,
                List.of(chemicalInput), List.of(), List.of(), 0, 1, false, false, false, Set.of());

        MachineRecipeDisplay display = MachineRecipeDisplay.from(recipe);

        JeiDisplayEntry chemical = display.entries().stream()
                .filter(entry -> entry.typeId().equals(MekanismRecipeTypes.CHEMICAL))
                .findFirst().orElseThrow();
        assertThat(chemical.chance()).isEqualTo(0F);
    }

    @Test
    void chemical_display_stack_uses_full_render_amount() {
        MachineRequirement chemicalInput = new LoadedChemicalRequirement(RecipeModifier.IOType.INPUT,
                ChemicalIngredient.chemical(ResourceLocation.parse("mekanism:oxygen"), 1_000L), 1F, List.of(), 1F);
        MachineRecipe recipe = MachineRecipe.fromCanonical(MMCR.id("chemical_render_amount"), MACHINE, 20,
                List.of(chemicalInput), List.of(), List.of(), 0, 1, false, false, false, Set.of());

        JeiDisplayEntry chemical = MachineRecipeDisplay.from(recipe).entries().stream()
                .filter(entry -> entry.typeId().equals(MekanismRecipeTypes.CHEMICAL))
                .findFirst().orElseThrow();

        assertThat(chemical.count()).isEqualTo(1_000);
        assertThat(chemical.ingredient()).isInstanceOf(List.class);
        List<?> ingredients = (List<?>) chemical.ingredient();
        assertThat(ingredients).singleElement().isInstanceOf(ChemicalStack.class);
        assertThat(((ChemicalStack) ingredients.getFirst()).getAmount()).isEqualTo(1_000);
    }

    @Test
    void chemical_quantity_uses_b_units() {
        assertThat(MachineRecipeCategory.chemicalQuantityText(1_000L))
                .isEqualTo("1.00B")
                .doesNotContain("mB");
        assertThat(MachineRecipeCategory.chemicalTooltipQuantity(1_250L))
                .isEqualTo("1.25B")
                .doesNotContain("mB");
    }
}
