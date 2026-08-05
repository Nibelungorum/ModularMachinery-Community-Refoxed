package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.RecipeFailureActions;
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
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class RecipeCraftingContext {

    static final String FAILURE_MISSING_INPUT = "gui.mmcr.controller.failure.missing_input";
    static final String FAILURE_MISSING_OUTPUT = "gui.mmcr.controller.failure.missing_output";
    static final String FAILURE_MISSING_ENERGY = "gui.mmcr.controller.failure.missing_energy";

    private final MachineControllerBlockEntity controller;

    private List<ItemInputRoute> itemInputRoutes = List.of();
    private List<ItemOutputRoute> itemOutputRoutes = List.of();
    private List<FluidInputRoute> fluidInputRoutes = List.of();
    private List<FluidOutputRoute> fluidOutputRoutes = List.of();
    private @Nullable String lastFailureUnloc;

    public RecipeCraftingContext(MachineControllerBlockEntity controller) {
        this.controller = controller;
    }

    public RecipeFailureActions failureAction() {
        Machine m = controller.getMachine();
        return m == null ? RecipeFailureActions.getDefaultAction() : m.failureAction();
    }

    public @Nullable String getLastFailureUnloc() {
        return lastFailureUnloc;
    }

    private void setFailure(String key) {
        this.lastFailureUnloc = key;
    }

    public boolean ioTick(MachineRecipe recipe) {
        lastFailureUnloc = null;
        for (MachineIngredient ingredient : recipe.inputs()) {
            if (!(ingredient instanceof MachineIngredient.EnergyIngredient energy)) continue;

            List<EnergyInputHatchBlockEntity> hatches = liveEnergyInputs();
            if (!EnergyRecipeIo.consumeInputs(energyStorages(hatches), energy.fePerTick(), 1)) {
                setFailure(FAILURE_MISSING_ENERGY);
                return false;
            }
        }
        return true;
    }

    public boolean simulateInputs(MachineRecipe recipe) {
        lastFailureUnloc = null;
        itemInputRoutes = new ArrayList<>();
        fluidInputRoutes = new ArrayList<>();
        List<ItemInputState> itemStates = itemInputStates();
        List<FluidInputState> fluidStates = fluidInputStates();

        for (MachineIngredient ingredient : recipe.inputs()) {
            if (ingredient instanceof MachineIngredient.ItemIngredient item) {
                List<ItemInputTransfer> transfers = new ArrayList<>();
                int remaining = item.count();
                for (ItemInputState state : itemStates) {
                    remaining = state.extract(item, remaining, transfers);
                    if (remaining <= 0) break;
                }
                if (remaining > 0) {
                    setFailure(FAILURE_MISSING_INPUT);
                    return false;
                }
                itemInputRoutes.add(new ItemInputRoute(transfers));
            } else if (ingredient instanceof MachineIngredient.FluidIngredient fluid) {
                List<FluidInputTransfer> transfers = new ArrayList<>();
                int remaining = fluid.amount();
                for (FluidInputState state : fluidStates) {
                    remaining = state.drain(fluid, remaining, transfers);
                    if (remaining <= 0) break;
                }
                if (remaining > 0) {
                    setFailure(FAILURE_MISSING_INPUT);
                    return false;
                }
                fluidInputRoutes.add(new FluidInputRoute(transfers));
            } else if (ingredient instanceof MachineIngredient.EnergyIngredient energy) {
                List<EnergyInputHatchBlockEntity> hatches = liveEnergyInputs();
                if (!EnergyRecipeIo.canConsumeInputs(energyStorages(hatches), energy.fePerTick(), 1)) {
                    setFailure(FAILURE_MISSING_ENERGY);
                    return false;
                }
            }
        }
        return true;
    }

    public boolean simulateOutputs(MachineRecipe recipe) {
        lastFailureUnloc = null;
        itemOutputRoutes = new ArrayList<>();
        fluidOutputRoutes = new ArrayList<>();
        List<ItemOutputState> itemStates = itemOutputStates();
        List<FluidOutputState> fluidStates = fluidOutputStates();

        for (ItemStack output : recipe.outputs()) {
            List<ItemOutputTransfer> transfers = new ArrayList<>();
            ItemStack remaining = output.copy();
            for (ItemOutputState state : itemStates) {
                remaining = state.insertIntoMatchingStack(remaining, transfers);
                if (remaining.isEmpty()) break;
            }
            for (ItemOutputState state : itemStates) {
                remaining = state.insert(remaining, transfers);
                if (remaining.isEmpty()) break;
            }
            if (!remaining.isEmpty()) {
                setFailure(FAILURE_MISSING_OUTPUT);
                return false;
            }
            itemOutputRoutes.add(new ItemOutputRoute(transfers));
        }

        for (FluidStack output : recipe.fluidOutputs()) {
            List<FluidOutputTransfer> transfers = new ArrayList<>();
            int remaining = output.getAmount();
            for (FluidOutputState state : fluidStates) {
                remaining = state.fill(output, remaining, transfers);
                if (remaining <= 0) break;
            }
            if (remaining > 0) {
                setFailure(FAILURE_MISSING_OUTPUT);
                return false;
            }
            fluidOutputRoutes.add(new FluidOutputRoute(transfers));
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
        List<ItemInputTransfer> itemTransfers = new ArrayList<>();
        List<FluidInputTransfer> fluidTransfers = new ArrayList<>();
        for (MachineIngredient ingredient : recipe.inputs()) {
            if (ingredient instanceof MachineIngredient.ItemIngredient) {
                itemTransfers.addAll(itemInputRoutes.get(itemIdx++).transfers());
            } else if (ingredient instanceof MachineIngredient.FluidIngredient) {
                fluidTransfers.addAll(fluidInputRoutes.get(fluidIdx++).transfers());
            }
        }
        if (!canExtract(itemTransfers) || !canDrain(fluidTransfers)) {
            setFailure(FAILURE_MISSING_INPUT);
            return false;
        }
        extract(itemTransfers);
        drain(fluidTransfers);
        return true;
    }

    public boolean commitOutputs(MachineRecipe recipe) {
        List<ItemOutputTransfer> itemTransfers = new ArrayList<>();
        List<ItemStack> outputs = recipe.outputs();
        for (int i = 0; i < outputs.size(); i++) {
            itemTransfers.addAll(itemOutputRoutes.get(i).transfers());
        }

        List<FluidOutputTransfer> fluidTransfers = new ArrayList<>();
        List<FluidStack> fluidOutputs = recipe.fluidOutputs();
        for (int i = 0; i < fluidOutputs.size(); i++) {
            fluidTransfers.addAll(fluidOutputRoutes.get(i).transfers());
        }
        if (!canInsert(itemTransfers) || !canFill(fluidTransfers)) {
            setFailure(FAILURE_MISSING_OUTPUT);
            return false;
        }
        insert(itemTransfers);
        fill(fluidTransfers);
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

    private List<ItemInputState> itemInputStates() {
        List<ItemInputState> states = new ArrayList<>();
        for (ItemInputBusBlockEntity bus : liveComponents(ItemInputBusBlockEntity.class)) {
            IItemHandler handler = bus.getItemHandler(null);
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                states.add(new ItemInputState(handler, slot, handler.getStackInSlot(slot).copy()));
            }
        }
        return states;
    }

    private List<ItemOutputState> itemOutputStates() {
        List<ItemOutputState> states = new ArrayList<>();
        for (ItemOutputBusBlockEntity bus : liveComponents(ItemOutputBusBlockEntity.class)) {
            IItemHandler handler = bus.getItemHandler(null);
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                states.add(new ItemOutputState(handler, slot, handler.getStackInSlot(slot).copy(), handler.getSlotLimit(slot)));
            }
        }
        return states;
    }

    private List<FluidInputState> fluidInputStates() {
        List<FluidInputState> states = new ArrayList<>();
        for (FluidInputHatchBlockEntity hatch : liveComponents(FluidInputHatchBlockEntity.class)) {
            IFluidHandler handler = hatch.getFluidHandler(null);
            for (int tank = 0; tank < handler.getTanks(); tank++) {
                states.add(new FluidInputState(handler, tank, handler.getFluidInTank(tank).copy()));
            }
        }
        return states;
    }

    private List<FluidOutputState> fluidOutputStates() {
        List<FluidOutputState> states = new ArrayList<>();
        for (FluidOutputHatchBlockEntity hatch : liveComponents(FluidOutputHatchBlockEntity.class)) {
            IFluidHandler handler = hatch.getFluidHandler(null);
            for (int tank = 0; tank < handler.getTanks(); tank++) {
                states.add(new FluidOutputState(handler, tank, handler.getFluidInTank(tank).copy(), handler.getTankCapacity(tank)));
            }
        }
        return states;
    }

    private static boolean canExtract(List<ItemInputTransfer> transfers) {
        List<ItemInputState> states = new ArrayList<>();
        for (ItemInputTransfer transfer : transfers) {
            ItemInputState state = itemInputState(states, transfer);
            if (state.extract(transfer.ingredient(), transfer.amount(), new ArrayList<>()) > 0) return false;
        }
        return true;
    }

    private static void extract(List<ItemInputTransfer> transfers) {
        for (ItemInputTransfer transfer : transfers) {
            transfer.handler().extractItem(transfer.slot(), transfer.amount(), false);
        }
    }

    private static boolean canInsert(List<ItemOutputTransfer> transfers) {
        List<ItemOutputState> states = new ArrayList<>();
        for (ItemOutputTransfer transfer : transfers) {
            ItemOutputState state = itemOutputState(states, transfer);
            if (!state.insert(transfer.stack(), new ArrayList<>()).isEmpty()) return false;
        }
        return true;
    }

    private static void insert(List<ItemOutputTransfer> transfers) {
        for (ItemOutputTransfer transfer : transfers) {
            transfer.handler().insertItem(transfer.slot(), transfer.stack(), false);
        }
    }

    private static boolean canDrain(List<FluidInputTransfer> transfers) {
        List<FluidInputState> states = new ArrayList<>();
        for (FluidInputTransfer transfer : transfers) {
            FluidInputState state = fluidInputState(states, transfer);
            if (state.drain(transfer.stack(), transfer.stack().getAmount(), new ArrayList<>()) > 0) return false;
        }
        return true;
    }

    private static void drain(List<FluidInputTransfer> transfers) {
        for (FluidInputTransfer transfer : transfers) {
            transfer.handler().drain(transfer.stack(), IFluidHandler.FluidAction.EXECUTE);
        }
    }

    private static boolean canFill(List<FluidOutputTransfer> transfers) {
        List<FluidOutputState> states = new ArrayList<>();
        for (FluidOutputTransfer transfer : transfers) {
            FluidOutputState state = fluidOutputState(states, transfer);
            if (state.fill(transfer.stack(), transfer.stack().getAmount(), new ArrayList<>()) > 0) return false;
        }
        return true;
    }

    private static void fill(List<FluidOutputTransfer> transfers) {
        for (FluidOutputTransfer transfer : transfers) {
            transfer.handler().fill(transfer.stack(), IFluidHandler.FluidAction.EXECUTE);
        }
    }

    private static ItemInputState itemInputState(List<ItemInputState> states, ItemInputTransfer transfer) {
        for (ItemInputState state : states) {
            if (state.handler == transfer.handler() && state.slot == transfer.slot()) return state;
        }
        ItemInputState state = new ItemInputState(transfer.handler(), transfer.slot(), transfer.handler().getStackInSlot(transfer.slot()).copy());
        states.add(state);
        return state;
    }

    private static ItemOutputState itemOutputState(List<ItemOutputState> states, ItemOutputTransfer transfer) {
        for (ItemOutputState state : states) {
            if (state.handler == transfer.handler() && state.slot == transfer.slot()) return state;
        }
        ItemOutputState state = new ItemOutputState(transfer.handler(), transfer.slot(),
                transfer.handler().getStackInSlot(transfer.slot()).copy(), transfer.handler().getSlotLimit(transfer.slot()));
        states.add(state);
        return state;
    }

    private static FluidInputState fluidInputState(List<FluidInputState> states, FluidInputTransfer transfer) {
        for (FluidInputState state : states) {
            if (state.handler == transfer.handler() && state.tank == transfer.tank()) return state;
        }
        FluidInputState state = new FluidInputState(transfer.handler(), transfer.tank(), transfer.handler().getFluidInTank(transfer.tank()).copy());
        states.add(state);
        return state;
    }

    private static FluidOutputState fluidOutputState(List<FluidOutputState> states, FluidOutputTransfer transfer) {
        for (FluidOutputState state : states) {
            if (state.handler == transfer.handler() && state.tank == transfer.tank()) return state;
        }
        FluidOutputState state = new FluidOutputState(transfer.handler(), transfer.tank(),
                transfer.handler().getFluidInTank(transfer.tank()).copy(), transfer.handler().getTankCapacity(transfer.tank()));
        states.add(state);
        return state;
    }

    private record ItemInputRoute(List<ItemInputTransfer> transfers) {}

    private record ItemOutputRoute(List<ItemOutputTransfer> transfers) {}

    private record FluidInputRoute(List<FluidInputTransfer> transfers) {}

    private record FluidOutputRoute(List<FluidOutputTransfer> transfers) {}

    private record ItemInputTransfer(IItemHandler handler, int slot, MachineIngredient.ItemIngredient ingredient, int amount) {}

    private record ItemOutputTransfer(IItemHandler handler, int slot, ItemStack stack) {}

    private record FluidInputTransfer(IFluidHandler handler, int tank, FluidStack stack) {}

    private record FluidOutputTransfer(IFluidHandler handler, int tank, FluidStack stack) {}

    private static final class ItemInputState {
        private final IItemHandler handler;
        private final int slot;
        private final ItemStack stack;

        private ItemInputState(IItemHandler handler, int slot, ItemStack stack) {
            this.handler = handler;
            this.slot = slot;
            this.stack = stack;
        }

        private int extract(MachineIngredient.ItemIngredient ingredient, int remaining, List<ItemInputTransfer> transfers) {
            if (remaining <= 0 || !ingredient.item().test(stack)) return remaining;
            int taken = Math.min(remaining, stack.getCount());
            if (taken <= 0) return remaining;
            transfers.add(new ItemInputTransfer(handler, slot, ingredient, taken));
            stack.shrink(taken);
            return remaining - taken;
        }
    }

    private static final class ItemOutputState {
        private final IItemHandler handler;
        private final int slot;
        private ItemStack stack;
        private final int limit;

        private ItemOutputState(IItemHandler handler, int slot, ItemStack stack, int limit) {
            this.handler = handler;
            this.slot = slot;
            this.stack = stack;
            this.limit = limit;
        }

        private ItemStack insert(ItemStack input, List<ItemOutputTransfer> transfers) {
            if (input.isEmpty()) return input;
            if (!stack.isEmpty() && !ItemStack.isSameItemSameComponents(stack, input)) return input;
            int slotLimit = Math.min(limit, input.getMaxStackSize());
            int room = stack.isEmpty() ? slotLimit : slotLimit - stack.getCount();
            int inserted = Math.min(room, input.getCount());
            if (inserted <= 0) return input;
            ItemStack insertedStack = input.copyWithCount(inserted);
            transfers.add(new ItemOutputTransfer(handler, slot, insertedStack));
            if (stack.isEmpty()) {
                stack = insertedStack.copy();
            } else {
                stack.grow(inserted);
            }
            ItemStack remaining = input.copy();
            remaining.shrink(inserted);
            return remaining;
        }

        private ItemStack insertIntoMatchingStack(ItemStack input, List<ItemOutputTransfer> transfers) {
            return stack.isEmpty() ? input : insert(input, transfers);
        }
    }

    private static final class FluidInputState {
        private final IFluidHandler handler;
        private final int tank;
        private final FluidStack stack;

        private FluidInputState(IFluidHandler handler, int tank, FluidStack stack) {
            this.handler = handler;
            this.tank = tank;
            this.stack = stack;
        }

        private int drain(MachineIngredient.FluidIngredient ingredient, int remaining, List<FluidInputTransfer> transfers) {
            return ingredient.fluid().test(stack) ? drain(stack, remaining, transfers) : remaining;
        }

        private int drain(FluidStack match, int remaining, List<FluidInputTransfer> transfers) {
            if (remaining <= 0 || !FluidStack.isSameFluidSameComponents(stack, match)) return remaining;
            int drained = Math.min(remaining, stack.getAmount());
            if (drained <= 0) return remaining;
            FluidStack transfer = stack.copy();
            transfer.setAmount(drained);
            transfers.add(new FluidInputTransfer(handler, tank, transfer));
            stack.shrink(drained);
            return remaining - drained;
        }
    }

    private static final class FluidOutputState {
        private final IFluidHandler handler;
        private final int tank;
        private FluidStack stack;
        private final int capacity;

        private FluidOutputState(IFluidHandler handler, int tank, FluidStack stack, int capacity) {
            this.handler = handler;
            this.tank = tank;
            this.stack = stack;
            this.capacity = capacity;
        }

        private int fill(FluidStack input, int remaining, List<FluidOutputTransfer> transfers) {
            if (remaining <= 0) return remaining;
            if (!stack.isEmpty() && !FluidStack.isSameFluidSameComponents(stack, input)) return remaining;
            int room = stack.isEmpty() ? capacity : capacity - stack.getAmount();
            int filled = Math.min(room, remaining);
            if (filled <= 0) return remaining;
            FluidStack transfer = input.copy();
            transfer.setAmount(filled);
            transfers.add(new FluidOutputTransfer(handler, tank, transfer));
            if (stack.isEmpty()) {
                stack = transfer.copy();
            } else {
                stack.grow(filled);
            }
            return remaining - filled;
        }
    }
}
