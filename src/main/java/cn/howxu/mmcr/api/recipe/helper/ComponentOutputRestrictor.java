package cn.howxu.mmcr.api.recipe.helper;

public abstract class ComponentOutputRestrictor {

    public abstract int getRestrictorPriority();

    public int modifyAmount(long groupId, int amount) {
        return amount;
    }
}
