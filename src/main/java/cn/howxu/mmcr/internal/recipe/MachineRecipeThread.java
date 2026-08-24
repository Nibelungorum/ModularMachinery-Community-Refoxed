package cn.howxu.mmcr.internal.recipe;

import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;

/**
 * Recipe thread used by a normal machine controller.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class MachineRecipeThread extends RecipeThread {
    public MachineRecipeThread(MachineControllerBlockEntity controller) {
        super(controller);
    }

    @Override
    protected void onStarted() {
    }

    @Override
    protected void onFinished() {
    }
}
