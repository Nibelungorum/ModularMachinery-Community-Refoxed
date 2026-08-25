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

    @Test
    void changing_the_conflicting_candidate_restarts_its_retry_window() {
        RecipeStartDelay delay = new RecipeStartDelay();

        assertThat(delay.shouldDelay(MMCR.id("broad"), true, 100)).isTrue();
        assertThat(delay.shouldDelay(MMCR.id("specific"), true, 119)).isTrue();
        assertThat(delay.shouldDelay(MMCR.id("specific"), true, 138)).isTrue();
        assertThat(delay.shouldDelay(MMCR.id("specific"), true, 139)).isFalse();
    }

    @Test
    void a_non_conflicting_search_clears_the_candidate_before_a_later_retry() {
        RecipeStartDelay delay = new RecipeStartDelay();

        assertThat(delay.shouldDelay(MMCR.id("broad"), true, 100)).isTrue();
        assertThat(delay.shouldDelay(MMCR.id("broad"), false, 101)).isFalse();
        assertThat(delay.shouldDelay(MMCR.id("broad"), true, 102)).isTrue();
    }
}
