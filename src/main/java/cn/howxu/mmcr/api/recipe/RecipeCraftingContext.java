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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public final class RecipeCraftingContext {

    private static final Logger LOG = LoggerFactory.getLogger(RecipeCraftingContext.class);

    private final Level level;
    private final BlockPos controllerPos;

    public RecipeCraftingContext(Level level, BlockPos controllerPos) {
        this.level = level;
        this.controllerPos = controllerPos;
        LOG.debug("Crafting context created: controllerPos={}", controllerPos);
    }

    public boolean simulateInputs(MachineRecipe recipe) {
        LOG.debug("[simulateInputs] recipe={} controllerPos={} ingredients={}", recipe.id(), controllerPos, recipe.inputs().size());
        for (MachineIngredient ingredient : recipe.inputs()) {
            if (ingredient instanceof MachineIngredient.ItemIngredient item) {
                ItemInputBusBlockEntity bus = findAndCheckItemBus(item);
                if (bus == null) {
                    LOG.info("[simulateInputs]   ✗ item ingredient {}x {} → no matching input bus",
                            item.count(), describeIngredient(item.item()));
                    return false;
                }
                LOG.debug("[simulateInputs]   ✓ item ingredient {}x {} via bus at {}",
                        item.count(), describeIngredient(item.item()), bus.getBlockPos());
            } else if (ingredient instanceof MachineIngredient.FluidIngredient fluid) {
                FluidInputHatchBlockEntity hatch = findAndCheckFluidHatch(fluid);
                if (hatch == null) {
                    LOG.info("[simulateInputs]   ✗ fluid ingredient {}mb {} → no matching fluid hatch",
                            fluid.amount(), describeFluid(fluid.fluid()));
                    return false;
                }
                LOG.debug("[simulateInputs]   ✓ fluid ingredient {}mb {} via hatch at {}",
                        fluid.amount(), describeFluid(fluid.fluid()), hatch.getBlockPos());
            } else if (ingredient instanceof MachineIngredient.EnergyIngredient energy) {
                int required = energy.fePerTick() * recipe.tickTime();
                EnergyInputHatchBlockEntity hatch = findAndCheckEnergyHatch(energy, recipe);
                if (hatch == null) {
                    LOG.info("[simulateInputs]   ✗ energy ingredient {}FE/t ({}FE total) → no matching energy hatch above controller",
                            energy.fePerTick(), required);
                    return false;
                }
                int stored = hatch.getEnergyStorage(null).getEnergyStored();
                LOG.debug("[simulateInputs]   ✓ energy ingredient {}FE/t ({}FE total) via hatch at {} (stored={}FE)",
                        energy.fePerTick(), required, hatch.getBlockPos(), stored);
            }
        }
        LOG.debug("[simulateInputs] recipe={} OK", recipe.id());
        return true;
    }

    public boolean simulateOutputs(MachineRecipe recipe) {
        List<OutputSlotState> slots = outputSlotStates();
        LOG.debug("[simulateOutputs] recipe={} controllerPos={} outputs={} candidateSlots={}",
                recipe.id(), controllerPos, recipe.outputs().size(), slots.size());
        int idx = 0;
        for (ItemStack output : recipe.outputs()) {
            idx++;
            ItemStack remaining = output.copy();
            int initial = remaining.getCount();
            int placed = 0;
            for (OutputSlotState slot : slots) {
                if (remaining.isEmpty()) break;
                int before = remaining.getCount();
                remaining = slot.insert(remaining);
                int delta = before - remaining.getCount();
                placed += delta;
            }
            if (!remaining.isEmpty()) {
                LOG.info("[simulateOutputs]   ✗ output[{} of {}] {}x {} would leave {} unabsorbed across {} slot(s)",
                        idx, recipe.outputs().size(), initial, output.getItem().builtInRegistryHolder().getRegisteredName(),
                        remaining.getCount(), slots.size());
                return false;
            }
            LOG.debug("[simulateOutputs]   ✓ output[{} of {}] {}x {} would fully fit ({} slot visits, {} total absorbed)",
                    idx, recipe.outputs().size(), initial, output.getItem().builtInRegistryHolder().getRegisteredName(), slots.size(), placed);
        }
        LOG.debug("[simulateOutputs] recipe={} OK", recipe.id());
        return true;
    }

    public boolean commitOutputs(MachineRecipe recipe) {
        List<OutputSlot> slots = outputSlots();
        LOG.info("[commitOutputs] recipe={} controllerPos={} outputs={} slots={}", recipe.id(), controllerPos, recipe.outputs().size(), slots.size());
        int idx = 0;
        for (ItemStack output : recipe.outputs()) {
            idx++;
            ItemStack remaining = output.copy();
            int initial = remaining.getCount();
            int placed = 0;
            int slotsUsed = 0;
            for (OutputSlot slot : slots) {
                if (remaining.isEmpty()) break;
                int before = remaining.getCount();
                remaining = slot.handler().insertItem(slot.slot(), remaining, false);
                int delta = before - remaining.getCount();
                if (delta > 0) {
                    placed += delta;
                    slotsUsed++;
                    LOG.debug("[commitOutputs]   output[{} of {}] +{}x {} into slot {} (cumulative placed={}, remaining={})",
                            idx, recipe.outputs().size(), delta, output.getItem().builtInRegistryHolder().getRegisteredName(),
                            slot.slot(), placed, remaining.getCount());
                }
            }
            if (!remaining.isEmpty()) {
                LOG.warn("[commitOutputs]   ✗ output[{} of {}] {}x {} failed to fully commit: {} leftover despite simulate success",
                        idx, recipe.outputs().size(), initial, output.getItem().builtInRegistryHolder().getRegisteredName(), remaining.getCount());
                return false;
            }
            LOG.info("[commitOutputs]   ✓ output[{} of {}] {}x {} placed across {} slot(s)",
                    idx, recipe.outputs().size(), initial, output.getItem().builtInRegistryHolder().getRegisteredName(), slotsUsed);
        }
        return true;
    }

    public boolean commitInputs(MachineRecipe recipe) {
        LOG.info("[commitInputs] recipe={} controllerPos={} ingredients={}", recipe.id(), controllerPos, recipe.inputs().size());
        int idx = 0;
        for (MachineIngredient ingredient : recipe.inputs()) {
            idx++;
            if (ingredient instanceof MachineIngredient.ItemIngredient item) {
                ItemInputBusBlockEntity bus = findAndCheckItemBus(item);
                if (bus == null) {
                    LOG.warn("[commitInputs]   ✗ item[{} of {}] {}x {} bus vanished between simulate and commit",
                            idx, recipe.inputs().size(), item.count(), describeIngredient(item.item()));
                    continue;
                }
                IItemHandler handler = bus.getItemHandler(null);
                int left = item.count();
                int beforeLeft = left;
                int slotsTouched = 0;
                for (int slot = 0; slot < handler.getSlots() && left > 0; slot++) {
                    ItemStack stack = handler.getStackInSlot(slot);
                    if (item.item().test(stack)) {
                        int taken = Math.min(left, stack.getCount());
                        handler.extractItem(slot, taken, false);
                        left -= taken;
                        slotsTouched++;
                        LOG.debug("[commitInputs]   item[{} of {}] extracted {}x from bus={} slot={} (remaining {})",
                                idx, recipe.inputs().size(), taken, bus.getBlockPos(), slot, left);
                    }
                }
                int totalExtracted = beforeLeft - left;
                LOG.info("[commitInputs]   ✓ item[{} of {}] {}x {} extracted ({} short of {}) from bus at {} across {} slot(s)",
                        idx, recipe.inputs().size(), totalExtracted, describeIngredient(item.item()),
                        left, beforeLeft, bus.getBlockPos(), slotsTouched);
            } else if (ingredient instanceof MachineIngredient.FluidIngredient fluid) {
                FluidInputHatchBlockEntity hatch = findAndCheckFluidHatch(fluid);
                if (hatch == null) {
                    LOG.warn("[commitInputs]   ✗ fluid[{} of {}] {}mb {} hatch vanished between simulate and commit",
                            idx, recipe.inputs().size(), fluid.amount(), describeFluid(fluid.fluid()));
                    continue;
                }
                int drained = hatch.getFluidHandler(null).drain(fluid.amount(), IFluidHandler.FluidAction.EXECUTE).getAmount();
                LOG.info("[commitInputs]   ✓ fluid[{} of {}] drained {}mb / requested {}mb {} from hatch at {}",
                        idx, recipe.inputs().size(), drained, fluid.amount(), describeFluid(fluid.fluid()), hatch.getBlockPos());
            } else if (ingredient instanceof MachineIngredient.EnergyIngredient energy) {
                int required = energy.fePerTick() * recipe.tickTime();
                EnergyInputHatchBlockEntity hatch = findAndCheckEnergyHatch(energy, recipe);
                if (hatch == null) {
                    LOG.warn("[commitInputs]   ✗ energy[{} of {}] {}FE hatch vanished between simulate and commit",
                            idx, recipe.inputs().size(), required);
                    continue;
                }
                int extracted = hatch.getEnergyStorage(null).extractEnergy(required, false);
                int remaining = hatch.getEnergyStorage(null).getEnergyStored();
                LOG.info("[commitInputs]   ✓ energy[{} of {}] extracted {}FE / requested {}FE ({}FE/t × {}t) from hatch at {}, hatch now has {}FE",
                        idx, recipe.inputs().size(), extracted, required, energy.fePerTick(), recipe.tickTime(),
                        hatch.getBlockPos(), remaining);
            }
        }
        return true;
    }

    private ItemInputBusBlockEntity findAndCheckItemBus(MachineIngredient.ItemIngredient ingredient) {
        int scanned = 0;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            BlockPos candidate = controllerPos.offset(dx, 1, dz);
            if (level.getBlockEntity(candidate) instanceof ItemInputBusBlockEntity bus) {
                scanned++;
                IItemHandler handler = bus.getItemHandler(null);
                int count = 0;
                int matchingSlots = 0;
                for (int slot = 0; slot < handler.getSlots(); slot++) {
                    ItemStack stack = handler.getStackInSlot(slot);
                    if (ingredient.item().test(stack)) {
                        count += stack.getCount();
                        matchingSlots++;
                    }
                }
                if (count >= ingredient.count()) {
                    LOG.debug("[scanItemBus] candidate pos={} matched: {}/{} required (across {} matching slot(s))",
                            candidate, count, ingredient.count(), matchingSlots);
                    return bus;
                }
                LOG.debug("[scanItemBus] candidate pos={} insufficient: {}/{} required (across {} matching slot(s))",
                        candidate, count, ingredient.count(), matchingSlots);
            }
        }
        LOG.debug("[scanItemBus] scanned {} input bus(es) in 3x3 above controller; none matched {}x of {}", scanned, ingredient.count(), describeIngredient(ingredient.item()));
        return null;
    }

    private FluidInputHatchBlockEntity findAndCheckFluidHatch(MachineIngredient.FluidIngredient ingredient) {
        int scanned = 0;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            BlockPos candidate = controllerPos.offset(dx, 1, dz);
            if (level.getBlockEntity(candidate) instanceof FluidInputHatchBlockEntity hatch) {
                scanned++;
                var tank = hatch.getFluidHandler(null).getFluidInTank(0);
                if (ingredient.fluid().test(tank) && tank.getAmount() >= ingredient.amount()) {
                    LOG.debug("[scanFluidHatch] candidate pos={} matched: tank has {}mb of {} (need {}mb)",
                            candidate, tank.getAmount(), tank.getFluid().builtInRegistryHolder().getRegisteredName(), ingredient.amount());
                    return hatch;
                }
                LOG.debug("[scanFluidHatch] candidate pos={} insufficient: tank has {}mb of {} (need {}mb of {})",
                        candidate, tank.getAmount(), tank.getFluid().builtInRegistryHolder().getRegisteredName(),
                        ingredient.amount(), describeFluid(ingredient.fluid()));
            }
        }
        LOG.debug("[scanFluidHatch] scanned {} fluid input hatch(es) in 3x3 above controller; none matched {}mb of {}",
                scanned, ingredient.amount(), describeFluid(ingredient.fluid()));
        return null;
    }

    private EnergyInputHatchBlockEntity findAndCheckEnergyHatch(MachineIngredient.EnergyIngredient ingredient, MachineRecipe recipe) {
        int required = ingredient.fePerTick() * recipe.tickTime();
        int scanned = 0;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            BlockPos candidate = controllerPos.offset(dx, 1, dz);
            if (level.getBlockEntity(candidate) instanceof EnergyInputHatchBlockEntity hatch) {
                scanned++;
                int stored = hatch.getEnergyStorage(null).getEnergyStored();
                if (stored >= required) {
                    LOG.debug("[scanEnergyHatch] candidate pos={} matched: stored {}FE (need {}FE)",
                            candidate, stored, required);
                    return hatch;
                }
                LOG.debug("[scanEnergyHatch] candidate pos={} insufficient: stored {}FE (need {}FE)",
                        candidate, stored, required);
            }
        }
        LOG.debug("[scanEnergyHatch] scanned {} energy input hatch(es) in 3x3 above controller; none held {}FE",
                scanned, required);
        return null;
    }

    private List<OutputSlot> outputSlots() {
        List<OutputSlot> slots = new ArrayList<>();
        int buses = 0;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            BlockPos candidate = controllerPos.offset(dx, 1, dz);
            if (level.getBlockEntity(candidate) instanceof ItemOutputBusBlockEntity bus) {
                buses++;
                IItemHandler handler = bus.getItemHandler(null);
                int usable = 0;
                for (int slot = 0; slot < handler.getSlots(); slot++) {
                    slots.add(new OutputSlot(handler, slot));
                    usable++;
                }
                LOG.debug("[scanOutputBus] candidate pos={} contributed {} slot(s) (total slots so far: {})", candidate, usable, slots.size());
            }
        }
        LOG.debug("[scanOutputBus] discovered {} output bus(es) → {} total slot(s) at controllerPos={}", buses, slots.size(), controllerPos);
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

    private static String describeIngredient(net.minecraft.world.item.crafting.Ingredient ingredient) {
        return ingredient.items()
                .findFirst()
                .map(h -> h.value().builtInRegistryHolder().getRegisteredName())
                .orElseGet(ingredient::toString);
    }

    private static String describeFluid(net.neoforged.neoforge.fluids.crafting.FluidIngredient fluid) {
        return fluid.fluids().stream()
                .findFirst()
                .map(h -> h.value().builtInRegistryHolder().getRegisteredName())
                .orElseGet(fluid::toString);
    }
}