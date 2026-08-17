package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.internal.multiblock.ComponentClaimPolicy;

/**
 * @author howxu <dev@howxu.cn>
 */
public interface MachineComponentTile {

    MachineComponent provideComponent();

    default ComponentClaimPolicy claimPolicy() {
        return ComponentClaimPolicy.EXCLUSIVE;
    }
}
