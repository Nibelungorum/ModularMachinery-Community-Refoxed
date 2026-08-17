package cn.howxu.mmcr.api.recipe.modifier;

import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
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
    void stores_position_predicate_and_modifiers() {
        RecipeModifier modifier = new RecipeModifier("item", RecipeModifier.IOType.INPUT, 2F,
                RecipeModifier.Operation.ADD, false);
        BlockPos pos = new BlockPos(1, 2, 3);
        var replacement = new SingleBlockModifierReplacement(
                "speed", pos, new BlockPredicate.OfBlock(Blocks.GOLD_BLOCK),
                List.of(modifier), "speed", ItemStack.EMPTY);

        assertThat(replacement.getPos()).isEqualTo(pos);
        assertThat(replacement.getReplacement()).isEqualTo(new BlockPredicate.OfBlock(Blocks.GOLD_BLOCK));
        assertThat(replacement.getModifiers()).containsExactly(modifier);
    }

    @Test
    void set_pos_returns_same_replacement_and_rejects_null() {
        var replacement = new SingleBlockModifierReplacement(
                "speed", new BlockPos(1, 0, 0), new BlockPredicate.Any(),
                List.of(), "", ItemStack.EMPTY);

        assertThat(replacement.setPos(new BlockPos(2, 0, 0))).isSameAs(replacement);
        assertThat(replacement.getPos()).isEqualTo(new BlockPos(2, 0, 0));
        assertThatThrownBy(() -> replacement.setPos(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void modifier_list_is_not_mutable_through_constructor_input() {
        List<RecipeModifier> modifiers = new ArrayList<>();
        var replacement = new SingleBlockModifierReplacement(
                "speed", BlockPos.ZERO, new BlockPredicate.Any(),
                modifiers, "", ItemStack.EMPTY);
        modifiers.add(new RecipeModifier("item", RecipeModifier.IOType.INPUT, 1F,
                RecipeModifier.Operation.ADD, false));

        assertThat(replacement.getModifiers()).isEmpty();
        assertThatThrownBy(() -> replacement.getModifiers().add(null))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
