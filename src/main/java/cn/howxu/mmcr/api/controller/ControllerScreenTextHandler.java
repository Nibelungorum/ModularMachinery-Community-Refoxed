package cn.howxu.mmcr.api.controller;

/**
 * Applies one controller screen text contribution for a runtime context.
 *
 * @author howxu <dev@howxu.cn>
 */
@FunctionalInterface
public interface ControllerScreenTextHandler {
    void apply(ControllerRuntimeContext context);
}
