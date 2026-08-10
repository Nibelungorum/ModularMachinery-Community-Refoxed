package cn.howxu.mmcr.api.recipe.component;

import com.google.gson.JsonArray;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ComponentPredicateTest {

    @Test
    void listMatchesWhenGreedyFirstCandidateWouldBlockAnotherRequirement() {
        JsonArray values = new JsonArray();
        values.add(1);
        values.add(2);
        ComponentPredicate predicate = ComponentPredicate.list(List.of(
                ComponentPredicate.range(1, 2),
                ComponentPredicate.exact(new Dynamic<>(JsonOps.INSTANCE, new JsonPrimitive(1)))
        ));

        assertThat(predicate.matches(new Dynamic<>(JsonOps.INSTANCE, values))).isTrue();
    }

    @Test
    void exactPredicateExportsCustomNamePatch() {
        var predicates = new DataComponentPredicateSet(Map.of(
                DataComponents.CUSTOM_NAME,
                ComponentPredicate.exact(new Dynamic<>(JsonOps.INSTANCE,
                        DataComponents.CUSTOM_NAME.codec().encodeStart(JsonOps.INSTANCE,
                                Component.literal("Required")).getOrThrow()))));

        assertThat(predicates.exactPatch()).isPresent();
        assertThat(predicates.exactPatch().orElseThrow().getPatch(DataComponents.CUSTOM_NAME).orElseThrow())
                .isEqualTo(Component.literal("Required"));
    }

    @Test
    void nonExactPredicateCannotExportPatch() {
        var predicates = new DataComponentPredicateSet(Map.of(
                DataComponents.MAX_STACK_SIZE,
                ComponentPredicate.range(1, 4)));

        assertThat(predicates.exactPatch()).isEmpty();
        assertThat(predicates.hasNonExactValues()).isTrue();
    }

    @Test
    void exactEnchantmentPredicateExportsThroughComponentCodec() {
        var stack = new net.minecraft.world.item.ItemStack(Items.DIAMOND_SWORD);
        var predicates = new DataComponentPredicateSet(Map.of(
                DataComponents.ENCHANTMENTS,
                ComponentPredicate.exact(new Dynamic<>(JsonOps.INSTANCE,
                        DataComponents.ENCHANTMENTS.codec().encodeStart(JsonOps.INSTANCE,
                                stack.get(DataComponents.ENCHANTMENTS)).getOrThrow()))));

        assertThat(predicates.exactPatch()).isPresent();
        assertThat(predicates.exactPatch().orElseThrow().getPatch(DataComponents.ENCHANTMENTS).orElseThrow())
                .isEqualTo(stack.get(DataComponents.ENCHANTMENTS));
    }
}
