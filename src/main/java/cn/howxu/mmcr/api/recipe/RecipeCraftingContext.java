package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.internal.tile.EnergyInputHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.FluidInputHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemInputBusBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemOutputBusBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.List;

public final class RecipeCraftingContext {

    private final Level level;
    private final BlockPos controllerPos;

    public RecipeCraftingContext(Level level, BlockPos controllerPos) {
        this.level = level;
        this.controllerPos = controllerPos;
    }

    public boolean simulateInputs(MachineRecipe recipe) {
        for (MachineIngredient ingredient : recipe.inputs()) {
            if (ingredient instanceof MachineIngredient.ItemIngredient item && findAndCheckItemBus(item) == null) return false;
            if (ingredient instanceof MachineIngredient.FluidIngredient fluid && findAndCheckFluidHatch(fluid) == null) return false;
            if (ingredient instanceof MachineIngredient.EnergyIngredient energy && findAndCheckEnergyHatch(energy, recipe) == null) return false;
        }
        return true;
    }

    public boolean simulateOutputs(MachineRecipe recipe) {
        List<OutputSlotState> slots = outputSlotStates();
        for (ItemStack output : recipe.outputs()) {
            ItemStack remaining = output.copy();
            for (OutputSlotState slot : slots) {
                if (remaining.isEmpty()) break;
                remaining = slot.insert(remaining);
            }
            if (!remaining.isEmpty()) return false;
        }
        return true;
    }

    public boolean commitOutputs(MachineRecipe recipe) {
        for (ItemStack output : recipe.outputs()) {
            ItemStack remaining = output.copy();
            for (OutputSlot slot : outputSlots()) {
                if (remaining.isEmpty()) break;
                remaining = slot.handler().insertItem(slot.slot(), remaining, false);
            }
            if (!remaining.isEmpty()) return false;
        }
        return true;
    }

    public boolean commitInputs(MachineRecipe recipe) {
        for (MachineIngredient ingredient : recipe.inputs()) {
            if (ingredient instanceof MachineIngredient.ItemIngredient item) {
                ItemInputBusBlockEntity bus = findAndCheckItemBus(item);
                if (bus == null) continue;
                IItemHandler handler = bus.getItemHandler(null);
                int left = item.count();
                for (int slot = 0; slot < handler.getSlots() && left > 0; slot++) {
                    ItemStack stack = handler.getStackInSlot(slot);
                    if (item.item().test(stack)) {
                        int taken = Math.min(left, stack.getCount());
                        handler.extractItem(slot, taken, false);
                        left -= taken;
                    }
                }
            } else if (ingredient instanceof MachineIngredient.FluidIngredient fluid) {
                FluidInputHatchBlockEntity hatch = findAndCheckFluidHatch(fluid);
                if (hatch != null) hatch.getFluidHandler(null).drain(fluid.amount(), IFluidHandler.FluidAction.EXECUTE);
            } else if (ingredient instanceof MachineIngredient.EnergyIngredient energy) {
                EnergyInputHatchBlockEntity hatch = findAndCheckEnergyHatch(energy, recipe);
                if (hatch != null) hatch.getEnergyStorage(null).extractEnergy(energy.fePerTick() * recipe.tickTime(), false);
            }
        }
        return true;
    }

    private ItemInputBusBlockEntity findAndCheckItemBus(MachineIngredient.ItemIngredient ingredient) {
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            if (level.getBlockEntity(controllerPos.offset(dx, 1, dz)) instanceof ItemInputBusBlockEntity bus) {
                IItemHandler handler = bus.getItemHandler(null);
                int count = 0;
                for (int slot = 0; slot < handler.getSlots(); slot++) {
                    ItemStack stack = handler.getStackInSlot(slot);
                    if (ingredient.item().test(stack)) count += stack.getCount();
                }
                if (count >= ingredient.count()) return bus;
            }
        }
        return null;
    }

    private FluidInputHatchBlockEntity findAndCheckFluidHatch(MachineIngredient.FluidIngredient ingredient) {
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            if (level.getBlockEntity(controllerPos.offset(dx, 1, dz)) instanceof FluidInputHatchBlockEntity hatch
                    && ingredient.fluid().test(hatch.getFluidHandler(null).getFluidInTank(0))
                    && hatch.getFluidHandler(null).getFluidInTank(0).getAmount() >= ingredient.amount()) return hatch;
        }
        return null;
    }

    private EnergyInputHatchBlockEntity findAndCheckEnergyHatch(MachineIngredient.EnergyIngredient ingredient, MachineRecipe recipe) {
        int required = ingredient.fePerTick() * recipe.tickTime();
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            if (level.getBlockEntity(controllerPos.offset(dx, 1, dz)) instanceof EnergyInputHatchBlockEntity hatch
                    && hatch.getEnergyStorage(null).getEnergyStored() >= required) return hatch;
        }
        return null;
    }

    private List<OutputSlot> outputSlots() {
        List<OutputSlot> slots = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            if (level.getBlockEntity(controllerPos.offset(dx, 1, dz)) instanceof ItemOutputBusBlockEntity bus) {
                IItemHandler handler = bus.getItemHandler(null);
                for (int slot = 0; slot < handler.getSlots(); slot++) {
                    slots.add(new OutputSlot(handler, slot));
                }
            }
        }
        return slots;
    }

    private List<OutputSlotState> outputSlotStates() {
        return outputSlots().stream().map(OutputSlotState::new).toList();
    }

    private record OutputSlot(IItemHandler handler, int slot) {}

    private static final class OutputSlotState {
        private final OutputSlot slot;
        private ItemStack stack;

        private OutputSlotState(OutputSlot slot) {
            this.slot = slot;
            this.stack = slot.handler().getStackInSlot(slot.slot()).copy();
        }

        private ItemStack insert(ItemStack input) {
            ItemStack accepted = input.copy();
            ItemStack simulatedRemainder = slot.handler().insertItem(slot.slot(), accepted, true);
            accepted.shrink(simulatedRemainder.getCount());
            if (accepted.isEmpty()) return input;
            if (!stack.isEmpty() && !ItemStack.isSameItemSameComponents(stack, accepted)) return input;

            int limit = Math.min(slot.handler().getSlotLimit(slot.slot()), accepted.getMaxStackSize());
            int space = stack.isEmpty() ? limit : limit - stack.getCount();
            int inserted = Math.min(space, accepted.getCount());
            if (inserted <= 0) return input;

            if (stack.isEmpty()) {
                stack = accepted.copyWithCount(inserted);
            } else {
                stack.grow(inserted);
            }
            ItemStack remaining = input.copy();
            remaining.shrink(inserted);
            return remaining;
        }
    }
}