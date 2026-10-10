package cn.howxu.mmcr.api.capability.transfer;

import cn.howxu.mmcr.util.IOType;
import cn.howxu.mmcr.compat.mekanism.MekanismBridge;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;

/**
 * Moves a carried container's resources in the port's external IO direction.
 * The port handler must already be restricted to the selected tank.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class ContainerResourceTransfer {
    private ContainerResourceTransfer() {
    }

    public static int transfer(IOType ioType, IFluidHandler container, IFluidHandler port, boolean simulate) {
        if (ioType == null || container == null || port == null) return 0;
        return ioType == IOType.INPUT
                ? FluidUtil.tryFluidTransfer(port, container, Integer.MAX_VALUE, !simulate).getAmount()
                : FluidUtil.tryFluidTransfer(container, port, Integer.MAX_VALUE, !simulate).getAmount();
    }

    /** Prepares every container on a copy before committing the whole cursor stack. */
    public static Result transferCarried(IOType ioType, ItemStack carried, IFluidHandler port, boolean simulate) {
        if (ioType == null || carried.isEmpty() || port == null) return new Result(0, carried);
        IFluidHandlerItem container = MekanismBridge.get().manualFluidContainerHandler(
                FluidUtil.getFluidHandler(carried.copyWithCount(1)).orElse(null));
        return transferCarried(ioType, carried, container, port, simulate);
    }

    static Result transferCarried(IOType ioType, ItemStack carried, IFluidHandlerItem container,
                                  IFluidHandler port, boolean simulate) {
        if (container == null) return new Result(0, carried);
        FluidStack resource;
        if (ioType == IOType.INPUT) {
            resource = FluidStack.EMPTY;
            for (int tank = 0; tank < container.getTanks(); tank++) {
                FluidStack candidate = container.getFluidInTank(tank);
                if (candidate.isEmpty() || !resource.isEmpty()
                        && !FluidStack.isSameFluidSameComponents(resource, candidate)) continue;
                int remaining = port.fill(candidate.copyWithAmount(Integer.MAX_VALUE), IFluidHandler.FluidAction.SIMULATE)
                        - resource.getAmount();
                if (remaining <= 0) continue;
                FluidStack extracted = container.drain(candidate.copyWithAmount(remaining), IFluidHandler.FluidAction.EXECUTE);
                if (extracted.isEmpty()) continue;
                resource = resource.isEmpty() ? extracted : resource.copyWithAmount(resource.getAmount() + extracted.getAmount());
            }
        } else {
            resource = port.drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.SIMULATE);
            int accepted = container.fill(resource, IFluidHandler.FluidAction.EXECUTE);
            if (accepted == 0) return new Result(0, carried);
            resource = resource.copyWithAmount(accepted);
        }
        if (resource.isEmpty()) return new Result(0, carried);
        ItemStack changed = container.getContainer();
        long total = (long) resource.getAmount() * carried.getCount();
        if (total > Integer.MAX_VALUE) return new Result(0, carried);
        resource = resource.copyWithAmount((int) total);
        int available = ioType == IOType.INPUT ? port.fill(resource, IFluidHandler.FluidAction.SIMULATE)
                : port.drain(resource, IFluidHandler.FluidAction.SIMULATE).getAmount();
        if (available != total) {
            return new Result(0, carried);
        }
        if (!changed.isEmpty()) changed.setCount(changed.getCount() * carried.getCount());
        if (simulate) return new Result((int) total, changed);
        int moved = ioType == IOType.INPUT ? port.fill(resource, IFluidHandler.FluidAction.EXECUTE)
                : port.drain(resource, IFluidHandler.FluidAction.EXECUTE).getAmount();
        return new Result(moved, changed);
    }

    /** Cursor result after a successful native transfer. @author howxu <dev@howxu.cn> */
    public record Result(int amount, ItemStack carried) {}
}
