package cn.howxu.mmcr.publicapi.recipe.modifier;

import cn.howxu.mmcr.api.machine.modifier.MachineModifier;
import cn.howxu.mmcr.api.machine.modifier.ModifierTarget;
import cn.howxu.mmcr.internal.api.facade.recipe.ModifierAdapters;
import cn.howxu.mmcr.publicapi.recipe.IoDirection;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Scope/operation adapters must reach the actual core recipe and scheduler arithmetic.
 * @author howxu <dev@howxu.cn>
 */
class PublicModifierMappingTest {
    @Test
    void every_numeric_scope_target_and_operation_maps_smart_values_into_core_effects() {
        var targets = List.of("duration", "energy", "chemical", "heat", "output", "parallelism", "factory_threads", "recipe_threads");
        for (var target : targets) {
            ModifierScope scope = switch (target) {
                case "output" -> ModifierScope.OUTPUT;
                case "parallelism", "factory_threads" -> ModifierScope.MACHINE;
                case "recipe_threads" -> ModifierScope.RECIPE;
                default -> ModifierScope.INPUT;
            };
            var coreTarget = ModifierTarget.parse(target, switch (scope) {
                case INPUT -> "input"; case OUTPUT -> "output"; case MACHINE -> "machine"; case RECIPE -> "recipe";
            });
            for (var operation : ModifierOperation.values()) {
                double expected = switch (operation) { case ADD -> 14D; case MULTIPLY -> 40D; case SUBTRACT -> 6D; case DIVIDE -> 2.5D; };
                var numeric = Modifiers.numeric(target, scope, 4D, operation, false);
                var smart = Modifiers.smart("test", target, scope, false, 0F, 10F, 2F, 6F, operation);
                assertEquals(scope, smart.scope());
                assertEquals(operation, smart.operation());
                assertEquals(4F, smart.mappedValue(5F));
                var mapped = smart.toModifier(5F);
                assertEquals(scope, mapped.scope());
                assertEquals(operation, mapped.operation());
                assertEquals(expected, MachineModifier.apply(List.of(ModifierAdapters.unwrap(numeric)), coreTarget, 10D, false));
                assertEquals(expected, MachineModifier.apply(List.of(ModifierAdapters.unwrap(mapped)), coreTarget, 10D, false));
                var recipeModifiers = Modifiers.recipeModifiers(List.of(mapped));
                if (scope == ModifierScope.MACHINE || scope == ModifierScope.RECIPE) {
                    assertTrue(recipeModifiers.isEmpty());
                } else {
                    assertEquals(expected, Modifiers.apply(recipeModifiers, target,
                            scope == ModifierScope.OUTPUT ? IoDirection.OUTPUT : IoDirection.INPUT, 10D, false));
                    assertEquals(10D, Modifiers.apply(recipeModifiers, target,
                            scope == ModifierScope.OUTPUT ? IoDirection.INPUT : IoDirection.OUTPUT, 10D, false));
                }
            }
        }
    }

    @Test
    void output_resource_scope_normalizes_and_chance_does_not_modify_amount() {
        var smart = Modifiers.smartItem("test", IoDirection.OUTPUT, true, 0F, 10F, 0.5F, 1F, ModifierOperation.MULTIPLY);
        assertEquals(ModifierScope.OUTPUT, smart.scope());
        assertEquals("output", smart.target());
        var mapped = smart.toModifier(0F);
        assertTrue(mapped.affectsChance());
        var modifiers = Modifiers.recipeModifiers(List.of(mapped));
        assertEquals(0.4D, Modifiers.apply(modifiers, "item", IoDirection.OUTPUT, 0.8D, true), 0.000001D);
        assertEquals(10D, Modifiers.apply(modifiers, "item", IoDirection.OUTPUT, 10D, false));
    }
}
