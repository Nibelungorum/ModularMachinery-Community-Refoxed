package cn.howxu.mmcr.publicapi.recipe;
import cn.howxu.mmcr.publicapi.recipe.component.ComponentConstraints;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.ApiStatus;
/** Library-produced output declaration; stack getter copies. @author howxu <dev@howxu.cn> */
@ApiStatus.NonExtendable
public interface ItemOutputSpec { ItemStack stack(); float chance(); ComponentConstraints components(); }
