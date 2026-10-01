package cn.howxu.mmcr.publicapi.presentation;

import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.ApiStatus;

/** MMCR-provided display icon; stack returns a copy. @author howxu <dev@howxu.cn> */
@ApiStatus.NonExtendable
public interface DisplayStackView { ItemStack stack(); }
