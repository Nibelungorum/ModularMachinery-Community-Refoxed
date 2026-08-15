package cn.howxu.mmcr.internal.block;

import cn.howxu.mmcr.util.IOType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.fluids.FluidUtil;

/**
 * @author howxu <dev@howxu.cn>
 */
public final class FluidHatchBucketInteraction {

    private static final int BUCKET_VOLUME = 1_000;

    private FluidHatchBucketInteraction() {
    }

    public static Result tryTransfer(FluidTank tank, IOType ioType, ItemStack container) {
        if (container.isEmpty() || container.getCount() != 1) return Result.failure(container);
        return FluidUtil.getFluidHandler(container.copy()).map(handler -> switch (ioType) {
            case INPUT -> tryEmptyIntoTank(tank, handler, container);
            case OUTPUT -> tryFillFromTank(tank, handler, container);
        }).orElseGet(() -> Result.failure(container));
    }

    private static Result tryEmptyIntoTank(FluidTank tank, IFluidHandlerItem container, ItemStack original) {
        FluidStack fluid = container.drain(BUCKET_VOLUME, IFluidHandler.FluidAction.SIMULATE);
        if (fluid.getAmount() != BUCKET_VOLUME
                || tank.fill(fluid, IFluidHandler.FluidAction.SIMULATE) != BUCKET_VOLUME) {
            return Result.failure(original);
        }
        container.drain(BUCKET_VOLUME, IFluidHandler.FluidAction.EXECUTE);
        tank.fill(fluid, IFluidHandler.FluidAction.EXECUTE);
        return Result.success(container.getContainer());
    }

    private static Result tryFillFromTank(FluidTank tank, IFluidHandlerItem container, ItemStack original) {
        FluidStack fluid = tank.drain(BUCKET_VOLUME, IFluidHandler.FluidAction.SIMULATE);
        if (fluid.getAmount() != BUCKET_VOLUME
                || container.fill(fluid, IFluidHandler.FluidAction.SIMULATE) != BUCKET_VOLUME) {
            return Result.failure(original);
        }
        tank.drain(BUCKET_VOLUME, IFluidHandler.FluidAction.EXECUTE);
        container.fill(fluid, IFluidHandler.FluidAction.EXECUTE);
        return Result.success(container.getContainer());
    }

    public record Result(boolean successful, ItemStack container) {
        static Result success(ItemStack container) {
            return new Result(true, container);
        }

        static Result failure(ItemStack container) {
            return new Result(false, container);
        }
    }
}
