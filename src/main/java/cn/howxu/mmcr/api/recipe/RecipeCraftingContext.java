package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.api.recipe.helper.EnergyRecipeIo;
import cn.howxu.mmcr.api.recipe.helper.ProcessingComponent;
import cn.howxu.mmcr.internal.tile.EnergyInputHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.FluidInputHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.FluidOutputHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemInputBusBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemOutputBusBlockEntity;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.List;

public final class RecipeCraftingContext {

    private final MachineControllerBlockEntity controller;

    private List<ItemInputRoute> itemInputRoutes = List.of();
    private List<ItemOutputRoute> itemOutputRoutes = List.of();
    private List<FluidInputRoute> fluidInputRoutes = List.of();
    private List<FluidOutputRoute> fluidOutputRoutes = List.of();

    public RecipeCraftingContext(MachineControllerBlockEntity controller) {
        this.controller = controller;
    }

    public boolean ioTick(MachineRecipe recipe) {
        for (MachineIngredient ingredient : recipe.inputs()) {
            if (!(ingredient instanceof MachineIngredient.EnergyIngredient energy)) continue;

            List<EnergyInputHatchBlockEntity> hatches = liveEnergyInputs();
            if (!EnergyRecipeIo.consumeInputs(energyStorages(hatches), energy.fePerTick(), 1)) {
                return false;
            }
        }
        return true;
    }

    public boolean simulateInputs(MachineRecipe recipe) {
        itemInputRoutes = new ArrayList<>();
        fluidInputRoutes = new ArrayList<>();

        for (MachineIngredient ingredient : recipe.inputs()) {
            if (ingredient instanceof MachineIngredient.ItemIngredient item) {
                List<BusWithSlots> buses = new ArrayList<>();
                int available = 0;
                for (ItemInputBusBlockEntity bus : liveComponents(ItemInputBusBlockEntity.class)) {
                    IItemHandler handler = bus.getItemHandler(null);
                    buses.add(new BusWithSlots(handler));
                    for (int slot = 0; slot < handler.getSlots(); slot++) {
                        ItemStack stack = handler.getStackInSlot(slot);
                        if (item.item().test(stack)) {
                            available += stack.getCount();
                        }
                    }
                }
                if (available < item.count()) {
                    return false;
                }
                itemInputRoutes.add(new ItemInputRoute(buses, item.count()));
            } else if (ingredient instanceof MachineIngredient.FluidIngredient fluid) {
                List<HatchWithTank> hatches = new ArrayList<>();
                int available = 0;
                for (FluidInputHatchBlockEntity hatch : liveComponents(FluidInputHatchBlockEntity.class)) {
                    IFluidHandler handler = hatch.getFluidHandler(null);
                    FluidStack tank = handler.getFluidInTank(0);
                    if (fluid.fluid().test(tank)) {
                        available += tank.getAmount();
                        hatches.add(new HatchWithTank(handler));
                    }
                }
                if (available < fluid.amount()) {
                    return false;
                }
                fluidInputRoutes.add(new FluidInputRoute(hatches, fluid.amount()));
            } else if (ingredient instanceof MachineIngredient.EnergyIngredient energy) {
                List<EnergyInputHatchBlockEntity> hatches = liveEnergyInputs();
                if (!EnergyRecipeIo.canConsumeInputs(energyStorages(hatches), energy.fePerTick(), 1)) {
                    return false;
                }
            }
        }
        return true;
    }

    public boolean simulateOutputs(MachineRecipe recipe) {
        itemOutputRoutes = new ArrayList<>();

        for (ItemStack output : recipe.outputs()) {
            List<BusWithSlots> buses = new ArrayList<>();
            ItemStack remaining = output.copy();
            for (ItemOutputBusBlockEntity bus : liveComponents(ItemOutputBusBlockEntity.class)) {
                IItemHandler handler = bus.getItemHandler(null);
                buses.add(new BusWithSlots(handler));
                for (int slot = 0; slot < handler.getSlots() && !remaining.isEmpty(); slot++) {
                    remaining = handler.insertItem(slot, remaining, true);
                }
            }
            if (!remaining.isEmpty()) {
                return false;
            }
            itemOutputRoutes.add(new ItemOutputRoute(buses, output));
        }
        return true;
    }

    public boolean startCrafting(MachineRecipe recipe) {
        return commitInputs(recipe);
    }

    public boolean finishCrafting(MachineRecipe recipe) {
        return commitOutputs(recipe);
    }

    public boolean commitInputs(MachineRecipe recipe) {
        int itemIdx = 0;
        int fluidIdx = 0;
        for (MachineIngredient ingredient : recipe.inputs()) {
            if (ingredient instanceof MachineIngredient.ItemIngredient item) {
                ItemInputRoute route = itemInputRoutes.get(itemIdx++);
                int left = item.count();
                for (BusWithSlots bus : route.buses()) {
                    if (left <= 0) break;
                    IItemHandler handler = bus.handler();
                    for (int slot = 0; slot < handler.getSlots() && left > 0; slot++) {
                        ItemStack stack = handler.getStackInSlot(slot);
                        if (item.item().test(stack)) {
                            int taken = Math.min(left, stack.getCount());
                            handler.extractItem(slot, taken, false);
                            left -= taken;
                        }
                    }
                }
                if (left > 0) {
                    return false;
                }
            } else if (ingredient instanceof MachineIngredient.FluidIngredient fluid) {
                FluidInputRoute route = fluidInputRoutes.get(fluidIdx++);
                int drained = 0;
                for (HatchWithTank hatch : route.hatches()) {
                    if (drained >= fluid.amount()) break;
                    int remaining = fluid.amount() - drained;
                    FluidStack result = hatch.handler().drain(remaining, IFluidHandler.FluidAction.EXECUTE);
                    drained += result.getAmount();
                }
                if (drained < fluid.amount()) {
                    return false;
                }
            }
        }
        return true;
    }

    public boolean commitOutputs(MachineRecipe recipe) {
        List<ItemStack> outputs = recipe.outputs();
        for (int i = 0; i < outputs.size(); i++) {
            ItemStack output = outputs.get(i);
            ItemOutputRoute route = itemOutputRoutes.get(i);
            ItemStack remaining = output.copy();
            for (BusWithSlots bus : route.buses()) {
                if (remaining.isEmpty()) break;
                IItemHandler handler = bus.handler();
                for (int slot = 0; slot < handler.getSlots() && !remaining.isEmpty(); slot++) {
                    remaining = handler.insertItem(slot, remaining, false);
                }
            }
            if (!remaining.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public List<FluidOutputHatchBlockEntity> fluidOutputs() {
        return liveComponents(FluidOutputHatchBlockEntity.class);
    }

    private List<EnergyInputHatchBlockEntity> liveEnergyInputs() {
        return liveComponents(EnergyInputHatchBlockEntity.class);
    }

    private static List<IEnergyStorage> energyStorages(List<EnergyInputHatchBlockEntity> hatches) {
        return hatches.stream()
                .map(hatch -> hatch.getEnergyStorage(null))
                .toList();
    }

    private <T extends BlockEntity> List<T> liveComponents(Class<T> type) {
        var level = controller.getLevel();
        if (level == null) return List.of();

        List<T> matches = new ArrayList<>();
        for (ProcessingComponent component : controller.getComponents()) {
            if (!type.isInstance(component.getContainer())) continue;
            BlockEntity live = level.getBlockEntity(component.getPos());
            if (live == component.getContainer()) {
                matches.add(type.cast(live));
            }
        }
        return matches;
    }

    private record ItemInputRoute(List<BusWithSlots> buses, int required) {}

    private record ItemOutputRoute(List<BusWithSlots> buses, ItemStack stack) {}

    private record FluidInputRoute(List<HatchWithTank> hatches, int required) {}

    private record FluidOutputRoute(List<HatchWithTank> hatches, FluidStack stack) {}

    private record BusWithSlots(IItemHandler handler) {}

    private record HatchWithTank(IFluidHandler handler) {}
}
