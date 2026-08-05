package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.api.recipe.helper.CraftCheck;
import cn.howxu.mmcr.api.recipe.helper.ProcessingComponent;
import cn.howxu.mmcr.internal.tile.FluidHatchBlockEntity;
import cn.howxu.mmcr.util.IOType;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author howxu <dev@howxu.cn>
 */
public record FluidRequirement(FluidIngredient fluid, int amount, @Nullable String tag, IOType ioType) implements MachineRequirement {

    @Override
    public String type() {
        return "fluid";
    }

    @Override
    public String describe() {
        return amount + "mb " + fluid.fluids().stream()
                .findFirst()
                .map(h -> h.value().builtInRegistryHolder().getRegisteredName())
                .orElseGet(fluid::toString);
    }

    @Override
    public boolean matches(ProcessingComponent component) {
        if (!component.matchesTag(tag)) return false;
        return component.getContainer() instanceof FluidHatchBlockEntity hatch && hatch.ioType() == ioType;
    }

    @Override
    public CraftCheck simulate(RecipeCraftingContext context) {
        return ioType == IOType.INPUT ? simulateInput(context) : simulateOutput(context);
    }

    @Override
    public boolean commit(RecipeCraftingContext context) {
        List<FluidRoute> route = context.route(this, FluidRoute.class);
        if (route == null) return false;
        int remaining = amount;
        for (FluidRoute entry : route) {
            if (remaining <= 0) return true;
            if (ioType == IOType.INPUT) {
                int drained = entry.handler.drain(entry.stack.copyWithAmount(Math.min(entry.amount, remaining)), IFluidHandler.FluidAction.EXECUTE).getAmount();
                remaining -= drained;
            } else {
                FluidStack stack = sampleFluid(Math.min(entry.amount, remaining));
                int filled = entry.handler.fill(stack, IFluidHandler.FluidAction.EXECUTE);
                remaining -= filled;
            }
        }
        return remaining <= 0;
    }

    private CraftCheck simulateInput(RecipeCraftingContext context) {
        List<FluidRoute> route = new ArrayList<>();
        List<String> searched = new ArrayList<>();
        int remaining = amount;
        for (ProcessingComponent component : context.componentsMatching(this)) {
            if (!(component.getContainer() instanceof FluidHatchBlockEntity hatch)) continue;
            IFluidHandler handler = hatch.getFluidHandler(null);
            int available = 0;
            for (int tank = 0; tank < handler.getTanks() && remaining > 0; tank++) {
                FluidStack stack = handler.getFluidInTank(tank);
                if (!fluid.test(stack)) continue;
                FluidStack request = stack.copyWithAmount(Math.min(remaining, stack.getAmount()));
                int drained = Math.min(remaining, handler.drain(request, IFluidHandler.FluidAction.SIMULATE).getAmount());
                if (drained <= 0) continue;
                available += drained;
                remaining -= drained;
                route.add(new FluidRoute(handler, request.copyWithAmount(drained), drained));
            }
            searched.add(hatch.getBlockPos() + ":fluid_input_hatch=" + available);
            if (remaining <= 0) {
                context.route(this, route);
                return CraftCheck.success();
            }
        }
        return CraftCheck.failure("Missing " + describe() + " (short " + remaining + "; searched " + searched + ")");
    }

    private CraftCheck simulateOutput(RecipeCraftingContext context) {
        List<FluidRoute> route = new ArrayList<>();
        List<String> searched = new ArrayList<>();
        int remaining = amount;
        for (ProcessingComponent component : context.componentsMatching(this)) {
            if (!(component.getContainer() instanceof FluidHatchBlockEntity hatch)) continue;
            IFluidHandler handler = hatch.getFluidHandler(null);
            int filled = handler.fill(sampleFluid(remaining), IFluidHandler.FluidAction.SIMULATE);
            if (filled > 0) {
                route.add(new FluidRoute(handler, sampleFluid(filled), filled));
                remaining -= filled;
            }
            searched.add(hatch.getBlockPos() + ":fluid_output_hatch=" + filled);
            if (remaining <= 0) {
                context.route(this, route);
                return CraftCheck.success();
            }
        }
        return CraftCheck.failure("Missing output space for " + describe() + " (short " + remaining + "; searched " + searched + ")");
    }

    private FluidStack sampleFluid(int stackAmount) {
        return fluid.fluids().stream()
                .findFirst()
                .map(holder -> new FluidStack(holder.value(), stackAmount))
                .orElse(FluidStack.EMPTY);
    }

    public record FluidRoute(IFluidHandler handler, FluidStack stack, int amount) {
    }
}
