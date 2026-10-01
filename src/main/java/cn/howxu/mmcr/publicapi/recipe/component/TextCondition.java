package cn.howxu.mmcr.publicapi.recipe.component;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.ApiStatus;
/** Library-produced text condition; construction and reads snapshot siblings and supported nested
 * Component contents. Editing a source or returned Component does not change matching.
 * @author howxu <dev@howxu.cn> */
@ApiStatus.NonExtendable
public interface TextCondition extends ComponentCondition { Component value(); TextMatchMode mode(); }
