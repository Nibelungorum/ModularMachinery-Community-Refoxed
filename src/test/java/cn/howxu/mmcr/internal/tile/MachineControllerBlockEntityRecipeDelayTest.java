package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.internal.recipe.RecipeStartDelay;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Final recipe start retry-delay behavior tests.
 *
 * @author howxu <dev@howxu.cn>
 */
class MachineControllerBlockEntityRecipeDelayTest {
    @Test
    void conflict_prone_recipe_retries_after_the_specific_candidate_window() {
        RecipeStartDelay delay = new RecipeStartDelay();

        assertThat(delay.shouldDelay(MMCR.id("broad"), true, 100)).isTrue();
        assertThat(delay.shouldDelay(MMCR.id("broad"), true, 119)).isTrue();
        assertThat(delay.shouldDelay(MMCR.id("broad"), true, 120)).isFalse();
        assertThat(delay.shouldDelay(MMCR.id("broad"), false, 121)).isFalse();
    }
}
