package cn.howxu.mmcr.api.recipe;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class RecipeCraftingContextTest {

    @Test
    void doesNotCacheControllerLevelBecauseRestoredContextsMayBeCreatedBeforeLevelBinding() {
        boolean cachesLevel = Arrays.stream(RecipeCraftingContext.class.getDeclaredFields())
                .anyMatch(field -> field.getName().equals("level"));

        assertThat(cachesLevel).isFalse();
    }
}
