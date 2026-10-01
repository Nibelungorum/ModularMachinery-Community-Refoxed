package cn.howxu.mmcr.publicapi.recipe.extension;
/** Typed requested slot action, validated when prepared by the core capability.
 * @author howxu <dev@howxu.cn> */
public record ResourceAction<R>(int slot, R resource, long amount, boolean insert) {}
