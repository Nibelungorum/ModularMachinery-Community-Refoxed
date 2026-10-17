package cn.howxu.mmcr.internal.recipe;

import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.api.capability.plan.CapabilityResult;

/** Read-only input preflight, distinct from a paid native resource operation.
 * @author howxu <dev@howxu.cn>
 */
public record InputValidationOperation(CapabilityOperation validation) implements CapabilityOperation {
    @Override
    public CapabilityResult commit() {
        return validation.commit();
    }
}
