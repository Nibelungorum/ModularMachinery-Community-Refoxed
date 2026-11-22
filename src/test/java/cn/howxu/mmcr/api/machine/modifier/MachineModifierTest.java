package cn.howxu.mmcr.api.machine.modifier;

import cn.howxu.mmcr.api.recipe.IntegrationTypeHelper;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import com.mojang.serialization.JsonOps;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.NbtOps;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class MachineModifierTest {

    @Test
    void numeric_modifier_rejects_invalid_target_scope_and_chance_combinations() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> MachineModifier.numeric("energy", "output", 1D, "multiply", false));
        assertThatIllegalArgumentException().isThrownBy(
                () -> MachineModifier.numeric("parallelism", "machine", 1D, "add", true));
        assertThatIllegalArgumentException().isThrownBy(
                () -> MachineModifier.numeric("duration", "input", Double.NaN, "multiply", false));
    }

    @Test
    void relative_changes_use_the_original_base_before_rounding_or_direct_multipliers() {
        var ratio = MachineModifier.numeric("duration", "input", -0.1, "add_ratio", false);
        var flat = MachineModifier.numeric("duration", "input", -10, "add", false);
        var multiply = MachineModifier.numeric("duration", "input", 0.5, "multiply", false);
        List<MachineModifier> mixed = List.of(flat, ratio, multiply);
        assertThat(MachineModifier.apply(mixed, ModifierTarget.DURATION, 100, false)).isEqualTo(40);
        assertThat(IntegrationTypeHelper.applyDuration(MachineModifier.recipeModifiers(mixed), 100)).isEqualTo(40);
        var energy = MachineModifier.numeric("energy", "input", -0.1, "add_ratio", false);
        var adjustments = MachineModifier.recipeModifiers(List.of(energy, energy));
        assertThat(IntegrationTypeHelper.applyEnergy(adjustments, 100)).isEqualTo(80);
        var json = RecipeModifier.CODEC.encodeStart(JsonOps.INSTANCE, adjustments.getFirst()).getOrThrow();
        var decoded = RecipeModifier.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertThat(IntegrationTypeHelper.applyEnergy(List.of(decoded), 100)).isEqualTo(90);
    }

    @Test
    void old_float_operands_and_new_double_ratios_keep_their_wire_types_and_energy_rounding() {
        for (var operation : RecipeModifier.Operation.values()) {
            var adjustment = new RecipeModifier("energy", RecipeModifier.IOType.INPUT,
                    operation == RecipeModifier.Operation.ADD_RATIO ? -0.1 : 0.9, operation, false);
            var encoded = (CompoundTag) RecipeModifier.CODEC.encodeStart(NbtOps.INSTANCE, adjustment).getOrThrow();
            Class<?> type = operation == RecipeModifier.Operation.ADD_RATIO ? DoubleTag.class : FloatTag.class;
            assertThat(encoded.get("multiplier")).isInstanceOf(type);
            assertThat(adjustment.serializeNbt().get("value")).isInstanceOf(type);
            assertThat(RecipeModifier.deserializeNbt(adjustment.serializeNbt())).isEqualTo(adjustment);
            assertThat(RecipeModifier.CODEC.parse(NbtOps.INSTANCE, encoded).getOrThrow()).isEqualTo(adjustment);
        }
        var ratio = MachineModifier.numeric("energy", "input", -0.1, "add_ratio", false);
        var multiplier = MachineModifier.numeric("energy", "input", 0.9, "multiply", false);
        assertThat(IntegrationTypeHelper.applyEnergy(MachineModifier.recipeModifiers(List.of(multiplier)), 100)).isEqualTo(89);
        var best = MachineModifier.bestOf(List.of(ratio), List.of(multiplier));
        assertThat(IntegrationTypeHelper.applyEnergy(MachineModifier.recipeModifiers(best), 100)).isEqualTo(89);
    }

    @Test
    void unique_groups_are_not_cherry_picked_and_appliers_use_the_same_winner_at_each_base() {
        List<MachineModifier> fixed = List.of(MachineModifier.numeric("output", "output", 10, "add", false),
                MachineModifier.numeric("output", "output", 0.5, "multiply", false));
        List<MachineModifier> relative = List.of(MachineModifier.numeric("output", "output", -0.2, "add_ratio", false));
        var best = MachineModifier.bestOf(fixed, relative);
        var adjustments = MachineModifier.recipeModifiers(best);
        var applier = new RecipeModifier.ModifierApplier();
        adjustments.forEach(mod -> RecipeModifier.applyValueToApplier(applier, mod));
        for (int base : List.of(10, 100)) {
            float expected = base == 10 ? 10 : 80;
            assertThat(applier.apply(base, RecipeModifier.IOType.OUTPUT)).isEqualTo(expected);
            assertThat(IntegrationTypeHelper.applyItemOutput(adjustments, base)).isEqualTo(expected);
            assertThat(MachineModifier.apply(best, ModifierTarget.OUTPUT, base, false)).isEqualTo(expected);
        }
    }

    @Test
    void zero_cancels_an_overflowed_product_in_direct_and_cached_recipe_modifiers() {
        for (var io : RecipeModifier.IOType.values()) {
            var huge = new RecipeModifier("", io, Float.MAX_VALUE, RecipeModifier.Operation.MULTIPLY, false);
            // Nine finite float factors overflow even the double evaluator.
            var modifiers = new ArrayList<>(Collections.nCopies(9, huge));
            var applier = new RecipeModifier.ModifierApplier();
            modifiers.forEach(mod -> RecipeModifier.applyValueToApplier(applier, mod));

            assertThat(RecipeModifier.applyModifiers(modifiers, "", io, 0F, false)).isZero();
            assertThat(RecipeModifier.applyModifiers(modifiers, "", io, 0D, false)).isZero();
            assertThat(applier.apply(0F, io)).isZero();

            var zero = new RecipeModifier("", io, 0F, RecipeModifier.Operation.MULTIPLY, false);
            modifiers.add(zero);
            RecipeModifier.applyValueToApplier(applier, zero);
            assertThat(RecipeModifier.applyModifiers(modifiers, "", io, 100F, false)).isZero();
            assertThat(RecipeModifier.applyModifiers(modifiers, "", io, 100D, false)).isZero();
            assertThat(applier.apply(100F, io)).isZero();

            var scaled = huge.multiply(Float.MAX_VALUE).multiply(0F);
            assertThat(RecipeModifier.applyModifiers(List.of(scaled), "", io, 100F, false)).isZero();
        }
    }

    @Test
    void counted_units_keep_absolute_amounts_and_boolean_vetoes_independent_of_block_multiplier_mode() {
        var sub = MachineModifier.numeric("output", "output", 3, "subtract", false);
        var div = MachineModifier.numeric("output", "output", 2, "divide", false);
        var ratio = MachineModifier.numeric("output", "output", -0.1, "add_ratio", true);
        var veto = MachineModifier.parallelized(false);
        List<MachineModifier> effects = List.of(sub, div, ratio, veto);
        assertThat(ModifierStacking.MULTIPLY.blocks(effects, 0)).isEmpty();
        assertThat(ModifierStacking.LEGACY.items(effects, 0)).isEmpty();
        var large = ModifierStacking.LEGACY.items(List.of(
                MachineModifier.numeric("factory_threads", "machine", 2, "multiply", false)), Long.MAX_VALUE);
        assertThat(MachineModifier.apply(large, ModifierTarget.FACTORY_THREADS, 1, false)).isEqualTo(Double.MAX_VALUE);
        var first = ModifierStacking.MULTIPLY.blocks(effects, 2);
        var second = ModifierStacking.ADD.items(effects, 2);
        for (var folded : List.of(first, second)) {
            assertThat(MachineModifier.apply(folded, ModifierTarget.OUTPUT, 46, false)).isEqualTo(10);
            assertThat(folded).containsOnlyOnce(veto);
        }
        assertThat(IntegrationTypeHelper.applyItemOutputChance(MachineModifier.recipeModifiers(first), 1)).isEqualTo(0.81F);
        assertThat(IntegrationTypeHelper.applyItemOutputChance(MachineModifier.recipeModifiers(second), 1)).isEqualTo(0.8F);
        var best = MachineModifier.bestOf(first, List.of(MachineModifier.parallelized(true)));
        assertThat(best).contains(veto).doesNotContain(MachineModifier.parallelized(true));
    }

    @Test
    void parallelized_modifier_is_distinct_from_numeric_modifiers() {
        assertThat(MachineModifier.parallelized(false).value()).isFalse();
        assertThatIllegalArgumentException().isThrownBy(
                () -> MachineModifier.numeric("parallelized", "recipe", 1D, "add", false));
    }
}
