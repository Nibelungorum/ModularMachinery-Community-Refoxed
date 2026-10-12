package cn.howxu.mmcr.internal.recipe;

import cn.howxu.mmcr.internal.recipe.CompiledRecipeProgram.Stage;
import cn.howxu.mmcr.internal.recipe.CompiledRecipeProgram.VersionStamp;
import net.minecraft.resources.ResourceLocation;

/**
 * Compiles already captured pure rules without consulting the world or retained quantity snapshots.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class RecipeProgramCompiler {
    public CompiledRecipeProgram compile(ResourceLocation recipeId, int duration, long parallelism,
                                         VersionStamp versions, Stage start, Stage tick,
                                         Stage finish, boolean mainPreparation) {
        return new CompiledRecipeProgram(recipeId, duration, parallelism, versions, start, tick, finish,
                mainPreparation || start.requiresCompatibility() || tick.requiresCompatibility()
                        || finish.requiresCompatibility());
    }
}
