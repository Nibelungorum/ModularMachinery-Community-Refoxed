package cn.howxu.mmcr.api.capability.plan;

/**
 * A capability operation that is safe for the built-in no-transaction runtime path.
 *
 * @author howxu <dev@howxu.cn>
 */
@FunctionalInterface
public interface NativeCapabilityOperation extends CapabilityOperation {
    @Override
    CapabilityResult commit();
}
