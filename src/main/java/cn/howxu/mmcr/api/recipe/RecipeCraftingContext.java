package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.RecipeFailureActions;
import cn.howxu.mmcr.api.recipe.helper.EnergyRecipeIo;
import cn.howxu.mmcr.api.recipe.helper.ProcessingComponent;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.api.recipe.requirement.FluidRequirement;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
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
import net.neoforged.neoforge.items.IItemHandlerModifiable;
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
    private @Nullable RequirementFailure lastRequirementFailure;

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

    public @Nullable RequirementFailure getLastRequirementFailure() {
        return lastRequirementFailure;
    }

    private void setFailure(String key) {
        setFailure(key, null);
    }

    private void setFailure(String key, @Nullable RequirementFailure failure) {
        this.lastFailureUnloc = key;
        this.lastRequirementFailure = failure;
    }

    private static String componentTrace(BlockEntity entity) {
        return entity.getClass().getSimpleName() + "@" + entity.getBlockPos().toShortString();
    }

    private static List<String> componentTraces(List<? extends BlockEntity> entities) {
        List<String> traces = new ArrayList<>(entities.size());
        for (BlockEntity entity : entities) traces.add(componentTrace(entity));
        return traces;
    }

    public boolean ioTick(MachineRecipe recipe) {
        lastFailureUnloc = null;
        lastRequirementFailure = null;
        List<MachineRequirement> requirements = recipe.requirements();
        List<EnergyInputHatchBlockEntity> hatches = liveEnergyInputs();
        for (int requirementIndex = 0; requirementIndex < requirements.size(); requirementIndex++) {
            MachineRequirement requirement = requirements.get(requirementIndex);
            if (!(requirement instanceof EnergyRequirement energy)) continue;
            if (!EnergyRecipeIo.consumeInputs(energyStorages(hatches), energy.fePerTick(), 1)) {
                long available = availableEnergy(hatches);
                setFailure(FAILURE_MISSING_ENERGY, new RequirementFailure(
                        requirementIndex,
                        RequirementFailure.Kind.MISSING_ENERGY,
                        energy.fePerTick(),
                        available,
                        Math.max(0, energy.fePerTick() - available),
                        componentTraces(new ArrayList<>(hatches)),
                        List.of()
                ));
                return false;
            }
        }
        return true;
    }

    public boolean simulateInputs(MachineRecipe recipe) {
        lastFailureUnloc = null;
        lastRequirementFailure = null;
        List<MachineRequirement> requirements = recipe.requirements();
        itemInputRoutes = emptyItemInputRoutes(requirements.size());
        fluidInputRoutes = emptyFluidInputRoutes(requirements.size());
        List<ItemInputState> itemStates = itemInputStates();
        List<FluidInputState> fluidStates = fluidInputStates();
        List<ItemInputBusBlockEntity> itemHatches = liveComponents(ItemInputBusBlockEntity.class);
        List<FluidInputHatchBlockEntity> fluidHatches = liveComponents(FluidInputHatchBlockEntity.class);

        for (int requirementIndex = 0; requirementIndex < requirements.size(); requirementIndex++) {
            MachineRequirement requirement = requirements.get(requirementIndex);
            if (requirement instanceof ItemRequirement item && item.io() == RecipeModifier.IOType.INPUT) {
                List<ItemInputTransfer> transfers = new ArrayList<>();
                MachineIngredient.ItemIngredient ingredient = new MachineIngredient.ItemIngredient(item.item(), item.count());
                int remaining = item.count();
                List<ItemInputBusBlockEntity> matched = new ArrayList<>();
                for (ItemInputState state : itemStates) {
                    int before = transfers.size();
                    remaining = state.extract(ingredient, remaining, transfers);
                    if (transfers.size() > before && state.bus() != null) matched.add(state.bus());
                    if (remaining <= 0) break;
                }
                if (remaining > 0) {
                    setFailure(FAILURE_MISSING_INPUT, new RequirementFailure(
                            requirementIndex,
                            RequirementFailure.Kind.MISSING_INPUT,
                            item.count(),
                            item.count() - remaining,
                            remaining,
                            componentTraces(new ArrayList<>(itemHatches)),
                            componentTraces(matched)
                    ));
                    return false;
                }
                itemInputRoutes.set(requirementIndex, new ItemInputRoute(transfers));
            } else if (requirement instanceof FluidRequirement fluid && fluid.io() == RecipeModifier.IOType.INPUT) {
                List<FluidInputTransfer> transfers = new ArrayList<>();
                MachineIngredient.FluidIngredient ingredient = new MachineIngredient.FluidIngredient(fluid.fluid(), fluid.amount());
                int remaining = fluid.amount();
                List<FluidInputHatchBlockEntity> matched = new ArrayList<>();
                for (FluidInputState state : fluidStates) {
                    int before = transfers.size();
                    remaining = state.drain(ingredient, remaining, transfers);
                    if (transfers.size() > before && state.hatch() != null) matched.add(state.hatch());
                    if (remaining <= 0) break;
                }
                if (remaining > 0) {
                    setFailure(FAILURE_MISSING_INPUT, new RequirementFailure(
                            requirementIndex,
                            RequirementFailure.Kind.MISSING_INPUT,
                            fluid.amount(),
                            fluid.amount() - remaining,
                            remaining,
                            componentTraces(new ArrayList<>(fluidHatches)),
                            componentTraces(matched)
                    ));
                    return false;
                }
                fluidInputRoutes.set(requirementIndex, new FluidInputRoute(transfers));
            } else if (requirement instanceof EnergyRequirement energy) {
                List<EnergyInputHatchBlockEntity> hatches = liveEnergyInputs();
                if (!EnergyRecipeIo.canConsumeInputs(energyStorages(hatches), energy.fePerTick(), 1)) {
                    long available = availableEnergy(hatches);
                    setFailure(FAILURE_MISSING_ENERGY, new RequirementFailure(
                            requirementIndex,
                            RequirementFailure.Kind.MISSING_ENERGY,
                            energy.fePerTick(),
                            available,
                            Math.max(0, energy.fePerTick() - available),
                            componentTraces(new ArrayList<>(hatches)),
                            List.of()
                    ));
                    return false;
                }
            }
        }
        return true;
    }

    public boolean simulateOutputs(MachineRecipe recipe) {
        lastFailureUnloc = null;
        lastRequirementFailure = null;
        List<MachineRequirement> requirements = recipe.requirements();
        itemOutputRoutes = emptyItemOutputRoutes(requirements.size());
        fluidOutputRoutes = emptyFluidOutputRoutes(requirements.size());
        List<ItemOutputState> itemStates = itemOutputStates();
        List<FluidOutputState> fluidStates = fluidOutputStates();
        List<ItemOutputBusBlockEntity> itemHatches = liveComponents(ItemOutputBusBlockEntity.class);
        List<FluidOutputHatchBlockEntity> fluidHatches = liveComponents(FluidOutputHatchBlockEntity.class);

        for (int requirementIndex = 0; requirementIndex < requirements.size(); requirementIndex++) {
            MachineRequirement requirement = requirements.get(requirementIndex);
            if (requirement instanceof ItemRequirement item && item.io() == RecipeModifier.IOType.OUTPUT) {
                List<ItemOutputTransfer> transfers = new ArrayList<>();
                ItemStack output = item.stack();
                ItemStack remaining = normalizeRecipeOutput(output);
                List<ItemOutputBusBlockEntity> matched = new ArrayList<>();
                for (ItemOutputState state : itemStates) {
                    int before = transfers.size();
                    remaining = state.insertIntoMatchingStack(remaining, transfers);
                    if (transfers.size() > before && state.bus() != null) matched.add(state.bus());
                    if (remaining.isEmpty()) break;
                }
                for (ItemOutputState state : itemStates) {
                    int before = transfers.size();
                    remaining = state.insert(remaining, transfers);
                    if (transfers.size() > before && state.bus() != null) matched.add(state.bus());
                    if (remaining.isEmpty()) break;
                }
                if (!remaining.isEmpty()) {
                    setFailure(FAILURE_MISSING_OUTPUT, new RequirementFailure(
                            requirementIndex,
                            RequirementFailure.Kind.MISSING_OUTPUT,
                            output.getCount(),
                            output.getCount() - remaining.getCount(),
                            remaining.getCount(),
                            componentTraces(new ArrayList<>(itemHatches)),
                            componentTraces(matched)
                    ));
                    return false;
                }
                itemOutputRoutes.set(requirementIndex, new ItemOutputRoute(transfers));
            } else if (requirement instanceof FluidRequirement fluid && fluid.io() == RecipeModifier.IOType.OUTPUT) {
                List<FluidOutputTransfer> transfers = new ArrayList<>();
                FluidStack output = fluid.stack();
                int remaining = output.getAmount();
                List<FluidOutputHatchBlockEntity> matched = new ArrayList<>();
                for (FluidOutputState state : fluidStates) {
                    int before = transfers.size();
                    remaining = state.fill(output, remaining, transfers);
                    if (transfers.size() > before && state.hatch() != null) matched.add(state.hatch());
                    if (remaining <= 0) break;
                }
                if (remaining > 0) {
                    setFailure(FAILURE_MISSING_OUTPUT, new RequirementFailure(
                            requirementIndex,
                            RequirementFailure.Kind.MISSING_OUTPUT,
                            output.getAmount(),
                            output.getAmount() - remaining,
                            remaining,
                            componentTraces(new ArrayList<>(fluidHatches)),
                            componentTraces(matched)
                    ));
                    return false;
                }
                fluidOutputRoutes.set(requirementIndex, new FluidOutputRoute(transfers));
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

    public boolean commitInputs(MachineRecipe recipe) {
        List<ItemInputTransfer> itemTransfers = new ArrayList<>();
        List<FluidInputTransfer> fluidTransfers = new ArrayList<>();
        List<MachineRequirement> requirements = recipe.requirements();
        RequirementFailure itemFailure = firstItemInputFailure(requirements);
        if (itemFailure != null) {
            setFailure(FAILURE_MISSING_INPUT, itemFailure);
            return false;
        }
        RequirementFailure fluidFailure = firstFluidInputFailure(requirements);
        if (fluidFailure != null) {
            setFailure(FAILURE_MISSING_INPUT, fluidFailure);
            return false;
        }
        for (int requirementIndex = 0; requirementIndex < requirements.size(); requirementIndex++) {
            MachineRequirement requirement = requirements.get(requirementIndex);
            if (requirement instanceof ItemRequirement item && item.io() == RecipeModifier.IOType.INPUT) {
                ItemInputRoute route = requirementIndex < itemInputRoutes.size() ? itemInputRoutes.get(requirementIndex) : null;
                if (route != null) itemTransfers.addAll(route.transfers());
            } else if (requirement instanceof FluidRequirement fluid && fluid.io() == RecipeModifier.IOType.INPUT) {
                FluidInputRoute route = requirementIndex < fluidInputRoutes.size() ? fluidInputRoutes.get(requirementIndex) : null;
                if (route != null) fluidTransfers.addAll(route.transfers());
            }
        }
        extract(itemTransfers);
        drain(fluidTransfers);
        return true;
    }

    public boolean commitOutputs(MachineRecipe recipe) {
        List<ItemOutputTransfer> itemTransfers = new ArrayList<>();
        List<FluidOutputTransfer> fluidTransfers = new ArrayList<>();
        List<MachineRequirement> requirements = recipe.requirements();
        RequirementFailure itemFailure = firstItemOutputFailure(requirements);
        if (itemFailure != null) {
            setFailure(FAILURE_MISSING_OUTPUT, itemFailure);
            return false;
        }
        RequirementFailure fluidFailure = firstFluidOutputFailure(requirements);
        if (fluidFailure != null) {
            setFailure(FAILURE_MISSING_OUTPUT, fluidFailure);
            return false;
        }
        for (int requirementIndex = 0; requirementIndex < requirements.size(); requirementIndex++) {
            MachineRequirement requirement = requirements.get(requirementIndex);
            if (requirement instanceof ItemRequirement item && item.io() == RecipeModifier.IOType.OUTPUT) {
                ItemOutputRoute route = requirementIndex < itemOutputRoutes.size() ? itemOutputRoutes.get(requirementIndex) : null;
                if (route != null) itemTransfers.addAll(route.transfers());
            } else if (requirement instanceof FluidRequirement fluid && fluid.io() == RecipeModifier.IOType.OUTPUT) {
                FluidOutputRoute route = requirementIndex < fluidOutputRoutes.size() ? fluidOutputRoutes.get(requirementIndex) : null;
                if (route != null) fluidTransfers.addAll(route.transfers());
            }
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

    private static int availableEnergy(List<EnergyInputHatchBlockEntity> hatches) {
        long available = 0;
        for (EnergyInputHatchBlockEntity hatch : hatches) {
            available += hatch.getEnergyStorage(null).getEnergyStored();
            if (available >= Integer.MAX_VALUE) return Integer.MAX_VALUE;
        }
        return (int) available;
    }

    private static List<ItemInputRoute> emptyItemInputRoutes(int size) {
        List<ItemInputRoute> routes = new ArrayList<>(size);
        for (int i = 0; i < size; i++) routes.add(null);
        return routes;
    }

    private static List<ItemOutputRoute> emptyItemOutputRoutes(int size) {
        List<ItemOutputRoute> routes = new ArrayList<>(size);
        for (int i = 0; i < size; i++) routes.add(null);
        return routes;
    }

    private static List<FluidInputRoute> emptyFluidInputRoutes(int size) {
        List<FluidInputRoute> routes = new ArrayList<>(size);
        for (int i = 0; i < size; i++) routes.add(null);
        return routes;
    }

    private static List<FluidOutputRoute> emptyFluidOutputRoutes(int size) {
        List<FluidOutputRoute> routes = new ArrayList<>(size);
        for (int i = 0; i < size; i++) routes.add(null);
        return routes;
    }

    private @Nullable RequirementFailure firstItemInputFailure(List<MachineRequirement> requirements) {
        List<ItemInputState> states = new ArrayList<>();
        for (int requirementIndex = 0; requirementIndex < requirements.size(); requirementIndex++) {
            MachineRequirement requirement = requirements.get(requirementIndex);
            if (!(requirement instanceof ItemRequirement item) || item.io() != RecipeModifier.IOType.INPUT) continue;
            ItemInputRoute route = requirementIndex < itemInputRoutes.size() ? itemInputRoutes.get(requirementIndex) : null;
            if (route == null) return new RequirementFailure(requirementIndex, RequirementFailure.Kind.MISSING_INPUT, item.count(), 0);
            int available = 0;
            for (ItemInputTransfer transfer : route.transfers()) {
                ItemInputState state = itemInputState(states, transfer);
                int remaining = state.extract(transfer.ingredient(), transfer.amount(), new ArrayList<>());
                available += transfer.amount() - remaining;
                if (remaining > 0) {
                    return new RequirementFailure(requirementIndex, RequirementFailure.Kind.MISSING_INPUT, item.count(), available);
                }
            }
        }
        return null;
    }

    private @Nullable RequirementFailure firstItemOutputFailure(List<MachineRequirement> requirements) {
        List<ItemOutputState> states = new ArrayList<>();
        for (int requirementIndex = 0; requirementIndex < requirements.size(); requirementIndex++) {
            MachineRequirement requirement = requirements.get(requirementIndex);
            if (!(requirement instanceof ItemRequirement item) || item.io() != RecipeModifier.IOType.OUTPUT) continue;
            ItemOutputRoute route = requirementIndex < itemOutputRoutes.size() ? itemOutputRoutes.get(requirementIndex) : null;
            if (route == null) return new RequirementFailure(requirementIndex, RequirementFailure.Kind.MISSING_OUTPUT, item.stack().getCount(), 0);
            int available = 0;
            for (ItemOutputTransfer transfer : route.transfers()) {
                ItemOutputState state = itemOutputState(states, transfer);
                ItemStack remaining = state.insert(transfer.stack(), new ArrayList<>());
                available += transfer.stack().getCount() - remaining.getCount();
                if (!remaining.isEmpty()) {
                    return new RequirementFailure(requirementIndex, RequirementFailure.Kind.MISSING_OUTPUT, item.stack().getCount(), available);
                }
            }
        }
        return null;
    }

    private @Nullable RequirementFailure firstFluidInputFailure(List<MachineRequirement> requirements) {
        List<FluidInputState> states = new ArrayList<>();
        for (int requirementIndex = 0; requirementIndex < requirements.size(); requirementIndex++) {
            MachineRequirement requirement = requirements.get(requirementIndex);
            if (!(requirement instanceof FluidRequirement fluid) || fluid.io() != RecipeModifier.IOType.INPUT) continue;
            FluidInputRoute route = requirementIndex < fluidInputRoutes.size() ? fluidInputRoutes.get(requirementIndex) : null;
            if (route == null) return new RequirementFailure(requirementIndex, RequirementFailure.Kind.MISSING_INPUT, fluid.amount(), 0);
            int available = 0;
            for (FluidInputTransfer transfer : route.transfers()) {
                FluidInputState state = fluidInputState(states, transfer);
                int remaining = state.drain(transfer.stack(), transfer.stack().getAmount(), new ArrayList<>());
                available += transfer.stack().getAmount() - remaining;
                if (remaining > 0) {
                    return new RequirementFailure(requirementIndex, RequirementFailure.Kind.MISSING_INPUT, fluid.amount(), available);
                }
            }
        }
        return null;
    }

    private @Nullable RequirementFailure firstFluidOutputFailure(List<MachineRequirement> requirements) {
        List<FluidOutputState> states = new ArrayList<>();
        for (int requirementIndex = 0; requirementIndex < requirements.size(); requirementIndex++) {
            MachineRequirement requirement = requirements.get(requirementIndex);
            if (!(requirement instanceof FluidRequirement fluid) || fluid.io() != RecipeModifier.IOType.OUTPUT) continue;
            FluidOutputRoute route = requirementIndex < fluidOutputRoutes.size() ? fluidOutputRoutes.get(requirementIndex) : null;
            if (route == null) return new RequirementFailure(requirementIndex, RequirementFailure.Kind.MISSING_OUTPUT, fluid.stack().getAmount(), 0);
            int available = 0;
            for (FluidOutputTransfer transfer : route.transfers()) {
                FluidOutputState state = fluidOutputState(states, transfer);
                int remaining = state.fill(transfer.stack(), transfer.stack().getAmount(), new ArrayList<>());
                available += transfer.stack().getAmount() - remaining;
                if (remaining > 0) {
                    return new RequirementFailure(requirementIndex, RequirementFailure.Kind.MISSING_OUTPUT, fluid.stack().getAmount(), available);
                }
            }
        }
        return null;
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
                states.add(new ItemInputState(bus, handler, slot, handler.getStackInSlot(slot).copy()));
            }
        }
        return states;
    }

    private List<ItemOutputState> itemOutputStates() {
        List<ItemOutputState> states = new ArrayList<>();
        for (ItemOutputBusBlockEntity bus : liveComponents(ItemOutputBusBlockEntity.class)) {
            IItemHandler handler = bus.getItemHandler(null);
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                states.add(new ItemOutputState(bus, handler, slot, handler.getStackInSlot(slot).copy(), handler.getSlotLimit(slot)));
            }
        }
        return states;
    }

    private List<FluidInputState> fluidInputStates() {
        List<FluidInputState> states = new ArrayList<>();
        for (FluidInputHatchBlockEntity hatch : liveComponents(FluidInputHatchBlockEntity.class)) {
            IFluidHandler handler = hatch.getFluidHandler(null);
            for (int tank = 0; tank < handler.getTanks(); tank++) {
                states.add(new FluidInputState(hatch, handler, tank, handler.getFluidInTank(tank).copy()));
            }
        }
        return states;
    }

    private List<FluidOutputState> fluidOutputStates() {
        List<FluidOutputState> states = new ArrayList<>();
        for (FluidOutputHatchBlockEntity hatch : liveComponents(FluidOutputHatchBlockEntity.class)) {
            IFluidHandler handler = hatch.getFluidHandler(null);
            for (int tank = 0; tank < handler.getTanks(); tank++) {
                states.add(new FluidOutputState(hatch, handler, tank, handler.getFluidInTank(tank).copy(), handler.getTankCapacity(tank)));
            }
        }
        return states;
    }

    private static void extract(List<ItemInputTransfer> transfers) {
        for (ItemInputTransfer transfer : transfers) {
            transfer.handler().extractItem(transfer.slot(), transfer.amount(), false);
        }
    }

    private static void insert(List<ItemOutputTransfer> transfers) {
        for (ItemOutputTransfer transfer : transfers) {
            ItemStack stack = normalizeRecipeOutput(transfer.stack());
            normalizeSlotBeforeInsert(transfer.handler(), transfer.slot(), stack);
            transfer.handler().insertItem(transfer.slot(), stack, false);
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
        ItemInputState state = new ItemInputState(null, transfer.handler(), transfer.slot(), transfer.handler().getStackInSlot(transfer.slot()).copy());
        states.add(state);
        return state;
    }

    private static ItemOutputState itemOutputState(List<ItemOutputState> states, ItemOutputTransfer transfer) {
        for (ItemOutputState state : states) {
            if (state.handler == transfer.handler() && state.slot == transfer.slot()) return state;
        }
        ItemOutputState state = new ItemOutputState(null, transfer.handler(), transfer.slot(),
                transfer.handler().getStackInSlot(transfer.slot()).copy(), transfer.handler().getSlotLimit(transfer.slot()));
        states.add(state);
        return state;
    }

    private static ItemStack normalizeRecipeOutput(ItemStack stack) {
        if (stack.isEmpty()) return stack;
        if (!stack.getComponents().isEmpty()) return stack.copy();
        ItemStack normalized = stack.getItem().getDefaultInstance();
        if (normalized.isEmpty() || stack.getMaxStackSize() >= normalized.getMaxStackSize()) return stack.copy();
        normalized.setCount(stack.getCount());
        return normalized;
    }

    private static void normalizeSlotBeforeInsert(IItemHandler handler, int slot, ItemStack input) {
        if (!(handler instanceof IItemHandlerModifiable modifiable) || input.isEmpty()) return;
        ItemStack current = handler.getStackInSlot(slot);
        if (current.isEmpty()) return;
        ItemStack normalized = normalizeRecipeOutput(current);
        if (normalized == current || normalized.getMaxStackSize() <= current.getMaxStackSize()) return;
        if (!ItemStack.isSameItemSameComponents(normalized, input)) return;
        modifiable.setStackInSlot(slot, normalized.copyWithCount(current.getCount()));
    }

    private static FluidInputState fluidInputState(List<FluidInputState> states, FluidInputTransfer transfer) {
        for (FluidInputState state : states) {
            if (state.handler == transfer.handler() && state.tank == transfer.tank()) return state;
        }
        FluidInputState state = new FluidInputState(null, transfer.handler(), transfer.tank(), transfer.handler().getFluidInTank(transfer.tank()).copy());
        states.add(state);
        return state;
    }

    private static FluidOutputState fluidOutputState(List<FluidOutputState> states, FluidOutputTransfer transfer) {
        for (FluidOutputState state : states) {
            if (state.handler == transfer.handler() && state.tank == transfer.tank()) return state;
        }
        FluidOutputState state = new FluidOutputState(null, transfer.handler(), transfer.tank(),
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
        private final ItemInputBusBlockEntity bus;
        private final IItemHandler handler;
        private final int slot;
        private final ItemStack stack;

        private ItemInputState(ItemInputBusBlockEntity bus, IItemHandler handler, int slot, ItemStack stack) {
            this.bus = bus;
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

        private ItemInputBusBlockEntity bus() {
            return bus;
        }
    }

    private static final class ItemOutputState {
        private final ItemOutputBusBlockEntity bus;
        private final IItemHandler handler;
        private final int slot;
        private ItemStack stack;
        private final int limit;

        private ItemOutputState(ItemOutputBusBlockEntity bus, IItemHandler handler, int slot, ItemStack stack, int limit) {
            this.bus = bus;
            this.handler = handler;
            this.slot = slot;
            this.stack = stack;
            this.limit = limit;
        }

        private ItemStack insert(ItemStack input, List<ItemOutputTransfer> transfers) {
            input = normalizeRecipeOutput(input);
            if (input.isEmpty()) return input;
            if (!stack.isEmpty()) {
                stack = normalizeRecipeOutput(stack);
                if (!ItemStack.isSameItemSameComponents(stack, input)) return input;
            }
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

        private ItemOutputBusBlockEntity bus() {
            return bus;
        }
    }

    private static final class FluidInputState {
        private final FluidInputHatchBlockEntity hatch;
        private final IFluidHandler handler;
        private final int tank;
        private final FluidStack stack;

        private FluidInputState(FluidInputHatchBlockEntity hatch, IFluidHandler handler, int tank, FluidStack stack) {
            this.hatch = hatch;
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

        private FluidInputHatchBlockEntity hatch() {
            return hatch;
        }
    }

    private static final class FluidOutputState {
        private final FluidOutputHatchBlockEntity hatch;
        private final IFluidHandler handler;
        private final int tank;
        private FluidStack stack;
        private final int capacity;

        private FluidOutputState(FluidOutputHatchBlockEntity hatch, IFluidHandler handler, int tank, FluidStack stack, int capacity) {
            this.hatch = hatch;
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

        private FluidOutputHatchBlockEntity hatch() {
            return hatch;
        }
    }
}
