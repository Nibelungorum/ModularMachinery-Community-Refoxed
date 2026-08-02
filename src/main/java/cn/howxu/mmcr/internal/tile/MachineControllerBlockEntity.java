package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.StructureMatcher;
import cn.howxu.mmcr.api.recipe.MachineIngredient;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.registry.MMCRRegistries;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

public class MachineControllerBlockEntity extends BlockEntity {

    private Machine machine;
    private MachineRecipe activeRecipe;
    private int tickCounter = 0;

    public MachineControllerBlockEntity(BlockPos pos, BlockState state) {
        super(MMCRRegistries.CONTROLLER_BE.get(), pos, state);
    }

    public Machine getMachine() { return machine; }
    public void setMachine(Machine m) { this.machine = m; setChanged(); }

    public boolean isFormed() { return getBlockState().getValue(MachineControllerBlock.FORMED); }
    public void setFormed(boolean f) {
        level.setBlock(getBlockPos(), getBlockState().setValue(MachineControllerBlock.FORMED, f), 3);
    }

    public MachineRecipe getActiveRecipe() { return activeRecipe; }
    public void setActiveRecipe(MachineRecipe r) { this.activeRecipe = r; setChanged(); }

    public int getTickCounter() { return tickCounter; }
    public void setTickCounter(int t) { this.tickCounter = t; setChanged(); }

    public void serverTick() {
        if (level == null || level.isClientSide()) return;
        if (machine == null) return;

        boolean formed = StructureMatcher.matches(
                machine.pattern(), level, getBlockPos(),
                getBlockState().getValue(MachineControllerBlock.FACING));
        if (formed != isFormed()) setFormed(formed);
        if (!formed) {
            if (activeRecipe != null) setActiveRecipe(null);
            return;
        }

        if (activeRecipe == null) tryStartNewRecipe();
        if (activeRecipe != null) tickActiveRecipe();
    }

    private void tryStartNewRecipe() {
        for (MachineRecipe recipe : RecipeRegistry.byMachine(machine)) {
            if (canAcceptInputs(recipe)) {
                setActiveRecipe(recipe);
                setTickCounter(0);
                return;
            }
        }
    }

    private boolean canAcceptInputs(MachineRecipe recipe) {
        for (MachineIngredient ingredient : recipe.inputs()) {
            if (ingredient instanceof MachineIngredient.ItemIngredient item && findAndCheckItemBus(item) == null) return false;
            if (ingredient instanceof MachineIngredient.FluidIngredient fluid && findAndCheckFluidHatch(fluid) == null) return false;
            if (ingredient instanceof MachineIngredient.EnergyIngredient energy && findAndCheckEnergyHatch(energy, recipe) == null) return false;
        }
        return true;
    }

    private ItemBusBlockEntity findAndCheckItemBus(MachineIngredient.ItemIngredient ingredient) {
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            if (level.getBlockEntity(getBlockPos().offset(dx, 1, dz)) instanceof ItemBusBlockEntity bus
                    && bus.ioType() == IOType.INPUT) {
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

    private FluidHatchBlockEntity findAndCheckFluidHatch(MachineIngredient.FluidIngredient ingredient) {
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            if (level.getBlockEntity(getBlockPos().offset(dx, 1, dz)) instanceof FluidHatchBlockEntity hatch
                    && hatch.ioType() == IOType.INPUT
                    && ingredient.fluid().test(hatch.getFluidHandler(null).getFluidInTank(0))
                    && hatch.getFluidHandler(null).getFluidInTank(0).getAmount() >= ingredient.amount()) return hatch;
        }
        return null;
    }

    private EnergyHatchBlockEntity findAndCheckEnergyHatch(MachineIngredient.EnergyIngredient ingredient, MachineRecipe recipe) {
        int required = ingredient.fePerTick() * recipe.tickTime();
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            if (level.getBlockEntity(getBlockPos().offset(dx, 1, dz)) instanceof EnergyHatchBlockEntity hatch
                    && hatch.ioType() == IOType.INPUT
                    && hatch.getEnergyStorage(null).getEnergyStored() >= required) return hatch;
        }
        return null;
    }

    private void tickActiveRecipe() {
        MachineRecipe recipe = activeRecipe;
        if (recipe == null) return;
        int next = tickCounter + 1;
        if (next >= recipe.tickTime()) {
            consumeAndProduce(recipe);
            setActiveRecipe(null);
            setTickCounter(0);
        } else {
            setTickCounter(next);
        }
    }

    private void consumeAndProduce(MachineRecipe recipe) {
        for (MachineIngredient ingredient : recipe.inputs()) {
            if (ingredient instanceof MachineIngredient.ItemIngredient item) {
                ItemBusBlockEntity bus = findAndCheckItemBus(item);
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
                FluidHatchBlockEntity hatch = findAndCheckFluidHatch(fluid);
                if (hatch != null) hatch.getFluidHandler(null).drain(fluid.amount(), IFluidHandler.FluidAction.EXECUTE);
            } else if (ingredient instanceof MachineIngredient.EnergyIngredient energy) {
                EnergyHatchBlockEntity hatch = findAndCheckEnergyHatch(energy, recipe);
                if (hatch != null) hatch.getEnergyStorage(null).extractEnergy(energy.fePerTick() * recipe.tickTime(), false);
            }
        }
        for (ItemStack output : recipe.outputs()) {
            ItemStack remaining = output.copy();
            for (int dx = -1; dx <= 1 && !remaining.isEmpty(); dx++) for (int dz = -1; dz <= 1 && !remaining.isEmpty(); dz++) {
                if (level.getBlockEntity(getBlockPos().offset(dx, 1, dz)) instanceof ItemBusBlockEntity bus
                        && bus.ioType() == IOType.OUTPUT) {
                    IItemHandler handler = bus.getItemHandler(null);
                    for (int slot = 0; slot < handler.getSlots() && !remaining.isEmpty(); slot++) {
                        remaining = handler.insertItem(slot, remaining, false);
                    }
                }
            }
        }
    }
}
