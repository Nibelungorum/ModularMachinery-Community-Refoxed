package cn.howxu.mmcr.internal.sync;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies optional recipe viewer selection without starting Minecraft.
 *
 * @author howxu <dev@howxu.cn>
 */
class RecipeViewerBackendTest {
    @Test
    void selectsEmiWhenBothViewersArePresent() {
        assertThat(RecipeViewerBackend.select(true, true)).isEqualTo(RecipeViewerBackend.EMI);
    }

    @Test
    void selectsEmiWhenOnlyEmiIsPresent() {
        assertThat(RecipeViewerBackend.select(false, true)).isEqualTo(RecipeViewerBackend.EMI);
    }

    @Test
    void selectsJeiWhenOnlyJeiIsPresent() {
        assertThat(RecipeViewerBackend.select(true, false)).isEqualTo(RecipeViewerBackend.JEI);
    }

    @Test
    void selectsNoneWhenNeitherViewerIsPresent() {
        assertThat(RecipeViewerBackend.select(false, false)).isEqualTo(RecipeViewerBackend.NONE);
    }

    @Test
    void currentReturnsNoneBeforeModListIsInitialized() {
        assertThat(RecipeViewerBackend.current()).isEqualTo(RecipeViewerBackend.NONE);
    }
}
