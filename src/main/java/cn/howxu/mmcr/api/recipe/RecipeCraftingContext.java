package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.internal.tile.EnergyInputHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.FluidInputHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.FluidOutputHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemInputBusBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemOutputBusBlockEntity;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.api.recipe.helper.EnergyRecipeIo;
import cn.howxu.mmcr.api.recipe.helper.ProcessingComponent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public final class RecipeCraftingContext {

    private static final Logger LOG = LoggerFactory.getLogger(RecipeCraftingContext.class);

    private final MachineControllerBlockEntity controller;
    private final BlockPos controllerPos;

    public RecipeCraftingContext(MachineControllerBlockEntity controller) {
        this.controller = controller;
        this.controllerPos = controller.getBlockPos();
        LOG.debug("Crafting context created: controllerPos={} components={}", controllerPos, controller.getComponents().size());
    }

    public boolean ioTick(MachineRecipe recipe) {
        for (MachineIngredient ingredient : recipe.inputs()) {
            if (!(ingredient instanceof MachineIngredient.EnergyIngredient energy)) continue;

            List<EnergyInputHatchBlockEntity> hatches = liveEnergyInputs();
            if (!EnergyRecipeIo.consumeInputs(energyStorages(hatches), energy.fePerTick(), 1)) {
                LOG.info("[ioTick] recipe={} missing {}FE/t energy input", recipe.id(), energy.fePerTick());
                return false;
            }
            LOG.debug("[ioTick] recipe={} drained {}FE across {} energy input hatch(es)",
                    recipe.id(), energy.fePerTick(), hatches.size());
        }
        return true;
    }

    public boolean simulateInputs(MachineRecipe recipe) {
        for (MachineIngredient ingredient : recipe.inputs()) {
            if (ingredient instanceof MachineIngredient.ItemIngredient item) {
                ItemInputBusBlockEntity bus = findAndCheckItemBus(item);
                if (bus == null) {
                    return false;
                }
            } else if (ingredient instanceof MachineIngredient.FluidIngredient fluid) {
                FluidInputHatchBlockEntity hatch = findAndCheckFluidHatch(fluid);
                if (hatch == null) {
                    return false;
                }
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
        List<OutputSlotState> slots = outputSlotStates();
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
                return false;
            }
        }
        return true;
    }

    public boolean startCrafting(MachineRecipe recipe) {
        return commitInputs(recipe);
    }

    public boolean finishCrafting(MachineRecipe recipe) {
        return commitOutputs(recipe);
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
                    return false;
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
                if (left > 0) {
                    return false;
                }
            } else if (ingredient instanceof MachineIngredient.FluidIngredient fluid) {
                FluidInputHatchBlockEntity hatch = findAndCheckFluidHatch(fluid);
                if (hatch == null) {
                    LOG.warn("[commitInputs]   ✗ fluid[{} of {}] {}mb {} hatch vanished between simulate and commit",
                            idx, recipe.inputs().size(), fluid.amount(), describeFluid(fluid.fluid()));
                    return false;
                }
                int drained = hatch.getFluidHandler(null).drain(fluid.amount(), IFluidHandler.FluidAction.EXECUTE).getAmount();
                LOG.info("[commitInputs]   ✓ fluid[{} of {}] drained {}mb / requested {}mb {} from hatch at {}",
                        idx, recipe.inputs().size(), drained, fluid.amount(), describeFluid(fluid.fluid()), hatch.getBlockPos());
                if (drained < fluid.amount()) {
                    return false;
                }
            } else if (ingredient instanceof MachineIngredient.EnergyIngredient energy) {
                LOG.debug("[commitInputs]   energy[{} of {}] already drained per tick at {}FE/t",
                        idx, recipe.inputs().size(), energy.fePerTick());
            }
        }
        return true;
    }

    private ItemInputBusBlockEntity findAndCheckItemBus(MachineIngredient.ItemIngredient ingredient) {
        for (ItemInputBusBlockEntity bus : liveComponents(ItemInputBusBlockEntity.class)) {
            IItemHandler handler = bus.getItemHandler(null);
            int count = 0;
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                if (ingredient.item().test(stack)) {
                    count += stack.getCount();
                }
            }
            if (count >= ingredient.count()) {
                return bus;
            }
        }
        return null;
    }

    private FluidInputHatchBlockEntity findAndCheckFluidHatch(MachineIngredient.FluidIngredient ingredient) {
        int scanned = 0;
        for (FluidInputHatchBlockEntity hatch : liveComponents(FluidInputHatchBlockEntity.class)) {
            scanned++;
            var tank = hatch.getFluidHandler(null).getFluidInTank(0);
            if (ingredient.fluid().test(tank) && tank.getAmount() >= ingredient.amount()) {
                LOG.debug("[scanFluidHatch] component pos={} matched: tank has {}mb of {} (need {}mb)",
                        hatch.getBlockPos(), tank.getAmount(), tank.getFluid().builtInRegistryHolder().getRegisteredName(), ingredient.amount());
                return hatch;
            }
            LOG.debug("[scanFluidHatch] component pos={} insufficient: tank has {}mb of {} (need {}mb of {})",
                    hatch.getBlockPos(), tank.getAmount(), tank.getFluid().builtInRegistryHolder().getRegisteredName(),
                    ingredient.amount(), describeFluid(ingredient.fluid()));
        }
        LOG.debug("[scanFluidHatch] scanned {} structure fluid input hatch(es); none matched {}mb of {}",
                scanned, ingredient.amount(), describeFluid(ingredient.fluid()));
        return null;
    }

    private List<EnergyInputHatchBlockEntity> liveEnergyInputs() {
        return liveComponents(EnergyInputHatchBlockEntity.class);
    }

    private static List<IEnergyStorage> energyStorages(List<EnergyInputHatchBlockEntity> hatches) {
        return hatches.stream()
                .map(hatch -> hatch.getEnergyStorage(null))
                .toList();
    }

    private List<OutputSlot> outputSlots() {
        List<OutputSlot> slots = new ArrayList<>();
        for (ItemOutputBusBlockEntity bus : liveComponents(ItemOutputBusBlockEntity.class)) {
            IItemHandler handler = bus.getItemHandler(null);
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                slots.add(new OutputSlot(handler, slot));
            }
        }
        return slots;
    }

    public List<FluidOutputHatchBlockEntity> fluidOutputs() {
        // Fluid outputs do not have a MachineRecipe output type yet; expose routed hatches for that next step.
        return liveComponents(FluidOutputHatchBlockEntity.class);
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
