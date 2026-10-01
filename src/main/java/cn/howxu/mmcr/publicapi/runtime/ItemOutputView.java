package cn.howxu.mmcr.publicapi.runtime;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.ApiStatus;
/** Library-produced runtime item output with a copying getter. @author howxu <dev@howxu.cn> */
@ApiStatus.NonExtendable
public interface ItemOutputView extends OutputView { ItemStack stack(); }
