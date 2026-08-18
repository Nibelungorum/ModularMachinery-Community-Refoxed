package cn.howxu.mmcr.api.recipe.modifier;

import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author howxu <dev@howxu.cn>
 */
class SingleBlockModifierReplacementTest {
    @BeforeAll static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void stores_predicate_name_modifiers_and_descriptive_stack() {
        RecipeModifier modifier = new RecipeModifier("item", RecipeModifier.IOType.INPUT, 2F,
                RecipeModifier.Operation.ADD, false);
        ItemStack stack = new ItemStack(Blocks.GOLD_BLOCK);
        var replacement = new SingleBlockModifierReplacement(
                "speed", new BlockPredicate.OfBlock(Blocks.GOLD_BLOCK), List.of(modifier), stack);

        assertThat(replacement.getModifierName()).isEqualTo("speed");
        assertThat(replacement.getReplacement()).isEqualTo(new BlockPredicate.OfBlock(Blocks.GOLD_BLOCK));
        assertThat(replacement.getModifiers()).containsExactly(modifier);
        assertThat(replacement.getDescriptiveStack()).isSameAs(stack);
    }

    @Test
    void constructor_requires_only_value_state_without_position_or_description() {
        var replacement = new SingleBlockModifierReplacement(
                "speed", new BlockPredicate.Any(), List.of(), ItemStack.EMPTY);

        assertThat(replacement.getModifierName()).isEqualTo("speed");
        assertThat(replacement.getReplacement()).isEqualTo(new BlockPredicate.Any());
        assertThat(replacement.getDescriptionLines()).isEmpty();
    }

    @Test
    void modifier_list_is_not_mutable_through_constructor_input() {
        List<RecipeModifier> modifiers = new ArrayList<>();
        var replacement = new SingleBlockModifierReplacement(
                "speed", new BlockPredicate.Any(), modifiers, ItemStack.EMPTY);
        modifiers.add(new RecipeModifier("item", RecipeModifier.IOType.INPUT, 1F,
                RecipeModifier.Operation.ADD, false));

        assertThat(replacement.getModifiers()).isEmpty();
        assertThatThrownBy(() -> replacement.getModifiers().add(null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void replacements_with_same_values_compare_equal() {
        RecipeModifier modifier = new RecipeModifier("item", RecipeModifier.IOType.OUTPUT, 2F,
                RecipeModifier.Operation.MULTIPLY, false);
        var first = new SingleBlockModifierReplacement(
                "speed", new BlockPredicate.OfBlock(Blocks.GOLD_BLOCK), List.of(modifier), new ItemStack(Blocks.GOLD_BLOCK));
        var second = new SingleBlockModifierReplacement(
                "speed", new BlockPredicate.OfBlock(Blocks.GOLD_BLOCK), List.of(modifier), new ItemStack(Blocks.GOLD_BLOCK));

        assertThat(first).isEqualTo(second);
        assertThat(first).hasSameHashCodeAs(second);
    }
}
