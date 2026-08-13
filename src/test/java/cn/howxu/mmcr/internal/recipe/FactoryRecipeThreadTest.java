package cn.howxu.mmcr.internal.recipe;

import cn.howxu.mmcr.api.recipe.ActiveMachineRecipe;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeCraftingContextPool;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests recipe locking state owned by factory recipe threads.
 *
 * @author howxu <dev@howxu.cn>
 */
class FactoryRecipeThreadTest {
    private static final HolderLookup.Provider EMPTY_LOOKUP =
            HolderLookup.Provider.create(java.util.stream.Stream.empty());

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @AfterEach
    void cleanup() {
        RecipeRegistry.clearForTesting();
    }

    @Test
    void togglingUsesActiveRecipeAndSecondClickUnlocks() {
        MachineRecipe recipe = recipe("mmcr:locked_recipe");
        FactoryRecipeThread thread = FactoryRecipeThread.base(null, new RecipeCraftingContextPool());
        thread.setActiveRecipeForTesting(new ActiveMachineRecipe(recipe));

        assertThat(thread.toggleRecipeLock()).isTrue();
        assertThat(thread.isRecipeLocked()).isTrue();
        assertThat(thread.lockedRecipeId()).isEqualTo(recipe.id());
        assertThat(thread.toggleRecipeLock()).isTrue();
        assertThat(thread.isRecipeLocked()).isFalse();
        assertThat(thread.lockedRecipeId()).isNull();
    }

    @Test
    void noContextAndNoLockDoesNothing() {
        FactoryRecipeThread thread = FactoryRecipeThread.base(null, new RecipeCraftingContextPool());

        assertThat(thread.toggleRecipeLock()).isFalse();
        assertThat(thread.lockedRecipeId()).isNull();
    }

    @Test
    void validLockPersistsWithoutAnActiveRecipe() {
        MachineRecipe recipe = recipe("mmcr:persisted_lock");
        RecipeRegistry.register(recipe);
        FactoryRecipeThread thread = FactoryRecipeThread.base(null, new RecipeCraftingContextPool());
        thread.setLockedRecipeId(recipe.id());

        FactoryRecipeThread restored = saveAndLoad(thread);

        assertThat(restored.lockedRecipeId()).isEqualTo(recipe.id());
    }

    @Test
    void missingRecipeClearsPersistedLock() {
        FactoryRecipeThread thread = FactoryRecipeThread.base(null, new RecipeCraftingContextPool());
        thread.setLockedRecipeId(Identifier.parse("mmcr:removed_recipe"));

        FactoryRecipeThread restored = saveAndLoad(thread);

        assertThat(restored.lockedRecipeId()).isNull();
    }

    private static FactoryRecipeThread saveAndLoad(FactoryRecipeThread thread) {
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, EMPTY_LOOKUP);
        thread.save(output);
        return FactoryRecipeThread.load(TagValueInput.create(ProblemReporter.DISCARDING, EMPTY_LOOKUP,
                output.buildResult()), null, new RecipeCraftingContextPool());
    }

    private static MachineRecipe recipe(String id) {
        return new MachineRecipe(Identifier.parse(id), Identifier.parse("mmcr:test_machine"),
                1, List.of(), List.of());
    }
}
