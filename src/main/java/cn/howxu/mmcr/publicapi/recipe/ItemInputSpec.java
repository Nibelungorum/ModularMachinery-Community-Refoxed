package cn.howxu.mmcr.publicapi.recipe;
import cn.howxu.mmcr.publicapi.recipe.component.ComponentConstraints;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.ApiStatus;
/** Library-produced input declaration. @author howxu <dev@howxu.cn> */
@ApiStatus.NonExtendable
public interface ItemInputSpec { Ingredient ingredient(); int count(); ComponentConstraints components(); float consumeChance(); }
