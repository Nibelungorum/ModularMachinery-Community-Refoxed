package cn.howxu.mmcr.publicapi.runtime;

/** Immutable aggregate resource amount. @author howxu <dev@howxu.cn> */
public record ResourceAmount<R>(R resource, long amount) {}
