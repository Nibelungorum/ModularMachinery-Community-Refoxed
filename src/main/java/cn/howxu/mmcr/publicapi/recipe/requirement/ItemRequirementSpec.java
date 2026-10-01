package cn.howxu.mmcr.publicapi.recipe.requirement;
import cn.howxu.mmcr.publicapi.recipe.component.ComponentConstraints;
import com.mojang.serialization.DynamicOps;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;
/** Library-produced item requirement; stack getters copy. @author howxu <dev@howxu.cn> */
@ApiStatus.NonExtendable
public interface ItemRequirementSpec extends RequirementSpec {
    @Nullable Ingredient item(); @Nullable Ingredient ingredient(); int count(); ItemStack stack(); float chance();
    ComponentConstraints components(); float consumeChance(); ItemStack resolvedStack(); ItemStack stack(DynamicOps<?> ops);
}
