package cn.howxu.mmcr.mixin.compat.ae2lt;

import com.moakiee.ae2lt.crafting.timewheel.Ae2LtTimeWheelCraftingCpuLogic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** @author HowXu {@literal <dev@howxu.cn>} */
@Mixin(targets = "com.moakiee.ae2lt.crafting.timewheel.Ae2LtTimeWheelCraftingCpuLogic$SingleTaskBatchJobView", remap = false)
public interface TimeWheelJobAccessor {
    @Accessor("this$0") Ae2LtTimeWheelCraftingCpuLogic mmcr$timeWheel();
}
