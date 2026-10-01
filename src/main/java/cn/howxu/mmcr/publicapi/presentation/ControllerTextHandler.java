package cn.howxu.mmcr.publicapi.presentation;

/** User-implementable text callback. @author howxu <dev@howxu.cn> */
@FunctionalInterface
public interface ControllerTextHandler { void apply(ControllerTextContext context); }
