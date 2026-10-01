package cn.howxu.mmcr.internal.runtime;

import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.IOType;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.plan.CraftingPlan;
import cn.howxu.mmcr.api.capability.plan.RequirementPlan;
import cn.howxu.mmcr.api.compat.mekanism.ChemicalIngredient;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerRegistry;
import cn.howxu.mmcr.compat.mekanism.loaded.LoadedChemicalRequirement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import cn.howxu.mmcr.test.TestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@link CraftingRuntime#captureInputState} treats
 * {@link LoadedChemicalRequirement} as a single-consume input, matching the
 * existing item semantics. The test invokes the package-private method via
 * reflection so it can observe the captured indices without standing up a full
 * controller runtime.
 *
 * @author howxu <dev@howxu.cn>
 */
class CraftingRuntimeChemicalConsumptionTest {

    @BeforeAll
    static void bootstrap() throws Exception {
        TestBootstrap.bootstrap();
        if (RequirementHandlerRegistry.canonicalType(LoadedChemicalRequirement.TYPE) == null) {
            RequirementHandlerRegistry.register(LoadedChemicalRequirement.TYPE);
        }
    }

    @Test
    void chemicalInputIsFlaggedAsConsumedAtStart() throws Exception {
        MachineRequirement chemicalInput = LoadedChemicalRequirement.input(
                ChemicalIngredient.chemical(ResourceLocation.fromNamespaceAndPath("mekanism", "oxygen"), 1_000L));
        MachineRequirement itemInput = new ItemRequirement(RecipeModifier.IOType.INPUT,
                Ingredient.of(Items.IRON_INGOT), 1, net.minecraft.world.item.ItemStack.EMPTY, 1F,
                List.of(), cn.howxu.mmcr.api.recipe.component.DataComponentPredicateSet.EMPTY, 1F);
        List<MachineRequirement> requirements = List.of(chemicalInput, itemInput);

        CraftingPlan plan = new CraftingPlan(List.of(
                new RequirementPlan(0, 1L, List.of(() -> null), null),
                new RequirementPlan(1, 1L, List.of(), null)),
                1L, Map.of(
                        0, RecipeModifier.IOType.INPUT,
                        1, RecipeModifier.IOType.INPUT));

        MachineRecipe recipe = cn.howxu.mmcr.test.RecipeTestSupport.create(
                MMCR.id("capture_state"), MMCR.id("test_cube"), 1,
                requirements, List.of(), List.of(), 0, 1, false,
                List.of(), List.of(), false, List.of(), false, Set.of());
        cn.howxu.mmcr.api.recipe.ActiveMachineRecipe activeRecipe =
                new cn.howxu.mmcr.api.recipe.ActiveMachineRecipe(recipe, 1L);
        activeRecipe.setParallelism(1L);

        Object runtime = createBareRuntime(activeRecipe);
        invokeCapture(runtime, requirements, plan);

        @SuppressWarnings("unchecked")
        Set<Integer> consumed = (Set<Integer>) readField(runtime, "consumedAtStart");
        @SuppressWarnings("unchecked")
        Set<Integer> retained = (Set<Integer>) readField(runtime, "retainedInputs");
        assertThat(consumed).containsExactly(0);
        assertThat(retained).containsExactly(1);
    }

    @Test
    void restoredChemicalInputRemainsConsumedAtStart() throws Exception {
        MachineRequirement chemicalInput = LoadedChemicalRequirement.input(
                ChemicalIngredient.chemical(ResourceLocation.fromNamespaceAndPath("mekanism", "oxygen"), 1_000L));
        MachineRecipe recipe = new MachineRecipe(MMCR.id("restore_chemical_input"), MMCR.id("test_cube"), 1,
                List.of(chemicalInput), List.of(), List.of(), 0, 1, false, false, false, Set.of());
        cn.howxu.mmcr.api.recipe.ActiveMachineRecipe activeRecipe =
                new cn.howxu.mmcr.api.recipe.ActiveMachineRecipe(recipe, 1L);
        activeRecipe.setInputConsumptionPlan(
                new cn.howxu.mmcr.api.recipe.ActiveMachineRecipe.InputConsumptionPlan(List.of(1)));
        CraftingRuntime runtime = (CraftingRuntime) createBareRuntime();

        runtime.restore(activeRecipe, null, 0L, 0L, 0L, 0L);

        @SuppressWarnings("unchecked")
        Set<Integer> consumed = (Set<Integer>) readField(runtime, "consumedAtStart");
        assertThat(consumed).containsExactly(0);
    }

    private static Object createBareRuntime(cn.howxu.mmcr.api.recipe.ActiveMachineRecipe activeRecipe)
            throws Exception {
        cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity controller =
                cn.howxu.mmcr.test.RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());
        Field activeRecipeField = CraftingRuntime.class.getDeclaredField("activeRecipe");
        activeRecipeField.setAccessible(true);
        activeRecipeField.set(runtime, activeRecipe);
        return runtime;
    }

    private static Object createBareRuntime() throws Exception {
        cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity controller =
                cn.howxu.mmcr.test.RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        return new CraftingRuntime(controller, controller.componentRuntime());
    }

    private static void invokeCapture(Object runtime, List<MachineRequirement> requirements, CraftingPlan plan)
            throws Exception {
        Method method = CraftingRuntime.class.getDeclaredMethod("captureInputState", List.class, CraftingPlan.class);
        method.setAccessible(true);
        method.invoke(runtime, requirements, plan);
    }

    private static Object readField(Object target, String name) throws Exception {
        Field field = CraftingRuntime.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
}
