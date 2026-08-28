package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.internal.recipe.RecipeSearchContextKey;
import cn.howxu.mmcr.internal.recipe.RecipeStartDelay;
import cn.howxu.mmcr.internal.recipe.FactoryRecipeThread;
import cn.howxu.mmcr.test.RuntimeTestFixtures;
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

    @Test
    void retry_delays_follow_the_five_ten_twenty_forty_eighty_hundred_schedule() {
        FactoryRecipeThread thread = FactoryRecipeThread.simple(RuntimeTestFixtures.controller(MMCR.id("test_cube")));
        RecipeSearchContextKey key = new RecipeSearchContextKey(1L, 1L, 1L, 1L, 1L, 1L, null, 1L);

        thread.recordSearchFailure(key, 0L);
        assertThat(thread.canSearch(4L, key)).isFalse();
        assertThat(thread.canSearch(5L, key)).isTrue();
        thread.recordSearchFailure(key, 5L);
        assertThat(thread.canSearch(14L, key)).isFalse();
        assertThat(thread.canSearch(15L, key)).isTrue();
        thread.recordSearchFailure(key, 15L);
        assertThat(thread.canSearch(34L, key)).isFalse();
        assertThat(thread.canSearch(35L, key)).isTrue();
        thread.recordSearchFailure(key, 35L);
        assertThat(thread.canSearch(74L, key)).isFalse();
        assertThat(thread.canSearch(75L, key)).isTrue();
        thread.recordSearchFailure(key, 75L);
        assertThat(thread.canSearch(154L, key)).isFalse();
        assertThat(thread.canSearch(155L, key)).isTrue();
        thread.recordSearchFailure(key, 155L);
        assertThat(thread.canSearch(254L, key)).isFalse();
        assertThat(thread.canSearch(255L, key)).isTrue();
    }
}
